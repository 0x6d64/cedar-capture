package org.martindemling.cedarcapture.core

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** STEP-05: the pipeline runs a generated WAV through fakes and produces the expected session (ADR-0003). */
class CapturePipelineTest {
    private val spans = listOf(
        TestWav.Silence(1.0), TestWav.Tone(1.0), // speech 1.0 to 2.0
        TestWav.Silence(1.0), TestWav.Tone(0.5), // speech 3.0 to 3.5
        TestWav.Silence(1.0), TestWav.Tone(0.8), // speech 4.5 to 5.3, the file ends while speaking
    )

    private val states = mutableListOf<PipelineState>()
    private val problems = mutableListOf<String>()
    private val store = InMemorySessionStore()
    private val raw = RecordingRawSink()

    private fun wav(bytes: ByteArray = TestWav.bytes(spans)) = WavAudioSource(ByteArrayInputStream(bytes))

    private fun pipeline(
        source: AudioSource = wav(),
        sink: RawAudioSink = raw,
        vad: VoiceActivityDetector = EnergyVad(),
        recognizer: SpeechRecognizer = FakeRecognizer(),
        executor: java.util.concurrent.Executor = DirectExecutor,
    ) = CapturePipeline(
        source = source,
        raw = sink,
        vad = vad,
        recognizer = recognizer,
        store = store,
        recognitionExecutor = executor,
        sessionId = "test",
        startedIso = "2026-11-21T10:00:00",
        onState = { states.add(it) },
        onProblem = { problems.add(it) },
    )

    @Test
    fun aGeneratedWavGivesTheExpectedSessionFile() {
        val pipeline = pipeline()
        assertEquals(PipelineState.Idle, pipeline.state)
        pipeline.run()

        val expected = "[SESSION id=test started=2026-11-21T10:00:00 format=1]\n\n" +
            "[SPEECH t=00:00:01]\nspeech at 1.0 s\n\n" +
            "[SPEECH t=00:00:03]\nspeech at 3.0 s\n\n" +
            "[SPEECH t=00:00:04]\nspeech at 4.5 s\n\n"
        assertEquals(expected, store.text())
        assertTrue(SessionFormat.parse(store.text()).ok)
        assertEquals(PipelineState.Stopped, pipeline.state)
        assertEquals(listOf(PipelineState.Recording, PipelineState.Stopped), states)
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun everySampleReachesTheRawSinkAndEverythingIsClosed() {
        val source = HookedSource(wav())
        pipeline(source = source).run()
        assertContentEquals(TestWav.samples(spans), raw.all())
        assertTrue(raw.closed)
        assertTrue(source.stopped)
        assertTrue(store.closed)
    }

    @Test
    fun rawAudioIsWrittenBeforeTheDetectorSeesTheFrame() {
        val log = mutableListOf<String>()
        pipeline(sink = RecordingRawSink(log), vad = LoggingVad(EnergyVad(), log)).run()
        assertTrue(log.size >= 2 * 100, "expected many frames, got ${log.size}")
        assertEquals(log.size % 2, 0)
        for (i in log.indices step 2) {
            assertEquals("write#${i / 2}", log[i])
            assertEquals("accept#${i / 2}", log[i + 1])
        }
    }

    @Test
    fun capturingContinuesWhileTheRecognizerIsSlow() {
        val release = CountDownLatch(1)
        val firstCall = CountDownLatch(1)
        val slow = object : SpeechRecognizer {
            private val fake = FakeRecognizer()

            override fun transcribe(segment: AudioSegment): TranscriptSegment {
                firstCall.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
                return fake.transcribe(segment)
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pipeline = pipeline(recognizer = slow, executor = executor)
            val runner = Thread { pipeline.run() }
            runner.start()
            assertTrue(firstCall.await(10, TimeUnit.SECONDS), "recognition never started")
            // The first segment is stuck in the recognizer, yet the capture loop reads the whole file.
            val total = TestWav.samples(spans).size
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (raw.sampleCount() < total && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(total, raw.sampleCount(), "capture must not wait for recognition")
            assertTrue(runner.isAlive, "run() is still waiting for the pending recognition")
            assertEquals(PipelineState.Recording, pipeline.state)

            release.countDown()
            runner.join(10_000)
            assertTrue(!runner.isAlive, "run() must finish once recognition is released")
            assertEquals(PipelineState.Stopped, pipeline.state)
            val speech = store.entries().filterIsInstance<SpeechEntry>()
            assertEquals(listOf(1L, 3L, 4L), speech.map { it.tSeconds }, "entries stay in order")
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun stopMidStreamStillTranscribesTheSegmentInProgress() {
        lateinit var pipeline: CapturePipeline
        // Frame 31 is where the first tone starts; stop while it is still playing (frame 50 is mid-tone).
        val source = HookedSource(wav()) { reads -> if (reads == 50) pipeline.stop() }
        pipeline = pipeline(source = source)
        pipeline.run()

        assertEquals(PipelineState.Stopped, pipeline.state)
        val speech = store.entries().filterIsInstance<SpeechEntry>()
        assertEquals(1, speech.size, "the flushed segment is transcribed")
        assertEquals(1L, speech[0].tSeconds)
        assertEquals(50 * 512, raw.sampleCount(), "the frame being processed is finished, then the loop ends")
        assertTrue(raw.closed && source.stopped && store.closed)
    }

    @Test
    fun aRecognizerFailureIsReportedAndCaptureContinues() {
        var calls = 0
        val flaky = object : SpeechRecognizer {
            private val fake = FakeRecognizer()

            override fun transcribe(segment: AudioSegment): TranscriptSegment {
                if (++calls == 2) throw IllegalStateException("model crashed")
                return fake.transcribe(segment)
            }
        }
        val pipeline = pipeline(recognizer = flaky)
        pipeline.run()

        assertEquals(PipelineState.Stopped, pipeline.state)
        assertEquals(listOf(1L, 4L), store.entries().filterIsInstance<SpeechEntry>().map { it.tSeconds })
        assertEquals(1, problems.size)
        assertTrue("model crashed" in problems[0], problems[0])
        assertEquals(TestWav.samples(spans).size, raw.sampleCount(), "the raw audio still has the lost segment")
    }

    @Test
    fun blankTranscriptsAreNotWritten() {
        val blank = object : SpeechRecognizer {
            override fun transcribe(segment: AudioSegment) = TranscriptSegment(segment.startSeconds, segment.startSeconds, "  ")
        }
        pipeline(recognizer = blank).run()
        assertEquals(1, store.entries().size, "only the SessionStart entry")
    }

    @Test
    fun aSourceFailureEndsInErrorAndStillClosesEverything() {
        val source = HookedSource(wav()) { reads -> if (reads == 3) throw IOException("microphone lost") }
        val pipeline = pipeline(source = source)
        pipeline.run() // run() reports the failure through the state, it does not throw

        val state = assertIs<PipelineState.Error>(pipeline.state)
        assertTrue("microphone lost" in state.message, state.message)
        assertEquals(listOf(PipelineState.Recording, state), states)
        assertTrue(raw.closed && source.stopped && store.closed)
        assertEquals(2 * 512, raw.sampleCount(), "the two frames read before the failure were written")
    }

    @Test
    fun aRawWriteFailureEndsInError() {
        val sink = RecordingRawSink(failOnWrite = 2)
        val pipeline = pipeline(sink = sink)
        pipeline.run()
        val state = assertIs<PipelineState.Error>(pipeline.state)
        assertTrue("disk full" in state.message, state.message)
        assertTrue(sink.closed && store.closed)
    }

    @Test
    fun aSourceThatFailsToStartEndsInErrorFromIdle() {
        val source = object : AudioSource {
            override val sampleRate = 16000

            override fun start() = throw IllegalStateException("no permission")

            override fun read(buffer: ShortArray) = -1

            override fun stop() = Unit
        }
        val pipeline = pipeline(source = source)
        pipeline.run()
        assertIs<PipelineState.Error>(pipeline.state)
        assertEquals(1, states.size)
    }

    @Test
    fun anErrorThatIsNotAnExceptionEndsInErrorAndPropagates() {
        val source = HookedSource(wav()) { reads -> if (reads == 3) throw AssertionError("native bridge broke") }
        val pipeline = pipeline(source = source)
        val thrown = assertFailsWith<AssertionError> { pipeline.run() }
        assertEquals("native bridge broke", thrown.message)
        assertIs<PipelineState.Error>(pipeline.state)
        assertTrue(raw.closed && source.stopped && store.closed, "cleanup still happens")
    }

    @Test
    fun anExecutorThatFailsEndsInErrorWithoutHangingTheRun() {
        val broken = java.util.concurrent.Executor { throw IllegalStateException("executor is broken") }
        val pipeline = pipeline(executor = broken)
        val runner = Thread { pipeline.run() }
        runner.start()
        runner.join(10_000)
        assertTrue(!runner.isAlive, "run() hung on a task that was never accepted")
        val state = assertIs<PipelineState.Error>(pipeline.state)
        assertTrue("executor is broken" in state.message, state.message)
        assertTrue(raw.closed && store.closed)
    }

    @Test
    fun aRejectedRecognitionIsReportedAndCaptureContinues() {
        val rejecting = java.util.concurrent.Executor { throw java.util.concurrent.RejectedExecutionException("shut down") }
        val pipeline = pipeline(executor = rejecting)
        pipeline.run()
        assertEquals(PipelineState.Stopped, pipeline.state)
        assertEquals(3, problems.size, problems.toString())
        assertTrue(problems.all { "rejected" in it && "shut down" in it }, problems.toString())
        assertEquals(TestWav.samples(spans).size, raw.sampleCount())
        assertEquals(1, store.entries().size, "only the SessionStart entry")
    }

    @Test
    fun anInterruptedDrainWaitsForTheRecognitionAndKeepsTheFlag() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val slow = object : SpeechRecognizer {
            private val fake = FakeRecognizer()

            override fun transcribe(segment: AudioSegment): TranscriptSegment {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
                return fake.transcribe(segment)
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        var interruptFlagAfterRun = false
        try {
            val pipeline = pipeline(recognizer = slow, executor = executor)
            val runner = Thread {
                pipeline.run()
                interruptFlagAfterRun = Thread.currentThread().isInterrupted
            }
            runner.start()
            assertTrue(started.await(10, TimeUnit.SECONDS))
            Thread.sleep(200) // the capture loop is done and run() is draining
            runner.interrupt()
            Thread.sleep(200)
            assertTrue(runner.isAlive, "an interrupt must not cut the drain short")
            release.countDown()
            runner.join(10_000)
            assertEquals(PipelineState.Stopped, pipeline.state)
            assertEquals(3, store.entries().filterIsInstance<SpeechEntry>().size, "no transcript lost")
            assertTrue(interruptFlagAfterRun, "the interrupt is restored for the caller")
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun stopBeforeRunOpensTheSourceWritesTheSessionStartAndStops() {
        val source = HookedSource(wav())
        val pipeline = pipeline(source = source)
        pipeline.stop()
        pipeline.run()
        assertEquals(PipelineState.Stopped, pipeline.state)
        assertEquals(listOf<SessionEntry>(SessionStart("test", "2026-11-21T10:00:00")), store.entries())
        assertEquals(0, raw.sampleCount())
        assertTrue(source.stopped && raw.closed && store.closed)
    }

    @Test
    fun aFailingObserverDoesNotBreakCapture() {
        val pipeline = CapturePipeline(
            source = wav(),
            raw = raw,
            vad = EnergyVad(),
            recognizer = FakeRecognizer(),
            store = store,
            recognitionExecutor = DirectExecutor,
            sessionId = "test",
            startedIso = "2026-11-21T10:00:00",
            onState = { throw IllegalStateException("observer bug") },
            onProblem = { throw IllegalStateException("observer bug") },
        )
        pipeline.run()
        assertEquals(PipelineState.Stopped, pipeline.state)
        assertEquals(3, store.entries().filterIsInstance<SpeechEntry>().size)
    }

    @Test
    fun aFailureWhileClosingAfterACleanRunEndsInError() {
        val sink = object : RawAudioSink {
            override fun write(samples: ShortArray, count: Int) = Unit

            override fun close() = throw IOException("flush failed")
        }
        val pipeline = pipeline(sink = sink)
        pipeline.run()
        val state = assertIs<PipelineState.Error>(pipeline.state)
        assertTrue("closing the raw audio failed" in state.message && "flush failed" in state.message, state.message)
        assertTrue(store.closed, "the other resources are still closed")
    }

    @Test
    fun aPipelineRunsOnlyOnce() {
        val pipeline = pipeline()
        pipeline.run()
        assertFailsWith<IllegalStateException> { pipeline.run() }
    }
}
