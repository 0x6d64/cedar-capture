package org.martindemling.cedarcapture.core

import java.util.Locale

/** Fake recognizer for tests (STEP-04): the text depends only on the segment start, so results are predictable. */
class FakeRecognizer(private val sampleRate: Int = TestWav.RATE) : SpeechRecognizer {
    override fun transcribe(segment: AudioSegment): TranscriptSegment {
        val end = segment.startSeconds + segment.samples.size.toDouble() / sampleRate
        return TranscriptSegment(segment.startSeconds, end, text(segment.startSeconds))
    }

    companion object {
        fun text(startSeconds: Double): String = String.format(Locale.ROOT, "speech at %.1f s", startSeconds)
    }
}
