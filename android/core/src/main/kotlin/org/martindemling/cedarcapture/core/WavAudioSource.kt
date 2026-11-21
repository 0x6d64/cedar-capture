package org.martindemling.cedarcapture.core

import java.io.InputStream

/** How fast [WavAudioSource] hands out audio. */
enum class Pacing {
    /** As fast as the consumer reads. For tests and desktop-speed replay. */
    FAST,

    /** Each frame is handed out when it would have been recorded, like a microphone. */
    REAL_TIME,
}

/** Time source for [Pacing.REAL_TIME]; a fake makes the pacing testable without sleeping. */
interface PacingClock {
    fun nanoTime(): Long
    fun sleepNanos(nanos: Long)
}

object SystemPacingClock : PacingClock {
    override fun nanoTime(): Long = System.nanoTime()

    override fun sleepNanos(nanos: Long) {
        if (nanos > 0) Thread.sleep(nanos / NANOS_PER_MILLI, (nanos % NANOS_PER_MILLI).toInt())
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}

/**
 * Replays a WAV file as an [AudioSource] (DEC-30, STEP-04). Accepts only 16 kHz mono 16-bit PCM, the format the whole
 * pipeline uses; anything else is rejected by [start] with an [IllegalArgumentException] that says why. Unknown chunks
 * before `data` are skipped. A data chunk longer than the file is read until the end of the stream. WAVE_FORMAT_EXTENSIBLE
 * files are rejected even when they hold 16 kHz mono PCM16 (a proof-of-concept limit). Owns [input] and closes it in
 * [stop]; call [stop] even when [start] threw. [Pacing.REAL_TIME] sleeps inside [read], so an interrupt surfaces as
 * `InterruptedException`. The frame size is the size of the buffer passed to [read]. Pure JVM, no Android imports.
 */
class WavAudioSource(
    private val input: InputStream,
    private val pacing: Pacing = Pacing.FAST,
    private val clock: PacingClock = SystemPacingClock,
) : AudioSource {
    override val sampleRate: Int = SAMPLE_RATE

    @Volatile private var started = false

    @Volatile private var stopped = false
    private var dataBytesLeft = 0L
    private var startedAtNanos = 0L
    private var samplesDelivered = 0L

    override fun start() {
        check(!stopped) { "already stopped" }
        check(!started) { "already started" }
        parseHeader()
        started = true
        startedAtNanos = clock.nanoTime()
    }

    override fun read(buffer: ShortArray): Int {
        check(started) { "the source is not started" }
        if (stopped || dataBytesLeft <= 0L) return -1 // stop() ends the stream like the end of the file
        if (buffer.isEmpty()) return 0
        val wanted = minOf(buffer.size * 2L, dataBytesLeft).toInt()
        val bytes = ByteArray(wanted)
        val got = try {
            readUpTo(bytes)
        } catch (e: java.io.IOException) {
            if (stopped) return -1 // stop() closed the stream while this read was blocked
            throw e
        }
        if (got < 2) {
            dataBytesLeft = 0
            return -1
        }
        val count = got / 2
        for (i in 0 until count) {
            buffer[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
        }
        dataBytesLeft = if (got < wanted) 0L else dataBytesLeft - got // a short read means the stream ended
        samplesDelivered += count
        if (pacing == Pacing.REAL_TIME) {
            val due = startedAtNanos + samplesDelivered * NANOS_PER_SECOND / SAMPLE_RATE
            val wait = due - clock.nanoTime()
            if (wait > 0) clock.sleepNanos(wait)
        }
        return count
    }

    /** Safe to call from another thread, before [start], and more than once. A blocked or later [read] returns -1. */
    override fun stop() {
        stopped = true
        input.close()
    }

    private fun parseHeader() {
        val riff = ByteArray(RIFF_HEADER_BYTES)
        require(readUpTo(riff) == riff.size) { "not a WAV file: too short" }
        require(tag(riff, 0) == "RIFF" && tag(riff, 8) == "WAVE") { "not a WAV file: no RIFF/WAVE header" }
        var haveFormat = false
        while (true) {
            val head = ByteArray(CHUNK_HEADER_BYTES)
            require(readUpTo(head) == head.size) { "WAV file has no data chunk" }
            val size = u32(head, 4)
            when (tag(head, 0)) {
                "fmt " -> {
                    require(size >= FMT_BYTES) { "WAV fmt chunk is too small ($size bytes)" }
                    val fmt = ByteArray(FMT_BYTES)
                    require(readUpTo(fmt) == fmt.size) { "WAV file is truncated inside the fmt chunk" }
                    skip(size - FMT_BYTES + (size and 1L))
                    requireFormat(fmt)
                    haveFormat = true
                }
                "data" -> {
                    require(haveFormat) { "WAV data chunk comes before the fmt chunk" }
                    dataBytesLeft = size
                    return
                }
                else -> skip(size + (size and 1L)) // chunks are padded to an even size
            }
        }
    }

    private fun requireFormat(fmt: ByteArray) {
        val format = u16(fmt, 0)
        val channels = u16(fmt, 2)
        val rate = u32(fmt, 4)
        val bits = u16(fmt, 14)
        require(format != EXTENSIBLE) { "WAVE_FORMAT_EXTENSIBLE is not supported; re-export the file as plain PCM" }
        require(format == PCM) { "WAV is not PCM (format code $format)" }
        require(channels == 1) { "WAV must be mono, found $channels channels" }
        require(rate == SAMPLE_RATE.toLong()) { "WAV must be $SAMPLE_RATE Hz, found $rate Hz" }
        require(bits == BITS) { "WAV must be $BITS-bit, found $bits-bit" }
    }

    /** Reads until [bytes] is full or the stream ends; returns how many bytes were read. */
    private fun readUpTo(bytes: ByteArray): Int {
        var total = 0
        while (total < bytes.size) {
            val n = input.read(bytes, total, bytes.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun skip(count: Long) {
        var left = count
        val scratch = ByteArray(SKIP_BUFFER_BYTES)
        while (left > 0) {
            val n = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            require(n >= 0) { "WAV file is truncated inside a chunk" }
            left -= n
        }
    }

    private fun tag(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)

    private companion object {
        const val SAMPLE_RATE = 16000
        const val BITS = 16
        const val PCM = 1
        const val EXTENSIBLE = 0xFFFE
        const val FMT_BYTES = 16
        const val RIFF_HEADER_BYTES = 12
        const val CHUNK_HEADER_BYTES = 8
        const val SKIP_BUFFER_BYTES = 4096
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
