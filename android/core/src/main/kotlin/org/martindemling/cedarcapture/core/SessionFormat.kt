package org.martindemling.cedarcapture.core

import java.util.Locale

/**
 * Reads and writes session files. Mirrors tools/src/voicetools/session.py; both must agree on fixtures/.
 * Specification: docs/session-format.md (format version 1). Pure JVM: no Android imports.
 */
object SessionFormat {
    const val VERSION = 1

    private val HEADER = Regex("""^\[([A-Z][A-Z-]*)((?: [a-z_]+=[^\s\]]+)*)\]$""")
    private val ATTR = Regex(""" ([a-z_]+)=([^\s\]]+)""")
    private val TIME = Regex("""^(\d{2,}):([0-5]\d):([0-5]\d)$""")
    private val COVERS = Regex("""^(\d{2,}:[0-5]\d:[0-5]\d)-(\d{2,}:[0-5]\d:[0-5]\d)$""")
    private val TOKEN_UNSAFE = Regex("""[\s\]]+""")

    fun formatTime(seconds: Long): String {
        require(seconds >= 0) { "seconds must be non-negative" }
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }

    fun parseTime(text: String): Long? {
        val match = TIME.matchEntire(text) ?: return null
        val (hours, minutes, secs) = match.destructured
        return hours.toLong() * 3600 + minutes.toLong() * 60 + secs.toLong()
    }

    /** Makes a value safe for a header attribute: no whitespace, no closing bracket. */
    fun token(value: String): String = TOKEN_UNSAFE.replace(value.trim(), "_").ifEmpty { "unknown" }

    fun render(entries: List<SessionEntry>): String = entries.joinToString("") { render(it) }

    fun render(entry: SessionEntry): String = when (entry) {
        is SessionStart -> {
            val attrs = listOf(
                "id" to token(entry.sessionId),
                "started" to token(entry.started),
                "format" to entry.formatVersion.toString(),
            )
            "${header("SESSION", attrs)}\n\n"
        }
        is SpeechEntry -> simple("SPEECH", entry.tSeconds, emptyList(), entry.text)
        is QuestionEntry -> simple("Q", entry.tSeconds, emptyList(), entry.text)
        is AnswerEntry -> simple("A", entry.tSeconds, emptyList(), entry.text)
        is GapEntry -> simple("GAP", entry.tSeconds, listOf("reason" to token(entry.reason)), "")
        is InterruptedEntry -> simple("INTERRUPTED", entry.tSeconds, emptyList(), "")
        is CompressionEntry -> {
            val time = "t" to formatTime(entry.tSeconds)
            val covers = "${formatTime(entry.coversFromSeconds)}-${formatTime(entry.coversToSeconds)}"
            val start = header("COMPRESSION-START", listOf(time, "model" to token(entry.model), "covers" to covers))
            val end = header("COMPRESSION-END", listOf(time))
            val body = if (entry.summary.isEmpty()) "" else escape(entry.summary) + "\n"
            "$start\n$body$end\n\n"
        }
    }

    fun parse(text: String): ParseResult {
        val lines = text.split("\n")
        val truncated = lines.last() != "" // no trailing newline: the last line is incomplete
        val problems = mutableListOf<String>()
        val assembler = Assembler()
        for (block in splitBlocks(lines.dropLast(1), problems)) assembler.feed(block)
        // A compression block still open at end of file is dropped and counts as truncation.
        return ParseResult(
            entries = assembler.entries.toList(),
            truncated = truncated || assembler.unfinished,
            problems = problems + assembler.problems,
        )
    }

    /**
     * Repairs an unfinished session file (writing rule 2 in the spec): keeps only whole entries, returns the cut-off
     * tail so the caller can save it, and builds the `[INTERRUPTED ...]` entry to append (nothing is appended when the
     * kept text already ends with one). [tSeconds] defaults to the latest time among the kept entries. Mirrors tools/src/voicetools/session.py (`Recovery.from_text`).
     */
    fun recover(text: String, tSeconds: Long? = null): Recovery {
        val cut = recoveryCut(text)
        val kept = text.substring(0, cut)
        val entries = parse(kept).entries
        if (entries.lastOrNull() is InterruptedEntry) {
            return Recovery(kept, text.substring(cut), "") // already marked: recovering twice adds nothing
        }
        val time = tSeconds ?: entries.maxOfOrNull { it.tSeconds } ?: 0L
        return Recovery(kept, text.substring(cut), render(InterruptedEntry(time)))
    }

    /** Offset where the file must be cut so that only whole entries remain. */
    private fun recoveryCut(text: String): Int {
        val base = text.substring(0, text.lastIndexOf('\n') + 1) // drop a last line that has no newline
        if (base.isEmpty()) return 0
        val lines = base.split("\n").dropLast(1)
        val blocks = splitBlocks(lines, mutableListOf())
        val assembler = Assembler()
        blocks.forEach { assembler.feed(it) }
        assembler.openLineNo?.let { return lineOffset(lines, it) }
        if (!base.endsWith("\n\n")) return if (blocks.isEmpty()) 0 else lineOffset(lines, lastEntryLine(blocks))
        return base.length
    }

    private fun lineOffset(lines: List<String>, lineNo: Int): Int = lines.take(lineNo - 1).sumOf { it.length + 1 }

    /** Line where the last entry starts; a closed compression block starts at its START line. */
    private fun lastEntryLine(blocks: List<Block>): Int {
        val last = blocks.last()
        if (last.tag == "COMPRESSION-END") {
            blocks.dropLast(1).lastOrNull { it.tag == "COMPRESSION-START" }?.let { return it.lineNo }
        }
        return last.lineNo
    }

    private fun header(tag: String, attrs: List<Pair<String, String>>): String =
        "[$tag${attrs.joinToString("") { " ${it.first}=${it.second}" }}]"

    private fun simple(tag: String, t: Long, extra: List<Pair<String, String>>, body: String): String {
        val head = header(tag, listOf("t" to formatTime(t)) + extra)
        val text = if (body.isEmpty()) "" else escape(body) + "\n"
        return "$head\n$text\n"
    }

    private fun escape(body: String): String =
        body.split("\n").joinToString("\n") { if (it.startsWith("[") || it.startsWith("\\")) "\\" + it else it }

    private fun unescape(lines: List<String>): String =
        lines.joinToString("\n") { if (it.startsWith("\\")) it.substring(1) else it }

    private class Block(
        val tag: String,
        val attrs: Map<String, String>,
        val lineNo: Int,
        val bodyLines: MutableList<String> = mutableListOf(),
    )

    private fun splitBlocks(lines: List<String>, problems: MutableList<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        lines.forEachIndexed { index, line ->
            val number = index + 1
            val match = HEADER.matchEntire(line)
            when {
                match != null -> blocks.add(Block(match.groupValues[1], parseAttrs(match.groupValues[2]), number))
                blocks.isNotEmpty() -> blocks.last().bodyLines.add(line)
                line.isNotBlank() -> problems.add("line $number: text before the first header")
            }
        }
        return blocks
    }

    private fun parseAttrs(text: String): Map<String, String> =
        ATTR.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

    private fun body(lines: List<String>): String {
        var end = lines.size
        while (end > 0 && lines[end - 1].isEmpty()) end--
        return unescape(lines.subList(0, end))
    }

    private fun entryFrom(block: Block, problems: MutableList<String>): SessionEntry? {
        if (block.tag == "SESSION") {
            return SessionStart(
                sessionId = block.attrs["id"] ?: "unknown",
                started = block.attrs["started"] ?: "unknown",
                formatVersion = block.attrs["format"]?.toIntOrNull() ?: VERSION,
            )
        }
        val seconds = block.attrs["t"]?.let { parseTime(it) }
        if (seconds == null) {
            problems.add("line ${block.lineNo}: ${block.tag} without a valid t=HH:MM:SS")
            return null
        }
        return when (block.tag) {
            "SPEECH" -> SpeechEntry(seconds, body(block.bodyLines))
            "Q" -> QuestionEntry(seconds, body(block.bodyLines))
            "A" -> AnswerEntry(seconds, body(block.bodyLines))
            "GAP" -> GapEntry(seconds, block.attrs["reason"] ?: "unknown")
            "INTERRUPTED" -> InterruptedEntry(seconds)
            else -> {
                problems.add("line ${block.lineNo}: unknown tag ${block.tag}")
                null
            }
        }
    }

    private fun compressionEntry(block: Block, problems: MutableList<String>): SessionEntry? {
        val seconds = block.attrs["t"]?.let { parseTime(it) }
        val model = block.attrs["model"]
        val covers = COVERS.matchEntire(block.attrs["covers"] ?: "")
        val from = covers?.groupValues?.get(1)?.let { parseTime(it) }
        val to = covers?.groupValues?.get(2)?.let { parseTime(it) }
        if (seconds == null || model == null || from == null || to == null) {
            problems.add("line ${block.lineNo}: COMPRESSION-START needs t, model and covers=HH:MM:SS-HH:MM:SS")
            return null
        }
        return CompressionEntry(seconds, model, from, to, body(block.bodyLines))
    }

    /** Turns header blocks into entries, pairing COMPRESSION-START with COMPRESSION-END. */
    private class Assembler {
        val entries = mutableListOf<SessionEntry>()
        val problems = mutableListOf<String>()
        private var open: Block? = null

        val unfinished: Boolean get() = open != null

        /** Line of a COMPRESSION-START that has no END yet, or null. */
        val openLineNo: Int? get() = open?.lineNo

        fun feed(block: Block) {
            when (block.tag) {
                "COMPRESSION-START" -> {
                    closeUnfinished()
                    open = block
                }
                "COMPRESSION-END" -> finishCompression(block)
                else -> {
                    closeUnfinished()
                    entryFrom(block, problems)?.let { entries.add(it) }
                }
            }
        }

        private fun closeUnfinished() {
            open?.let { problems.add("line ${it.lineNo}: COMPRESSION-START not closed") }
            open = null
        }

        private fun finishCompression(end: Block) {
            val start = open
            if (start == null) {
                problems.add("line ${end.lineNo}: COMPRESSION-END without START")
                return
            }
            open = null
            compressionEntry(start, problems)?.let { entries.add(it) }
        }
    }
}
