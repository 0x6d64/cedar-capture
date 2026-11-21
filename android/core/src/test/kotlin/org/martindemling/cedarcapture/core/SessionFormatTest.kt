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
}
