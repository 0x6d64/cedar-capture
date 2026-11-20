"""Generate agent definition files for Claude Code and GitHub Copilot from one source.

Sources live in `agents/*.md` (see `agents/README.md`). Outputs are `.claude/agents/<name>.md`
and `.github/agents/<name>.agent.md`. Generated files carry a marker line and must not be edited
by hand.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

_FRONTMATTER_RE = re.compile(r"\A---\n(.*?)\n---\n(.*)\Z", re.DOTALL)
_NAME_RE = re.compile(r"^[a-z][a-z0-9-]*$")
_KNOWN_KEYS = frozenset({"name", "description", "claude_tools"})
_RESERVED_SOURCES = frozenset({"README.md", "_common.md"})
_MARKER = "GENERATED from agents/{name}.md by `python -m voicetools sync-agents`. Do not edit."
_MARKER_PREFIX = "<!-- GENERATED from agents/"


def _quote(value: str) -> str:
    """Quote a value as a YAML double-quoted string."""
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def _parse_meta(text: str, *, source: str) -> dict[str, str]:
    meta: dict[str, str] = {}
    for line in text.split("\n"):
        key, separator, value = line.partition(":")
        key = key.strip()
        if not separator or key not in _KNOWN_KEYS:
            raise ValueError(f"{source}: unsupported frontmatter line {line!r}")
        meta[key] = value.strip()
    missing = _KNOWN_KEYS - meta.keys()
    if missing:
        raise ValueError(f"{source}: missing frontmatter keys {sorted(missing)}")
    return meta


@dataclass(frozen=True, slots=True)
class AgentSpec:
    """One agent role. `body` already includes the shared preamble."""

    name: str
    description: str
    claude_tools: tuple[str, ...]
    body: str

    @classmethod
    def from_text(cls, text: str, *, common: str, source: str) -> AgentSpec:
        match = _FRONTMATTER_RE.match(text)
        if match is None:
            raise ValueError(f"{source}: missing frontmatter")
        meta = _parse_meta(match.group(1), source=source)
        name = meta["name"]
        if _NAME_RE.match(name) is None:
            raise ValueError(f"{source}: invalid agent name {name!r}")
        if not meta["description"]:
            raise ValueError(f"{source}: empty description")
        tools = tuple(tool.strip() for tool in meta["claude_tools"].split(",") if tool.strip())
        body = f"{common.strip()}\n\n{match.group(2).strip()}\n"
        return cls(name, meta["description"], tools, body)

    @classmethod
    def from_path(cls, path: Path, *, common: str) -> AgentSpec:
        spec = cls.from_text(path.read_text(encoding="utf-8"), common=common, source=path.name)
        if spec.name != path.stem:
            raise ValueError(f"{path.name}: name {spec.name!r} must equal the file name")
        return spec

    @property
    def marker(self) -> str:
        return f"<!-- {_MARKER.format(name=self.name)} -->"

    def render_claude(self) -> str:
        """Claude Code subagent file (frontmatter fields: name, description, tools)."""
        head = (
            f"---\nname: {self.name}\ndescription: {_quote(self.description)}\n"
            f"tools: {', '.join(self.claude_tools)}\n---\n"
        )
        return f"{head}\n{self.marker}\n\n{self.body}"

    def render_copilot(self) -> str:
        """Copilot custom agent file. No tool list: tool names differ between hosts (UNVERIFIED)."""
        head = f"---\ndescription: {_quote(self.description)}\n---\n"
        return f"{head}\n{self.marker}\n\n{self.body}"


def load_specs(root: Path) -> list[AgentSpec]:
    """Read every agent source under `<root>/agents`, sorted by name."""
    source_dir = root / "agents"
    common = (source_dir / "_common.md").read_text(encoding="utf-8")
    paths = sorted(p for p in source_dir.glob("*.md") if p.name not in _RESERVED_SOURCES)
    return [AgentSpec.from_path(path, common=common) for path in paths]


def expected_outputs(root: Path) -> dict[Path, str]:
    """Map every generated file path to the text it must contain."""
    outputs: dict[Path, str] = {}
    for spec in load_specs(root):
        outputs[root / ".claude" / "agents" / f"{spec.name}.md"] = spec.render_claude()
        outputs[root / ".github" / "agents" / f"{spec.name}.agent.md"] = spec.render_copilot()
    return outputs


def stale_outputs(root: Path, expected: dict[Path, str]) -> list[Path]:
    """Generated files that no longer have a source (found by their marker line)."""
    stale: list[Path] = []
    for directory in (root / ".claude" / "agents", root / ".github" / "agents"):
        for path in sorted(directory.glob("*.md")):
            if path not in expected and _MARKER_PREFIX in path.read_text(encoding="utf-8"):
                stale.append(path)
    return stale


def sync_agents(root: Path, *, check: bool) -> list[Path]:
    """Write the generated files; with check=True only report missing, differing or stale paths."""
    expected = expected_outputs(root)
    problems = [
        path
        for path, text in expected.items()
        if not path.exists() or path.read_text(encoding="utf-8") != text
    ]
    stale = stale_outputs(root, expected)
    if check:
        return problems + stale
    for path in problems:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(expected[path], encoding="utf-8", newline="\n")
    for path in stale:
        path.unlink()
    return problems + stale
