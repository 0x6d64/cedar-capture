package org.martindemling.cedarcapture.core

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** STEP-04: WAV replay AudioSource, plus the test fakes it needs (DEC-30). */
class WavAudioSourceTest {
    private class FakeClock : PacingClock {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        override fun nanoTime() = now
        override fun sleepNanos(nanos: Long) {
            sleeps.add(nanos)
            now += nanos
        }
    }

    private fun source(bytes: ByteArray, pacing: Pacing = Pacing.FAST, clock: PacingClock = FakeClock()) =
        WavAudioSource(ByteArrayInputStream(bytes), pacing, clock)

    private fun readAll(source: AudioSource, frame: Int = TestWav.FRAME): List<ShortArray> {
        val frames = mutableListOf<ShortArray>()
        val buffer = ShortArray(frame)
        while (true) {
            val n = source.read(buffer)
            if (n < 0) break
            frames.add(buffer.copyOf(n))
        }
        return frames
    }

    @Test
    fun readsTheExactSamplesInFramesOf512() {
        val samples = ShortArray(1200) { (it - 600).toShort() }
        val source = source(TestWav.bytes(samples))
        source.start()
        assertEquals(16000, source.sampleRate)
        val frames = readAll(source)
        assertEquals(listOf(512, 512, 176), frames.map { it.size })
        assertContentEquals(samples, frames.flatMap { it.toList() }.toShortArray())
        assertEquals(-1, source.read(ShortArray(512)))
    }

    @Test
    fun skipsUnknownChunksBeforeTheDataChunk() {
        val samples = ShortArray(10) { it.toShort() }
        for (extra in listOf(ByteArray(4), ByteArray(5))) { // an odd-sized chunk is followed by a pad byte
            val source = source(TestWav.bytes(samples, extraChunkBeforeData = extra))
            source.start()
            assertContentEquals(samples, readAll(source).flatMap { it.toList() }.toShortArray())
        }
    }

    @Test
    fun anOddTrailingByteIsIgnored() {
        val samples = ShortArray(5) { (it + 1).toShort() }
        val source = source(TestWav.bytes(samples, trailingByte = true))
        source.start()
        assertContentEquals(samples, readAll(source).flatMap { it.toList() }.toShortArray())
    }

    @Test
    fun anEmptyDataChunkEndsImmediately() {
        val source = source(TestWav.bytes(ShortArray(0)))
        source.start()
        assertEquals(-1, source.read(ShortArray(512)))
    }

    @Test
    fun rejectsFilesThatAreNot16kMonoPcm16() {
        val samples = ShortArray(10)
        val bad = listOf(
            Triple("8 kHz", TestWav.bytes(samples, sampleRate = 8000), "must be 16000 Hz, found 8000 Hz"),
            Triple("stereo", TestWav.bytes(samples, channels = 2), "must be mono, found 2 channels"),
            Triple("8-bit", TestWav.bytes(samples, bitsPerSample = 8), "must be 16-bit, found 8-bit"),
            Triple("float", TestWav.bytes(samples, format = 3), "is not PCM (format code 3)"),
            Triple("extensible", TestWav.bytes(samples, format = 0xFFFE), "WAVE_FORMAT_EXTENSIBLE is not supported"),
            Triple("no data chunk", TestWav.bytes(samples, includeData = false), "has no data chunk"),
            Triple("not a WAV", "hello world, this is not a wav file".toByteArray(), "no RIFF/WAVE header"),
            Triple("too short", ByteArray(5), "too short"),
        )
        for ((name, bytes, message) in bad) {
            val failure = assertFailsWith<IllegalArgumentException>(name) { source(bytes).start() }
            assertTrue(message in failure.message.orEmpty(), "$name: ${failure.message}")
        }
    }

    @Test
    fun readingBeforeStartFails() {
        assertFailsWith<IllegalStateException> { source(TestWav.bytes(ShortArray(10))).read(ShortArray(4)) }
    }

    @Test
    fun stopEndsTheStreamAndIsSafeToRepeatOrCallEarly() {
        val early = source(TestWav.bytes(ShortArray(10)))
        early.stop() // before start
        early.stop() // twice
        assertFailsWith<IllegalStateException> { early.start() }

        val running = source(TestWav.bytes(ShortArray(2000)))
        running.start()
        assertEquals(512, running.read(ShortArray(512)))
        running.stop()
        assertEquals(-1, running.read(ShortArray(512)), "after stop() the stream ends like a finished file")
        running.stop()
    }

    @Test
    fun readKeepsReturningMinusOneAtTheEnd() {
        val source = source(TestWav.bytes(ShortArray(3)))
        source.start()
        assertEquals(3, source.read(ShortArray(512)))
        repeat(3) { assertEquals(-1, source.read(ShortArray(512))) }
    }

    @Test
    fun aDataSizeLargerThanTheFileIsReadToTheEnd() {
        val samples = ShortArray(700) { it.toShort() }
        for (declared in listOf(-1, samples.size * 2 + 1000)) { // 0xFFFFFFFF as written by streaming recorders, and too big
            val source = source(TestWav.bytes(samples, declaredDataSize = declared))
            source.start()
            assertContentEquals(samples, readAll(source).flatMap { it.toList() }.toShortArray(), "declared $declared")
        }
    }

    @Test
    fun aStreamThatEndsInTheMiddleOfASampleDropsTheHalfSample() {
        val samples = ShortArray(5) { (it + 1).toShort() }
        val source = source(TestWav.bytes(samples, trailingByte = true, declaredDataSize = -1))
        source.start()
        assertContentEquals(samples, readAll(source).flatMap { it.toList() }.toShortArray())
    }

    @Test
    fun anUnknownChunkLargerThanTheFileIsATruncationError() {
        val failure = assertFailsWith<IllegalArgumentException> {
            // The LIST chunk claims 100 bytes, but the file ends 8 bytes into its body.
            source(TestWav.bytes(ShortArray(4), extraChunkBeforeData = ByteArray(100)).copyOf(60)).start()
        }
        assertTrue("truncated" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun fastPacingNeverSleeps() {
        val clock = FakeClock()
        val source = source(TestWav.bytes(ShortArray(16000)), Pacing.FAST, clock)
        source.start()
        readAll(source)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test
    fun realTimePacingDeliversEachFrameWhenItWouldHaveBeenRecordedWithoutDrift() {
        val clock = FakeClock()
        val samples = ShortArray(16000 + 100) // 1.00625 s
        val source = source(TestWav.bytes(samples), Pacing.REAL_TIME, clock)
        source.start()
        val startedAt = clock.now
        val buffer = ShortArray(512)
        var delivered = 0L
        while (true) {
            val n = source.read(buffer)
            if (n < 0) break
            delivered += n
            assertEquals(startedAt + delivered * 1_000_000_000L / 16000, clock.now, "after $delivered samples")
        }
        assertEquals(samples.size.toLong(), delivered)
    }

    @Test
    fun realTimePacingDoesNotSleepWhenTheConsumerIsLate() {
        val clock = FakeClock()
        val source = source(TestWav.bytes(ShortArray(1024)), Pacing.REAL_TIME, clock)
        source.start()
        clock.now += 10_000_000_000L // the consumer was busy for 10 s
        readAll(source)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test
    fun threeBurstsBecomeThreeSegmentsAndTheLastOneComesFromFlush() {
        val spans = listOf(
            TestWav.Silence(1.0), TestWav.Tone(1.0), // speech 1.0 to 2.0
            TestWav.Silence(1.0), TestWav.Tone(0.5), // speech 3.0 to 3.5
            TestWav.Silence(1.0), TestWav.Tone(0.8), // speech 4.5 to 5.3, the file ends while speaking
        )
        val source = source(TestWav.bytes(spans))
        val vad = EnergyVad()
        val ended = mutableListOf<AudioSegment>()
        var started = 0
        source.start()
        for (frame in readAll(source)) {
            when (val event = vad.accept(frame)) {
                VadEvent.SpeechStarted -> started++
                is VadEvent.SpeechEnded -> ended.add(event.segment)
                VadEvent.Silence -> Unit
            }
        }
        assertEquals(3, started)
        assertEquals(2, ended.size, "the last burst has no silence after it")
        val last = assertNotNull(vad.flush())
        assertNull(vad.flush(), "flush resets the detector")
        val segments = ended + last
        val frameSeconds = TestWav.FRAME.toDouble() / TestWav.RATE
        for ((segment, expectedStart) in segments.zip(listOf(1.0, 3.0, 4.5))) {
            assertTrue(kotlin.math.abs(segment.startSeconds - expectedStart) <= frameSeconds, "$expectedStart")
        }
        val lengths = segments.map { it.samples.size.toDouble() / TestWav.RATE }
        for ((length, expected) in lengths.zip(listOf(1.0, 0.5, 0.8))) {
            assertTrue(kotlin.math.abs(length - expected) <= 2 * frameSeconds, "$length vs $expected")
        }
        assertEquals("speech at 1.0 s", FakeRecognizer().transcribe(segments[0]).text)
    }

    @Test
    fun theFakeVadFlushesNothingWhenIdleAndDropsSpeechThatIsTooShort() {
        val vad = EnergyVad(minSpeechFrames = 3)
        assertNull(vad.flush())
        val loud = ShortArray(512) { 8000 }
        assertEquals(VadEvent.SpeechStarted, vad.accept(loud))
        assertNull(vad.flush(), "one loud frame is shorter than minSpeechFrames")
        assertNull(vad.flush())
    }
}
