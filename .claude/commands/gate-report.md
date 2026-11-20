---
description: Turn pasted measurements into a results log entry for a gate, without inventing numbers
argument-hint: [GATE-ID, e.g. GATE-03]
---

Create a new entry for `$ARGUMENTS` in `docs/results/RESULTS-LOG.md` using the template at the top of that file.

Rules:
1. Use only numbers the human pasted or that exist in a file they point you to. If a field is missing, write `NOT MEASURED`.
2. Compare the numbers to the thresholds in `docs/gates.md` and write the proposed label (PASS, MARGINAL, FAIL).
   Mark it `PROPOSED`: only a human confirms a gate.
3. Record device, Android version, tool and model versions, and the recording used, or `UNKNOWN`.
4. Do not edit thresholds.
