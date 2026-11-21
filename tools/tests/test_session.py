from pathlib import Path

import pytest

from voicetools.session import (
    Entry,
    ParseResult,
    Recovery,
    format_time,
    parse_time,
    render_session,
)

FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "sessions"


def test_time_round_trip() -> None:
    assert format_time(3725) == "01:02:05"
    assert parse_time("01:02:05") == 3725
    assert parse_time("100:00:00") == 360000
    assert parse_time("00:61:00") is None
    assert parse_time("1:02:05") is None


def test_negative_time_is_rejected() -> None:
    with pytest.raises(ValueError):
        format_time(-1)


def test_render_then_parse_gives_identical_entries() -> None:
    entries = [
        Entry.session_start(session_id="s1", started="2026-11-20T08:15:00"),
        Entry.speech(t_seconds=1, text="hello"),
        Entry.question(t_seconds=2, text="what?"),
        Entry.answer(t_seconds=3, text="line one\n\nline three"),
        Entry.gap(t_seconds=4, reason="route conflict"),
        Entry.compression(t_seconds=5, model="m 1", covers_from=0, covers_to=4, summary="s"),
        Entry.interrupted(t_seconds=6),
    ]
    result = ParseResult.from_text(render_session(entries))
    assert result.ok
    assert list(result.entries) == [
        *entries[:4],
        Entry.gap(t_seconds=4, reason="route_conflict"),
        Entry.compression(t_seconds=5, model="m_1", covers_from=0, covers_to=4, summary="s"),
        entries[6],
    ]


@pytest.mark.parametrize("line", ["[SPEECH t=00:00:01]", "\\[x]", "[plain bracket", "\\\\two"])
def test_body_lines_that_look_like_headers_survive_a_round_trip(line: str) -> None:
    entry = Entry.speech(t_seconds=1, text=f"before\n{line}\nafter")
    result = ParseResult.from_text(render_session([entry]))
    assert result.ok
    assert result.entries == (entry,)


def test_valid_fixture() -> None:
    result = ParseResult.from_path(FIXTURES / "valid-basic.session.md")
    assert result.ok
    assert len(result.entries) == 9
    answer = result.entries[4]
    assert answer.tag == "A"
    assert answer.body.split("\n")[1:] == ["[not a header]", "\\backslash line"]
    compression = result.entries[6]
    assert compression.tag == "COMPRESSION"
    assert compression.attr("model") == "gemma-2-2b-q4"
    assert compression.attr("covers") == "00:00:00-00:41:10"
    assert result.entries[5].attr("reason") == "audio_focus_lost"


def test_truncated_fixture() -> None:
    result = ParseResult.from_path(FIXTURES / "truncated.session.md")
    assert result.truncated
    assert len(result.entries) == 3
    assert not result.problems


def test_unclosed_compression_fixture_is_dropped_and_truncated() -> None:
    result = ParseResult.from_path(FIXTURES / "unclosed-compression.session.md")
    assert result.truncated
    assert [entry.tag for entry in result.entries] == ["SESSION", "SPEECH"]
    assert not result.problems


def test_invalid_entries_fixture_reports_three_problems() -> None:
    result = ParseResult.from_path(FIXTURES / "invalid-entries.session.md")
    assert not result.truncated
    assert len(result.entries) == 2
    assert len(result.problems) == 3
    assert not result.ok


def test_header_inside_open_compression_block_is_a_problem() -> None:
    text = (
        "[COMPRESSION-START t=00:00:10 model=m covers=00:00:00-00:00:09]\n"
        "summary\n"
        "[SPEECH t=00:00:11]\nhi\n\n"
    )
    result = ParseResult.from_text(text)
    assert [entry.tag for entry in result.entries] == ["SPEECH"]
    assert any("not closed" in problem for problem in result.problems)


def test_compression_end_without_start_is_a_problem() -> None:
    result = ParseResult.from_text("[COMPRESSION-END t=00:00:10]\n\n")
    assert result.entries == ()
    assert any("without START" in problem for problem in result.problems)


def test_empty_text_is_ok_and_empty() -> None:
    result = ParseResult.from_text("")
    assert result.ok
    assert result.entries == ()


def test_speech_text_joins_only_speech() -> None:
    result = ParseResult.from_path(FIXTURES / "valid-basic.session.md")
    text = result.speech_text()
    assert "840 euros" in text
    assert "What did I say" not in text


def test_invalid_entries_fixture_problem_texts() -> None:
    result = ParseResult.from_path(FIXTURES / "invalid-entries.session.md")
    assert result.problems == (
        "line 1: text before the first header",
        "line 4: SPEECH without a valid t=HH:MM:SS",
        "line 7: unknown tag FOO",
    )


GOOD = "[SESSION id=s started=t format=1]\n\n[SPEECH t=00:00:05]\nhello\n\n"
OPEN_COMPRESSION = "[COMPRESSION-START t=00:01:00 model=m covers=00:00:00-00:00:59]\n"
INTERRUPTED_5 = "[INTERRUPTED t=00:00:05]\n\n"


def test_recovery_of_a_clean_file_only_appends_the_marker() -> None:
    recovery = Recovery.from_text(GOOD)
    assert recovery.kept == GOOD
    assert recovery.dropped_tail == ""
    assert recovery.appended == INTERRUPTED_5


def test_recovery_of_an_empty_file() -> None:
    recovery = Recovery.from_text("")
    assert (recovery.kept, recovery.dropped_tail) == ("", "")
    assert recovery.appended == "[INTERRUPTED t=00:00:00]\n\n"


def test_recovery_uses_the_given_time() -> None:
    assert Recovery.from_text(GOOD, t_seconds=90).appended == "[INTERRUPTED t=00:01:30]\n\n"


@pytest.mark.parametrize(
    "tail",
    [
        "[SPEECH t=00:00:09]\nhal",  # body cut mid-line
        "[SPEECH t=00:00:09]\nhalf\n",  # body line complete, entry not terminated
        "[SPEECH t=00:00:09]\n",  # header only
        "[SPEECH t=00:0",  # header cut mid-line
        "[SPEECH t=00:00:09]",  # header complete but no newline
    ],
)
def test_recovery_cuts_a_half_written_entry_and_keeps_the_tail(tail: str) -> None:
    recovery = Recovery.from_text(GOOD + tail)
    assert recovery.kept == GOOD
    assert recovery.dropped_tail == tail
    assert recovery.appended == INTERRUPTED_5


@pytest.mark.parametrize("summary", ["summary\n", "summary\n\nmore\n", "summary\n\n"])
def test_recovery_cuts_an_open_compression_block(summary: str) -> None:
    recovery = Recovery.from_text(GOOD + OPEN_COMPRESSION + summary)
    assert recovery.kept == GOOD
    assert recovery.dropped_tail == OPEN_COMPRESSION + summary


def test_recovery_keeps_a_closed_compression_block() -> None:
    closed = OPEN_COMPRESSION + "summary\n[COMPRESSION-END t=00:01:00]\n\n"
    recovery = Recovery.from_text(GOOD + closed)
    assert recovery.kept == GOOD + closed
    assert recovery.dropped_tail == ""
    assert recovery.appended == "[INTERRUPTED t=00:01:00]\n\n"


def test_recovery_counts_utf8_bytes_not_characters() -> None:
    text = GOOD + "[SPEECH t=00:00:07]\nGrüße \U0001f600\n\n"
    recovery = Recovery.from_text(text)
    assert recovery.kept == text
    assert recovery.kept_utf8_bytes == len(text.encode("utf-8"))
    assert recovery.kept_utf8_bytes > len(text)


def test_recovery_from_every_prefix_of_a_valid_file_is_valid() -> None:
    text = (FIXTURES / "valid-basic.session.md").read_text(encoding="utf-8")
    previous_count = 0
    for cut in range(len(text) + 1):
        prefix = text[:cut]
        recovery = Recovery.from_text(prefix)
        assert recovery.kept + recovery.dropped_tail == prefix, cut
        result = ParseResult.from_text(recovery.kept + recovery.appended)
        assert result.ok, (cut, result.problems)
        assert result.entries[-1].tag == "INTERRUPTED", cut
        # Cutting must never be more aggressive than needed: whole entries are never lost.
        assert len(result.entries) >= previous_count, cut
        previous_count = len(result.entries)
    assert Recovery.from_text(text).kept == text


def test_recovery_does_not_stack_interrupted_markers() -> None:
    once = Recovery.from_text(GOOD)
    twice = Recovery.from_text(once.kept + once.appended)
    assert twice.kept == once.kept + once.appended
    assert twice.dropped_tail == ""
    assert twice.appended == ""


def test_recovery_drops_a_closed_compression_block_without_its_blank_line() -> None:
    block = OPEN_COMPRESSION + "summary\n[COMPRESSION-END t=00:01:00]\n"
    recovery = Recovery.from_text(GOOD + block)
    assert recovery.kept == GOOD
    assert recovery.dropped_tail == block


def test_recovery_of_text_without_any_header_cuts_everything() -> None:
    recovery = Recovery.from_text("garbage\n")
    assert (recovery.kept, recovery.dropped_tail) == ("", "garbage\n")


def test_recovery_with_a_torn_character_in_the_tail_keeps_the_byte_count() -> None:
    # A torn UTF-8 write decodes (errors="replace") to U+FFFD, only inside the dropped tail.
    replacement_char = chr(0xFFFD)
    recovery = Recovery.from_text(GOOD + "[SPEECH t=00:00:09]\nGr" + replacement_char)
    assert recovery.kept == GOOD
    assert recovery.kept_utf8_bytes == len(GOOD.encode("utf-8"))
