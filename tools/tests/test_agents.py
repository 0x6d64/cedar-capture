from pathlib import Path

import pytest

from voicetools.agents import AgentSpec, expected_outputs, load_specs, stale_outputs, sync_agents
from voicetools.cli import main

REPO = Path(__file__).resolve().parents[2]

SOURCE = """---
name: demo-agent
description: Does a thing: "quoted" and with a colon.
claude_tools: Read, Grep
---
## Role

Body text.
"""


def make_repo(tmp_path: Path) -> Path:
    (tmp_path / "agents").mkdir()
    (tmp_path / "agents" / "_common.md").write_text("Common preamble.\n", encoding="utf-8")
    (tmp_path / "agents" / "README.md").write_text("ignored\n", encoding="utf-8")
    (tmp_path / "agents" / "demo-agent.md").write_text(SOURCE, encoding="utf-8")
    return tmp_path


def test_spec_parsing_and_rendering() -> None:
    spec = AgentSpec.from_text(SOURCE, common="Common preamble.", source="demo-agent.md")
    assert spec.name == "demo-agent"
    assert spec.claude_tools == ("Read", "Grep")
    assert spec.body.startswith("Common preamble.\n\n## Role")
    claude = spec.render_claude()
    assert claude.startswith(
        '---\nname: demo-agent\ndescription: "Does a thing: \\"quoted\\" and with a colon."\n'
    )
    assert "tools: Read, Grep\n---\n" in claude
    copilot = spec.render_copilot()
    assert "tools:" not in copilot
    assert copilot.startswith('---\ndescription: "Does a thing')
    assert "GENERATED from agents/demo-agent.md" in copilot


@pytest.mark.parametrize(
    "text",
    [
        "no frontmatter",
        "---\nname: a\ndescription: d\n---\nbody",
        "---\nname: a\ndescription: d\nclaude_tools: Read\nextra: x\n---\nbody",
        "---\nname: Bad Name\ndescription: d\nclaude_tools: Read\n---\nbody",
        "---\nname: a\ndescription:\nclaude_tools: Read\n---\nbody",
    ],
)
def test_invalid_sources_are_rejected(text: str) -> None:
    with pytest.raises(ValueError):
        AgentSpec.from_text(text, common="c", source="x.md")


def test_name_must_match_file_name(tmp_path: Path) -> None:
    root = make_repo(tmp_path)
    (root / "agents" / "demo-agent.md").rename(root / "agents" / "other.md")
    with pytest.raises(ValueError, match="must equal the file name"):
        load_specs(root)


def test_sync_writes_then_checks_clean_then_detects_drift_and_stale(tmp_path: Path) -> None:
    root = make_repo(tmp_path)
    assert len(sync_agents(root, check=True)) == 2  # both outputs missing
    assert len(sync_agents(root, check=False)) == 2
    assert sync_agents(root, check=True) == []

    claude = root / ".claude" / "agents" / "demo-agent.md"
    claude.write_text(claude.read_text(encoding="utf-8") + "hand edit\n", encoding="utf-8")
    assert sync_agents(root, check=True) == [claude]

    sync_agents(root, check=False)
    (root / "agents" / "demo-agent.md").unlink()
    assert len(stale_outputs(root, expected_outputs(root))) == 2
    sync_agents(root, check=False)
    assert not claude.exists()


def test_cli_check_fails_when_out_of_date(tmp_path: Path) -> None:
    root = make_repo(tmp_path)
    assert main(["sync-agents", "--root", str(root), "--check"]) == 1
    assert main(["sync-agents", "--root", str(root)]) == 0
    assert main(["sync-agents", "--root", str(root), "--check"]) == 0


def test_repository_agent_files_are_in_sync() -> None:
    assert sync_agents(REPO, check=True) == []


def test_repository_agents_follow_the_house_rules() -> None:
    specs = load_specs(REPO)
    assert {spec.name for spec in specs} == {
        "session-format-guardian",
        "kotlin-core-engineer",
        "python-tools-engineer",
        "android-app-engineer",
        "results-scribe",
        "reviewer",
        "doc-auditor",
    }
    for spec in specs:
        assert "AGENTS.md" in spec.body
        assert "UNVERIFIED" in spec.body
        assert "Human must:" in spec.body
        assert "\n" not in spec.description
        assert len(spec.description) <= 400
    read_only = {"reviewer", "doc-auditor"}
    for spec in specs:
        writes = {"Edit", "Write"} & set(spec.claude_tools)
        assert (not writes) == (spec.name in read_only), spec.name
