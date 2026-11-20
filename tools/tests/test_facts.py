import json
from pathlib import Path

from voicetools.facts import FactSet, normalize

REPO = Path(__file__).resolve().parents[2]
FACTS = REPO / "docs" / "testing" / "planted-facts.json"
QUESTIONS = REPO / "docs" / "testing" / "questions.json"

FULL_TRANSCRIPT = """
The cabin rental costs 840 euros for the week. Ilinca suggested the route.
The inspection is on March 14th. Maybe we should switch suppliers.
We decided to keep the old router.
The meeting is Tuesday, no, Thursday at 15:30.
We did not order the extra memory. The drive is 12 kilometers, not 120.
Ticket number A-4471. The blue folder holds contracts, the green folder holds invoices.
Dragos will call back on Monday. The shop in Sibiu closes at 18:00.
The warehouse code is K-7. Mara's flight lands at 21:45.
The battery pack is 4,800 milliamp hours. Quantization is fun.
A foreground service keeps recording. Voice activity detection finds speech.
I might cancel the trip. The deadline is the 30th.
"""


def test_normalize() -> None:
    assert normalize("Dragoș didn't order 4,800 units!") == "dragos didnt order 4800 units"
    assert normalize("Meeting at 15:30.") == "meeting at 15:30"
    assert normalize("A-4471") == "a 4471"


def test_fact_file_is_complete_and_unique() -> None:
    fact_set = FactSet.load(FACTS)
    ids = [fact.fact_id for fact in fact_set.facts]
    assert ids == [f"F{number:02d}" for number in range(1, 21)]
    assert fact_set.strict_kinds >= {"number", "name", "code", "date", "time"}


def test_full_transcript_finds_every_fact() -> None:
    score = FactSet.load(FACTS).score(FULL_TRANSCRIPT)
    assert [fact.fact_id for fact in score.missing] == []
    assert score.strict_percent == 100.0


def test_missing_facts_are_reported() -> None:
    transcript = FULL_TRANSCRIPT.replace("840", "eight forty").replace("K-7", "case seven")
    score = FactSet.load(FACTS).score(transcript)
    assert {fact.fact_id for fact in score.missing} == {"F01", "F13"}
    assert score.strict_percent < 100.0
    assert "F01" in score.report()


def test_a_number_inside_a_longer_number_does_not_count() -> None:
    score = FactSet.load(FACTS).score("The cabin costs 8400 euros.")
    assert "F01" in {fact.fact_id for fact in score.missing}


def test_possessive_name_matches() -> None:
    score = FactSet.load(FACTS).score("Mara's flight lands at 9:45 pm")
    assert "F14" in {fact.fact_id for fact in score.found}


def test_questions_file_matches_the_protocol() -> None:
    data = json.loads(QUESTIONS.read_text(encoding="utf-8"))
    questions = data["questions"]
    assert [q["id"] for q in questions] == list(range(1, 17))
    assert [q["id"] for q in questions if q["core"]] == list(range(1, 12))
    fact_ids = {fact.fact_id for fact in FactSet.load(FACTS).facts}
    assert all(q["fact"] is None or q["fact"] in fact_ids for q in questions)
    assert [q["id"] for q in questions if q["type"] == "never"] == [9, 10, 11]
