package org.martindemling.cedarcapture.core

import kotlin.math.sqrt

/**
 * Fake voice activity detector for tests (STEP-04): a frame is speech when its RMS is at least [threshold]. Speech
 * ends after [endSilenceFrames] quiet frames; shorter speech than [minSpeechFrames] is dropped. The segment keeps the
 * samples up to the last speech frame. Frames are counted per call, so the start time is frame index * frame size / rate.
 */
class EnergyVad(
    private val threshold: Double = 1000.0,
    private val endSilenceFrames: Int = 10,
    private val minSpeechFrames: Int = 3,
    private val sampleRate: Int = TestWav.RATE,
) : VoiceActivityDetector {
    private var frameIndex = 0L
    private var frameSize = TestWav.FRAME // taken from the first frame
    private var startFrame = -1L
    private var speechFrames = 0
    private var quietFrames = 0
    private val samples = ArrayList<Short>()
    private var lastSpeechEnd = 0 // sample count up to and including the last speech frame

    override fun accept(frame: ShortArray): VadEvent {
        if (frameIndex == 0L) frameSize = frame.size
        val index = frameIndex++
        val loud = rms(frame) >= threshold
        if (startFrame < 0) {
            if (!loud) return VadEvent.Silence
            startFrame = index
            speechFrames = 0
            quietFrames = 0
            samples.clear()
            lastSpeechEnd = 0
            append(frame, loud = true)
            return VadEvent.SpeechStarted
        }
        append(frame, loud)
        if (quietFrames >= endSilenceFrames) return finish()?.let { VadEvent.SpeechEnded(it) } ?: VadEvent.Silence
        return VadEvent.Silence
    }

    override fun flush(): AudioSegment? = if (startFrame < 0) null else finish()

    private fun append(frame: ShortArray, loud: Boolean) {
        for (s in frame) samples.add(s)
        if (loud) {
            speechFrames++
            quietFrames = 0
            lastSpeechEnd = samples.size
        } else {
            quietFrames++
        }
    }

    /** Ends the segment in progress and resets. Returns null when it was too short. */
    private fun finish(): AudioSegment? {
        val segment = if (speechFrames >= minSpeechFrames) {
            AudioSegment(startFrame * frameSize.toDouble() / sampleRate, samples.take(lastSpeechEnd).toShortArray())
        } else {
            null
        }
        startFrame = -1
        samples.clear()
        return segment
    }

    private fun rms(frame: ShortArray): Double {
        if (frame.isEmpty()) return 0.0
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        return sqrt(sum / frame.size)
    }
}
