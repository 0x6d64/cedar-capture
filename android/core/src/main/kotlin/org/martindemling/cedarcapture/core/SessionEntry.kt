package org.martindemling.cedarcapture.core

/** One entry of a session file. Specification: docs/session-format.md (REQ-SES-01, REQ-SES-03). */
sealed interface SessionEntry {
    /** Seconds since the session started. */
    val tSeconds: Long
}

data class SessionStart(
    val sessionId: String,
    val started: String,
    val formatVersion: Int = SessionFormat.VERSION,
) : SessionEntry {
    override val tSeconds: Long get() = 0L
}

data class SpeechEntry(override val tSeconds: Long, val text: String) : SessionEntry

data class QuestionEntry(override val tSeconds: Long, val text: String) : SessionEntry

data class AnswerEntry(override val tSeconds: Long, val text: String) : SessionEntry

/** Capture was paused, for example because audio focus was lost. */
data class GapEntry(override val tSeconds: Long, val reason: String) : SessionEntry

/** Written when an unfinished session is recovered. */
data class InterruptedEntry(override val tSeconds: Long) : SessionEntry

/** A user-triggered compression block (REQ-CTX-03, REQ-CTX-05). The raw transcript is never changed. */
data class CompressionEntry(
    override val tSeconds: Long,
    val model: String,
    val coversFromSeconds: Long,
    val coversToSeconds: Long,
    val summary: String,
) : SessionEntry

/** Entries read from a session file, plus truncation and problem reports. */
data class ParseResult(
    val entries: List<SessionEntry>,
    val truncated: Boolean,
    val problems: List<String>,
) {
    val ok: Boolean get() = !truncated && problems.isEmpty()
}
