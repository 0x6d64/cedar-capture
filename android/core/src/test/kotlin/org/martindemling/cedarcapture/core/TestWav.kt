package org.martindemling.cedarcapture.core

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Builds WAV files in memory so that tests need no binary fixtures (STEP-04). */
object TestWav {
    const val RATE = 16000
    const val FRAME = 512

    sealed interface Span {
        val seconds: Double
    }

    /** A 440 Hz tone: stands in for speech. */
    data class Tone(override val seconds: Double, val amplitude: Int = 8000) : Span

    data class Silence(override val seconds: Double) : Span

    fun samples(spans: List<Span>): ShortArray {
        val out = ArrayList<Short>()
        for (span in spans) {
            val count = (span.seconds * RATE).roundToInt()
            for (i in 0 until count) {
                val value = when (span) {
                    is Tone -> (span.amplitude * sin(2 * PI * 440.0 * i / RATE)).roundToInt()
                    is Silence -> 0
                }
                out.add(value.toShort())
            }
        }
        return out.toShortArray()
    }

    /** A canonical 44-byte-header WAV file. Parameters can be changed to build invalid files. */
    fun bytes(
        samples: ShortArray,
        sampleRate: Int = RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        format: Int = 1,
        extraChunkBeforeData: ByteArray? = null,
        includeData: Boolean = true,
        trailingByte: Boolean = false,
    ): ByteArray {
        val data = ByteArrayOutputStream()
        for (s in samples) {
            data.write(s.toInt() and 0xFF)
            data.write((s.toInt() shr 8) and 0xFF)
        }
        if (trailingByte) data.write(0x7F)
        val body = ByteArrayOutputStream()
        body.write("WAVE".toByteArray(Charsets.US_ASCII))
        body.write("fmt ".toByteArray(Charsets.US_ASCII))
        body.write(le32(16))
        body.write(le16(format))
        body.write(le16(channels))
        body.write(le32(sampleRate))
        body.write(le32(sampleRate * channels * bitsPerSample / 8))
        body.write(le16(channels * bitsPerSample / 8))
        body.write(le16(bitsPerSample))
        if (extraChunkBeforeData != null) {
            body.write("LIST".toByteArray(Charsets.US_ASCII))
            body.write(le32(extraChunkBeforeData.size))
            body.write(extraChunkBeforeData)
            if (extraChunkBeforeData.size % 2 == 1) body.write(0) // chunks are padded to an even size
        }
        if (includeData) {
            body.write("data".toByteArray(Charsets.US_ASCII))
            body.write(le32(data.size()))
            body.write(data.toByteArray())
        }
        val file = ByteArrayOutputStream()
        file.write("RIFF".toByteArray(Charsets.US_ASCII))
        file.write(le32(body.size()))
        file.write(body.toByteArray())
        return file.toByteArray()
    }

    fun bytes(spans: List<Span>): ByteArray = bytes(samples(spans))

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )
}
