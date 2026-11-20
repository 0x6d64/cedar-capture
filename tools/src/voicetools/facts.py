"""Planted-fact scoring for the ground-truth recording. See docs/testing/test-protocol.md."""

from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any


def normalize(text: str) -> str:
    """Lowercase, strip accents and punctuation so transcripts compare fairly.

    Apostrophes are removed ("didn't" -> "didnt"), thousands separators are dropped
    ("4,800" -> "4800"), colons are kept ("15:30"), and everything else that is not a letter
    or digit becomes a space.
    """
    decomposed = unicodedata.normalize("NFKD", text)
    stripped = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    lowered = stripped.lower().replace("\u2019", "'").replace("'", "")
    lowered = re.sub(r"(?<=\d),(?=\d)", "", lowered)
    lowered = re.sub(r"[^a-z0-9: ]+", " ", lowered)
    return " ".join(lowered.split())


def _contains(normalized_text: str, normalized_alternative: str) -> bool:
    """Whole-word match; an optional trailing 's' allows possessives ("Mara's" -> "maras")."""
    pattern = rf"(?<![a-z0-9]){re.escape(normalized_alternative)}s?(?![a-z0-9])"
    return re.search(pattern, normalized_text) is not None


@dataclass(frozen=True, slots=True)
class Fact:
    """A planted fact. Found when every group has at least one alternative in the transcript."""

    fact_id: str
    kind: str
    description: str
    position: str
    requires: tuple[tuple[str, ...], ...]

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Fact:
        groups = tuple(
            tuple(normalize(str(alternative)) for alternative in group)
            for group in data["requires"]
        )
        return cls(
            fact_id=str(data["id"]),
            kind=str(data["kind"]),
            description=str(data["description"]),
            position=str(data.get("position", "unspecified")),
            requires=groups,
        )

    def found_in(self, normalized_text: str) -> bool:
        return all(
            any(_contains(normalized_text, alternative) for alternative in group)
            for group in self.requires
        )


@dataclass(frozen=True, slots=True)
class Score:
    """Outcome of checking one transcript."""

    found: tuple[Fact, ...]
    missing: tuple[Fact, ...]
    strict_kinds: frozenset[str]

    @property
    def total(self) -> int:
        return len(self.found) + len(self.missing)

    def _strict(self, facts: tuple[Fact, ...]) -> int:
        return sum(1 for fact in facts if fact.kind in self.strict_kinds)

    @property
    def strict_percent(self) -> float:
        """Percent of numbers, names, codes, dates, times and magnitudes found (GATE-04 input)."""
        strict_total = self._strict(self.found) + self._strict(self.missing)
        if strict_total == 0:
            return 0.0
        return 100.0 * self._strict(self.found) / strict_total

    def report(self) -> str:
        lines = [
            f"facts found: {len(self.found)} of {self.total}",
            f"strict kinds ({', '.join(sorted(self.strict_kinds))}): {self.strict_percent:.1f}%",
        ]
        if self.missing:
            lines.append("missing:")
            lines.extend(
                f"  {fact.fact_id} [{fact.kind}] {fact.description}" for fact in self.missing
            )
        return "\n".join(lines)


@dataclass(frozen=True, slots=True)
class FactSet:
    facts: tuple[Fact, ...]
    strict_kinds: frozenset[str]

    @classmethod
    def load(cls, path: Path) -> FactSet:
        data = json.loads(path.read_text(encoding="utf-8"))
        facts = tuple(Fact.from_dict(item) for item in data["facts"])
        return cls(facts, frozenset(str(kind) for kind in data["strict_kinds"]))

    def score(self, transcript: str) -> Score:
        normalized = normalize(transcript)
        found = tuple(fact for fact in self.facts if fact.found_in(normalized))
        missing = tuple(fact for fact in self.facts if not fact.found_in(normalized))
        return Score(found, missing, self.strict_kinds)
