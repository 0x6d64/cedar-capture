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
        val bad = mapOf(
            "8 kHz" to TestWav.bytes(samples, sampleRate = 8000),
            "stereo" to TestWav.bytes(samples, channels = 2),
            "8-bit" to TestWav.bytes(samples, bitsPerSample = 8),
            "float" to TestWav.bytes(samples, format = 3),
            "no data chunk" to TestWav.bytes(samples, includeData = false),
            "not a WAV" to "hello world, this is not a wav file".toByteArray(),
            "too short" to ByteArray(5),
        )
        for ((name, bytes) in bad) {
            val failure = assertFailsWith<IllegalArgumentException>(name) { source(bytes).start() }
            assertTrue(failure.message.orEmpty().isNotBlank(), name)
        }
    }

    @Test
    fun readingBeforeStartOrAfterStopFails() {
        val source = source(TestWav.bytes(ShortArray(10)))
        assertFailsWith<IllegalStateException> { source.read(ShortArray(4)) }
        source.start()
        source.stop()
        assertFailsWith<IllegalStateException> { source.read(ShortArray(4)) }
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
        assertEquals(FakeRecognizer.text(segments[0].startSeconds), FakeRecognizer().transcribe(segments[0]).text)
    }
}
