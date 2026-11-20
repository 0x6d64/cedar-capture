---
description: Pick up a work item from docs/plan/steps.md and carry out the parts an agent can do
argument-hint: [STEP-ID, e.g. STEP-03]
---

Work on `$ARGUMENTS` (if empty, choose the lowest-numbered step in `docs/plan/steps.md` that is not marked done and
whose "Who" column contains `A`).

1. Read `AGENTS.md`, then the step's row in `docs/plan/steps.md`, then every `REQ-*`, `GATE-*` and `DEC-*` it mentions.
2. State your plan in five lines or fewer, including which parts need a human (`D` or `H`) and which you will do.
3. Write the failing test first, then the implementation. Run the checks from `AGENTS.md`.
4. Finish with the hand-off note required by the definition of done. Do not mark the step done if its exit check needs a
   device or a human; say exactly what they must run.
