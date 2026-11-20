from pathlib import Path

import pytest

from voicetools.session import Entry, ParseResult, format_time, parse_time, render_session

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
