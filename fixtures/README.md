# fixtures/

Shared test inputs. Both `android/core` (Kotlin) and `tools` (Python) must produce the same results for these files.
Read-only ground truth: do not edit a fixture to make a test pass. Changing the session format means changing the
spec, both parsers and these files together (and bumping `format=`).

| File | Expected result |
| --- | --- |
| `sessions/valid-basic.session.md` | 9 entries, not truncated, no problems. Contains escaped body lines and a compression block |
| `sessions/truncated.session.md` | 3 entries, truncated (last line cut), no problems |
| `sessions/unclosed-compression.session.md` | 2 entries (compression block dropped), truncated, no problems |
| `sessions/invalid-entries.session.md` | 2 entries, not truncated, 3 problems (text before header, invalid time, unknown tag) |
