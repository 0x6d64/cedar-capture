package org.martindemling.cedarcapture.core

import java.io.IOException
import java.util.concurrent.Executor

/** Test doubles for the pipeline (STEP-05). All of them are plain JVM objects. */

class InMemorySessionStore : SessionStore {
    private val list = mutableListOf<SessionEntry>()
    var closed = false
        private set

    @Synchronized
    override fun append(entry: SessionEntry) {
        check(!closed) { "store is closed" }
        list.add(entry)
    }

    @Synchronized
    override fun entries(): List<SessionEntry> = list.toList()

    override fun close() {
        closed = true
    }

    fun text(): String = SessionFormat.render(entries())
}

/** Collects every sample it is given, and logs `write#<n>` into [log] when one is passed. */
class RecordingRawSink(private val log: MutableList<String>? = null, private val failOnWrite: Int = -1) : RawAudioSink {
    private val samples = ArrayList<Short>()
    private var writes = 0
    var closed = false
        private set

    @Synchronized
    override fun write(samples: ShortArray, count: Int) {
        if (writes == failOnWrite) throw IOException("disk full")
        log?.add("write#$writes")
        writes++
        for (i in 0 until count) this.samples.add(samples[i])
    }

    override fun close() {
        closed = true
    }

    @Synchronized
    fun sampleCount(): Int = samples.size

    @Synchronized
    fun all(): ShortArray = samples.toShortArray()
}

/** Wraps a detector and logs `accept#<n>` so that tests can check the order against the raw writes. */
class LoggingVad(private val inner: VoiceActivityDetector, private val log: MutableList<String>) : VoiceActivityDetector {
    private var accepted = 0

    override fun accept(frame: ShortArray): VadEvent {
        log.add("accept#$accepted")
        accepted++
        return inner.accept(frame)
    }

    override fun flush(): AudioSegment? = inner.flush()
}

/** Wraps a source: counts reads, tracks stop(), and runs [onRead] after each read (to stop mid-stream or to fail). */
class HookedSource(private val inner: AudioSource, private val onRead: (readCount: Int) -> Unit = {}) : AudioSource {
    override val sampleRate: Int get() = inner.sampleRate
    var stopped = false
        private set
    private var reads = 0

    override fun start() = inner.start()

    override fun read(buffer: ShortArray): Int {
        val n = inner.read(buffer)
        reads++
        onRead(reads)
        return n
    }

    override fun stop() {
        stopped = true
        inner.stop()
    }
}

/** Runs tasks on the calling thread. */
object DirectExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}
