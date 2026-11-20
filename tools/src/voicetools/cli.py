"""Command line entry point: `voicetools lint-session` and `voicetools check-facts`."""

from __future__ import annotations

import argparse
from collections.abc import Sequence
from pathlib import Path

from voicetools.agents import sync_agents
from voicetools.facts import FactSet
from voicetools.session import ParseResult

REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_FACTS = REPO_ROOT / "docs" / "testing" / "planted-facts.json"


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="voicetools")
    commands = parser.add_subparsers(dest="command", required=True)

    lint = commands.add_parser("lint-session", help="check a session file against the format spec")
    lint.add_argument("path", type=Path)
    lint.add_argument(
        "--allow-truncated", action="store_true", help="do not fail on a cut-off file"
    )

    check = commands.add_parser("check-facts", help="score a transcript against the planted facts")
    check.add_argument("transcript", type=Path)
    check.add_argument("--facts", type=Path, default=DEFAULT_FACTS)
    check.add_argument(
        "--session",
        action="store_true",
        help="the file is a session file; score only SPEECH entries",
    )

    sync = commands.add_parser(
        "sync-agents", help="generate .claude/agents and .github/agents from agents/*.md"
    )
    sync.add_argument("--check", action="store_true", help="only report files that are out of date")
    sync.add_argument("--root", type=Path, default=REPO_ROOT, help="repository root")
    return parser


def _lint(path: Path, *, allow_truncated: bool) -> int:
    result = ParseResult.from_path(path)
    print(f"{len(result.entries)} entries")
    for problem in result.problems:
        print(f"problem: {problem}")
    if result.truncated:
        print("warning: file is truncated")
    failed = bool(result.problems) or (result.truncated and not allow_truncated)
    return 1 if failed else 0


def _check_facts(transcript: Path, facts: Path, *, session: bool) -> int:
    text = (
        ParseResult.from_path(transcript).speech_text()
        if session
        else transcript.read_text(encoding="utf-8")
    )
    print(FactSet.load(facts).score(text).report())
    return 0


def _sync_agents(root: Path, *, check: bool) -> int:
    changed = sync_agents(root, check=check)
    for path in changed:
        print(f"{'out of date' if check else 'updated'}: {path.relative_to(root)}")
    if not changed:
        print("agent files are in sync")
    return 1 if (check and changed) else 0


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    if args.command == "lint-session":
        return _lint(args.path, allow_truncated=args.allow_truncated)
    if args.command == "sync-agents":
        return _sync_agents(args.root, check=args.check)
    return _check_facts(args.transcript, args.facts, session=args.session)
