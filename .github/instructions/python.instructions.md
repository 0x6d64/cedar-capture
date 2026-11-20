---
applyTo: "tools/**/*.py"
---
Python 3.10+, full type hints, mypy --strict and ruff clean. Frozen dataclasses with behavior as methods, alternate
constructors as classmethods, keyword-only arguments for more than two parameters, pathlib.Path at boundaries.
No runtime dependencies. Tests use pytest and the shared inputs in `fixtures/`.
