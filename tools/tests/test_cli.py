from pathlib import Path

import pytest

from voicetools.cli import main

FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "sessions"


def test_lint_valid_session_returns_zero(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["lint-session", str(FIXTURES / "valid-basic.session.md")]) == 0
    assert "9 entries" in capsys.readouterr().out


def test_lint_truncated_session_fails_unless_allowed() -> None:
    path = str(FIXTURES / "truncated.session.md")
    assert main(["lint-session", path]) == 1
    assert main(["lint-session", path, "--allow-truncated"]) == 0


def test_lint_invalid_session_fails() -> None:
    assert main(["lint-session", str(FIXTURES / "invalid-entries.session.md")]) == 1


def test_check_facts_on_session_file(capsys: pytest.CaptureFixture[str]) -> None:
    assert main(["check-facts", str(FIXTURES / "valid-basic.session.md"), "--session"]) == 0
    out = capsys.readouterr().out
    assert "facts found:" in out
    assert "F01" not in out.split("missing:")[0]
