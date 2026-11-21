"""Parse and render session files. Specification: docs/session-format.md (format version 1)."""

from __future__ import annotations

import re
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

FORMAT_VERSION = 1

_HEADER_RE = re.compile(r"^\[([A-Z][A-Z-]*)((?: [a-z_]+=[^\s\]]+)*)\]$")
_ATTR_RE = re.compile(r" ([a-z_]+)=([^\s\]]+)")
_TIME_RE = re.compile(r"^(\d{2,}):([0-5]\d):([0-5]\d)$")
_COVERS_RE = re.compile(r"^(\d{2,}:[0-5]\d:[0-5]\d)-(\d{2,}:[0-5]\d:[0-5]\d)$")
_TOKEN_UNSAFE_RE = re.compile(r"[\s\]]+")
_SIMPLE_TAGS = frozenset({"SPEECH", "Q", "A", "GAP", "INTERRUPTED"})


def format_time(seconds: int) -> str:
    """Render seconds as HH:MM:SS (hours may exceed two digits)."""
    if seconds < 0:
        raise ValueError("seconds must be non-negative")
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def parse_time(text: str) -> int | None:
    """Parse HH:MM:SS into seconds, or return None if the text is not a valid time."""
    match = _TIME_RE.match(text)
    if match is None:
        return None
    hours, minutes, secs = (int(group) for group in match.groups())
    return hours * 3600 + minutes * 60 + secs


def _token(value: str) -> str:
    """Make a value safe for a header attribute: no whitespace, no closing bracket."""
    cleaned = _TOKEN_UNSAFE_RE.sub("_", value.strip())
    return cleaned or "unknown"


def _escape(body: str) -> str:
    return "\n".join(
        "\\" + line if line.startswith(("[", "\\")) else line for line in body.split("\n")
    )


def _unescape(lines: list[str]) -> str:
    return "\n".join(line[1:] if line.startswith("\\") else line for line in lines)


def _header(tag: str, attrs: Iterable[tuple[str, str]]) -> str:
    return f"[{tag}{''.join(f' {key}={value}' for key, value in attrs)}]"


@dataclass(frozen=True, slots=True)
class Entry:
    """One session entry. `tag` is one of SESSION, SPEECH, Q, A, GAP, INTERRUPTED, COMPRESSION."""

    tag: str
    t_seconds: int
    attrs: tuple[tuple[str, str], ...] = ()
    body: str = ""

    @classmethod
    def session_start(
        cls, *, session_id: str, started: str, format_version: int = FORMAT_VERSION
    ) -> Entry:
        attrs = (
            ("id", _token(session_id)),
            ("started", _token(started)),
            ("format", str(format_version)),
        )
        return cls("SESSION", 0, attrs)

    @classmethod
    def speech(cls, *, t_seconds: int, text: str) -> Entry:
        return cls("SPEECH", t_seconds, (), text)

    @classmethod
    def question(cls, *, t_seconds: int, text: str) -> Entry:
        return cls("Q", t_seconds, (), text)

    @classmethod
    def answer(cls, *, t_seconds: int, text: str) -> Entry:
        return cls("A", t_seconds, (), text)

    @classmethod
    def gap(cls, *, t_seconds: int, reason: str) -> Entry:
        return cls("GAP", t_seconds, (("reason", _token(reason)),))

    @classmethod
    def interrupted(cls, *, t_seconds: int) -> Entry:
        return cls("INTERRUPTED", t_seconds)

    @classmethod
    def compression(
        cls, *, t_seconds: int, model: str, covers_from: int, covers_to: int, summary: str
    ) -> Entry:
        covers = f"{format_time(covers_from)}-{format_time(covers_to)}"
        return cls(
            "COMPRESSION", t_seconds, (("model", _token(model)), ("covers", covers)), summary
        )

    def attr(self, name: str) -> str | None:
        """Return the value of an attribute, or None."""
        for key, value in self.attrs:
            if key == name:
                return value
        return None

    def render(self) -> str:
        """Render this entry as text, ending with a blank line."""
        if self.tag == "SESSION":
            return f"{_header('SESSION', self.attrs)}\n\n"
        time_attr = (("t", format_time(self.t_seconds)),)
        body = f"{_escape(self.body)}\n" if self.body else ""
        if self.tag == "COMPRESSION":
            start = _header("COMPRESSION-START", (*time_attr, *self.attrs))
            end = _header("COMPRESSION-END", time_attr)
            return f"{start}\n{body}{end}\n\n"
        return f"{_header(self.tag, (*time_attr, *self.attrs))}\n{body}\n"


@dataclass(slots=True)
class _Block:
    tag: str
    attrs: dict[str, str]
    line_no: int
    body_lines: list[str]


def _split_blocks(lines: list[str], problems: list[str]) -> list[_Block]:
    blocks: list[_Block] = []
    for number, line in enumerate(lines, start=1):
        match = _HEADER_RE.match(line)
        if match is not None:
            blocks.append(
                _Block(match.group(1), dict(_ATTR_RE.findall(match.group(2))), number, [])
            )
        elif blocks:
            blocks[-1].body_lines.append(line)
        elif line.strip():
            problems.append(f"line {number}: text before the first header")
    return blocks


def _body(lines: list[str]) -> str:
    end = len(lines)
    while end > 0 and lines[end - 1] == "":
        end -= 1
    return _unescape(lines[:end])


def _entry_from(block: _Block, problems: list[str]) -> Entry | None:
    if block.tag == "SESSION":
        return Entry("SESSION", 0, tuple(block.attrs.items()))
    seconds = parse_time(block.attrs.get("t", ""))
    if seconds is None:
        problems.append(f"line {block.line_no}: {block.tag} without a valid t=HH:MM:SS")
        return None
    if block.tag not in _SIMPLE_TAGS:
        problems.append(f"line {block.line_no}: unknown tag {block.tag}")
        return None
    extra = tuple((key, value) for key, value in block.attrs.items() if key != "t")
    return Entry(block.tag, seconds, extra, _body(block.body_lines))


def _compression_entry(block: _Block, problems: list[str]) -> Entry | None:
    seconds = parse_time(block.attrs.get("t", ""))
    model = block.attrs.get("model")
    covers = block.attrs.get("covers", "")
    if seconds is None or model is None or _COVERS_RE.match(covers) is None:
        problems.append(
            f"line {block.line_no}: COMPRESSION-START needs t, model and covers=HH:MM:SS-HH:MM:SS"
        )
        return None
    return Entry(
        "COMPRESSION", seconds, (("model", model), ("covers", covers)), _body(block.body_lines)
    )


class _Assembler:
    """Turns header blocks into entries, pairing COMPRESSION-START with COMPRESSION-END."""

    def __init__(self) -> None:
        self.entries: list[Entry] = []
        self.problems: list[str] = []
        self._open: _Block | None = None

    @property
    def unfinished(self) -> bool:
        return self._open is not None

    @property
    def open_line_no(self) -> int | None:
        """Line of a COMPRESSION-START that has no END yet, or None."""
        return None if self._open is None else self._open.line_no

    def feed(self, block: _Block) -> None:
        if block.tag == "COMPRESSION-START":
            self._close_unfinished()
            self._open = block
        elif block.tag == "COMPRESSION-END":
            self._finish_compression(block)
        else:
            self._close_unfinished()
            self._add(_entry_from(block, self.problems))

    def _close_unfinished(self) -> None:
        if self._open is not None:
            self.problems.append(f"line {self._open.line_no}: COMPRESSION-START not closed")
            self._open = None

    def _finish_compression(self, end: _Block) -> None:
        if self._open is None:
            self.problems.append(f"line {end.line_no}: COMPRESSION-END without START")
            return
        start, self._open = self._open, None
        self._add(_compression_entry(start, self.problems))

    def _add(self, entry: Entry | None) -> None:
        if entry is not None:
            self.entries.append(entry)


@dataclass(frozen=True, slots=True)
class ParseResult:
    """Entries read from a session file, plus truncation and problem reports."""

    entries: tuple[Entry, ...]
    truncated: bool
    problems: tuple[str, ...]

    @classmethod
    def from_text(cls, text: str) -> ParseResult:
        lines = text.split("\n")
        truncated = lines[-1] != ""  # no trailing newline: the last line is incomplete
        problems: list[str] = []
        assembler = _Assembler()
        for block in _split_blocks(lines[:-1], problems):
            assembler.feed(block)
        # A compression block still open at end of file is dropped and counts as truncation.
        return cls(
            tuple(assembler.entries),
            truncated or assembler.unfinished,
            tuple(problems + assembler.problems),
        )

    @classmethod
    def from_path(cls, path: Path) -> ParseResult:
        return cls.from_text(path.read_text(encoding="utf-8"))

    @property
    def ok(self) -> bool:
        return not self.truncated and not self.problems

    def speech_text(self) -> str:
        """All SPEECH bodies joined with newlines (what the planted-fact scorer reads)."""
        return "\n".join(entry.body for entry in self.entries if entry.tag == "SPEECH")


def _line_offset(lines: list[str], line_no: int) -> int:
    """Character offset of the start of 1-based line `line_no` in a text split on newlines."""
    return sum(len(line) + 1 for line in lines[: line_no - 1])


def _last_entry_line(blocks: list[_Block]) -> int:
    """Line where the last entry starts (a closed compression block starts at its START line)."""
    last = blocks[-1]
    if last.tag == "COMPRESSION-END":
        for block in reversed(blocks[:-1]):
            if block.tag == "COMPRESSION-START":
                return block.line_no
    return last.line_no


def _recovery_cut(text: str) -> int:
    """Offset where an unfinished session file must be cut so that only whole entries remain."""
    base = text[: text.rfind("\n") + 1]  # drop a last line that has no newline
    if not base:
        return 0
    lines = base.split("\n")[:-1]
    blocks = _split_blocks(lines, [])
    assembler = _Assembler()
    for block in blocks:
        assembler.feed(block)
    if assembler.open_line_no is not None:
        return _line_offset(lines, assembler.open_line_no)
    if not base.endswith("\n\n"):
        return _line_offset(lines, _last_entry_line(blocks)) if blocks else 0
    return len(base)


@dataclass(frozen=True, slots=True)
class Recovery:
    """How to repair an unfinished session file (docs/session-format.md, writing rule 2).

    Write `kept + appended` back to the session file and store `dropped_tail` in a side file,
    so nothing is thrown away. Text in, text out: reading and writing files is the caller's job.
    """

    kept: str
    dropped_tail: str
    appended: str

    @classmethod
    def from_text(cls, text: str, *, t_seconds: int | None = None) -> Recovery:
        cut = _recovery_cut(text)
        kept = text[:cut]
        entries = ParseResult.from_text(kept).entries
        if entries and entries[-1].tag == "INTERRUPTED":
            return cls(kept, text[cut:], "")  # already marked: recovering twice adds nothing
        if t_seconds is None:
            t_seconds = max((entry.t_seconds for entry in entries), default=0)
        return cls(kept, text[cut:], Entry.interrupted(t_seconds=t_seconds).render())

    @property
    def kept_utf8_bytes(self) -> int:
        """Length of `kept` in bytes, for truncating the file on disk."""
        return len(self.kept.encode("utf-8"))


def render_session(entries: Iterable[Entry]) -> str:
    """Render entries as a complete session file."""
    return "".join(entry.render() for entry in entries)
