package org.martindemling.cedarcapture.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses the shared fixtures in the repository root (REQ-SES-01..04, STEP-03). Gradle runs tests in android/core. */
class SessionFormatTest {
    private fun fixture(name: String): String = File("../../fixtures/sessions/$name").readText()

    @Test
    fun timeRoundTrip() {
        assertEquals("01:02:05", SessionFormat.formatTime(3725))
        assertEquals(3725L, SessionFormat.parseTime("01:02:05"))
        assertEquals(360000L, SessionFormat.parseTime("100:00:00"))
        assertNull(SessionFormat.parseTime("00:61:00"))
        assertNull(SessionFormat.parseTime("1:02:05"))
    }

    @Test
    fun negativeTimeIsRejected() {
        assertFailsWith<IllegalArgumentException> { SessionFormat.formatTime(-1) }
    }

    @Test
    fun renderThenParseGivesIdenticalEntries() {
        val entries = listOf(
            SessionStart("s1", "2026-11-20T08:15:00"),
            SpeechEntry(1, "hello"),
            QuestionEntry(2, "what?"),
            AnswerEntry(3, "line one\n\nline three"),
            GapEntry(4, "route_conflict"),
            CompressionEntry(5, "m1", 0, 4, "s"),
            InterruptedEntry(6),
        )
        val result = SessionFormat.parse(SessionFormat.render(entries))
        assertTrue(result.ok)
        assertEquals(entries, result.entries)
    }

    @Test
    fun bodyLinesThatLookLikeHeadersSurviveARoundTrip() {
        for (line in listOf("[SPEECH t=00:00:01]", "\\[x]", "[plain bracket", "\\\\two")) {
            val entry = SpeechEntry(1, "before\n$line\nafter")
            val result = SessionFormat.parse(SessionFormat.render(listOf(entry)))
            assertTrue(result.ok, line)
            assertEquals(listOf<SessionEntry>(entry), result.entries, line)
        }
    }

    @Test
    fun validFixture() {
        val result = SessionFormat.parse(fixture("valid-basic.session.md"))
        assertTrue(result.ok)
        assertEquals(9, result.entries.size)
        val answer = result.entries[4] as AnswerEntry
        assertEquals(listOf("[not a header]", "\\backslash line"), answer.text.split("\n").drop(1))
        val compression = result.entries[6] as CompressionEntry
        assertEquals("gemma-2-2b-q4", compression.model)
        assertEquals(0L, compression.coversFromSeconds)
        assertEquals(2470L, compression.coversToSeconds)
        assertEquals("audio_focus_lost", (result.entries[5] as GapEntry).reason)
    }

    @Test
    fun truncatedFixture() {
        val result = SessionFormat.parse(fixture("truncated.session.md"))
        assertTrue(result.truncated)
        assertEquals(3, result.entries.size)
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun unclosedCompressionIsDroppedAndCountsAsTruncated() {
        val result = SessionFormat.parse(fixture("unclosed-compression.session.md"))
        assertTrue(result.truncated)
        assertEquals(2, result.entries.size)
        assertTrue(result.entries.none { it is CompressionEntry })
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun invalidEntriesFixtureReportsThreeProblems() {
        val result = SessionFormat.parse(fixture("invalid-entries.session.md"))
        assertFalse(result.truncated)
        assertEquals(2, result.entries.size)
        assertEquals(3, result.problems.size)
        assertFalse(result.ok)
    }

    @Test
    fun headerInsideOpenCompressionBlockIsAProblem() {
        val text = "[COMPRESSION-START t=00:00:10 model=m covers=00:00:00-00:00:09]\nsummary\n" +
            "[SPEECH t=00:00:11]\nhi\n\n"
        val result = SessionFormat.parse(text)
        assertEquals(listOf<SessionEntry>(SpeechEntry(11, "hi")), result.entries)
        assertTrue(result.problems.any { "not closed" in it })
    }

    @Test
    fun compressionEndWithoutStartIsAProblem() {
        val result = SessionFormat.parse("[COMPRESSION-END t=00:00:10]\n\n")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.problems.any { "without START" in it })
    }

    @Test
    fun emptyTextIsOkAndEmpty() {
        val result = SessionFormat.parse("")
        assertTrue(result.ok)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun tokenMakesValuesSafeForHeaders() {
        assertEquals("a_b_c", SessionFormat.token(" a b]c "))
        assertEquals("unknown", SessionFormat.token("   "))
    }

    @Test
    fun invalidEntriesFixtureProblemTexts() {
        val result = SessionFormat.parse(fixture("invalid-entries.session.md"))
        assertEquals(
            listOf(
                "line 1: text before the first header",
                "line 4: SPEECH without a valid t=HH:MM:SS",
                "line 7: unknown tag FOO",
            ),
            result.problems,
        )
    }

    private val good = "[SESSION id=s started=t format=1]\n\n[SPEECH t=00:00:05]\nhello\n\n"
    private val openCompression = "[COMPRESSION-START t=00:01:00 model=m covers=00:00:00-00:00:59]\n"
    private val interrupted5 = "[INTERRUPTED t=00:00:05]\n\n"

    @Test
    fun recoveryOfACleanFileOnlyAppendsTheMarker() {
        val recovery = SessionFormat.recover(good)
        assertEquals(good, recovery.kept)
        assertEquals("", recovery.droppedTail)
        assertEquals(interrupted5, recovery.appended)
    }

    @Test
    fun recoveryOfAnEmptyFile() {
        val recovery = SessionFormat.recover("")
        assertEquals("", recovery.kept)
        assertEquals("", recovery.droppedTail)
        assertEquals("[INTERRUPTED t=00:00:00]\n\n", recovery.appended)
    }

    @Test
    fun recoveryUsesTheGivenTime() {
        assertEquals("[INTERRUPTED t=00:01:30]\n\n", SessionFormat.recover(good, tSeconds = 90).appended)
    }

    @Test
    fun recoveryCutsAHalfWrittenEntryAndKeepsTheTail() {
        val tails = listOf(
            "[SPEECH t=00:00:09]\nhal", // body cut mid-line
            "[SPEECH t=00:00:09]\nhalf\n", // body line complete, entry not terminated
            "[SPEECH t=00:00:09]\n", // header only
            "[SPEECH t=00:0", // header cut mid-line
            "[SPEECH t=00:00:09]", // header complete but no newline
        )
        for (tail in tails) {
            val recovery = SessionFormat.recover(good + tail)
            assertEquals(good, recovery.kept, tail)
            assertEquals(tail, recovery.droppedTail, tail)
            assertEquals(interrupted5, recovery.appended, tail)
        }
    }

    @Test
    fun recoveryCutsAnOpenCompressionBlock() {
        for (summary in listOf("summary\n", "summary\n\nmore\n", "summary\n\n")) {
            val recovery = SessionFormat.recover(good + openCompression + summary)
            assertEquals(good, recovery.kept, summary)
            assertEquals(openCompression + summary, recovery.droppedTail, summary)
        }
    }

    @Test
    fun recoveryKeepsAClosedCompressionBlock() {
        val closed = openCompression + "summary\n[COMPRESSION-END t=00:01:00]\n\n"
        val recovery = SessionFormat.recover(good + closed)
        assertEquals(good + closed, recovery.kept)
        assertEquals("", recovery.droppedTail)
        assertEquals("[INTERRUPTED t=00:01:00]\n\n", recovery.appended)
    }

    @Test
    fun recoveryCountsUtf8BytesNotCharacters() {
        val text = good + "[SPEECH t=00:00:07]\nGrüße 😀\n\n"
        val recovery = SessionFormat.recover(text)
        assertEquals(text, recovery.kept)
        assertEquals(text.toByteArray(Charsets.UTF_8).size, recovery.keptUtf8Bytes)
        assertTrue(recovery.keptUtf8Bytes > text.length)
    }

    @Test
    fun recoveryFromEveryPrefixOfAValidFileIsValid() {
        val text = fixture("valid-basic.session.md")
        var previousCount = 0
        for (cut in 0..text.length) {
            val prefix = text.substring(0, cut)
            val recovery = SessionFormat.recover(prefix)
            assertEquals(prefix, recovery.kept + recovery.droppedTail, "cut $cut")
            val result = SessionFormat.parse(recovery.kept + recovery.appended)
            assertTrue(result.ok, "cut $cut: ${result.problems}")
            assertTrue(result.entries.last() is InterruptedEntry, "cut $cut")
            // Cutting must never be more aggressive than needed: whole entries are never lost.
            assertTrue(result.entries.size >= previousCount, "cut $cut")
            previousCount = result.entries.size
        }
        assertEquals(text, SessionFormat.recover(text).kept)
    }

    @Test
    fun recoveryDoesNotStackInterruptedMarkers() {
        val once = SessionFormat.recover(good)
        val twice = SessionFormat.recover(once.kept + once.appended)
        assertEquals(once.kept + once.appended, twice.kept)
        assertEquals("", twice.droppedTail)
        assertEquals("", twice.appended)
    }

    @Test
    fun recoveryDropsAClosedCompressionBlockWithoutItsBlankLine() {
        val block = openCompression + "summary\n[COMPRESSION-END t=00:01:00]\n"
        val recovery = SessionFormat.recover(good + block)
        assertEquals(good, recovery.kept)
        assertEquals(block, recovery.droppedTail)
    }

    @Test
    fun recoveryOfTextWithoutAnyHeaderCutsEverything() {
        val recovery = SessionFormat.recover("garbage\n")
        assertEquals("", recovery.kept)
        assertEquals("garbage\n", recovery.droppedTail)
    }

    @Test
    fun recoveryWithATornCharacterInTheTailKeepsTheByteCount() {
        // A torn UTF-8 write decodes (leniently) to U+FFFD, and only inside the dropped tail.
        val replacementChar = 0xFFFD.toChar()
        val recovery = SessionFormat.recover(good + "[SPEECH t=00:00:09]\nGr$replacementChar")
        assertEquals(good, recovery.kept)
        assertEquals(good.toByteArray(Charsets.UTF_8).size, recovery.keptUtf8Bytes)
    }
}
