# tools/ agent notes

Python 3.10+, no runtime dependencies. Run from this directory:

```bash
pip install -e ".[dev]" && ruff check . && ruff format --check . && mypy src && pytest
```

Style (see root AGENTS.md): frozen dataclasses with behavior as methods, classmethod alternate constructors,
keyword-only arguments for more than two parameters, `pathlib.Path` at boundaries, `mypy --strict` clean.
`voicetools sync-agents` generates `.claude/agents/` and `.github/agents/` from `agents/`; keep them in sync.
The session parser mirrors `android/core` and `docs/session-format.md`; both must agree on `fixtures/`.
