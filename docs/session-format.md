# Session file format (version 1)

Status: proposed. Changing it requires bumping `format=` and updating `fixtures/`, `tools/` and `android/core` together.

A session is a UTF-8 text file made of **entries**. An entry is a header line followed by a body.

```
[TAG t=HH:MM:SS key=value ...]
body line 1
body line 2

```

## Header line

- Regex: `^\[([A-Z][A-Z-]*)((?: [a-z_]+=[^\s\]]+)*)\]$`
- Attribute values contain no whitespace and no `]`. Writers replace those with `_`.
- `t` is the time since the session start, `HH:MM:SS` (hours may have more than two digits).
- The first entry is `[SESSION id=<id> started=<ISO-8601> format=1]` (no `t`).

## Tags

| Tag | Attributes | Body |
| --- | --- | --- |
| `SESSION` | `id`, `started`, `format` | empty |
| `SPEECH` | `t` | transcribed user speech |
| `Q` | `t` | question to the LLM |
| `A` | `t` | LLM answer |
| `GAP` | `t`, `reason` | empty. Capture was paused (audio focus lost, route conflict) |
| `INTERRUPTED` | `t` | empty. Written when an unfinished session is recovered |
| `COMPRESSION-START` / `COMPRESSION-END` | START: `t`, `model`, `covers=HH:MM:SS-HH:MM:SS`; END: `t` | the summary sits between the two lines |

In memory, the two compression lines form one `COMPRESSION` entry. The raw transcript is never changed by compression;
the summary is an additional block.

## Escaping

A body line that starts with `[` or `\` is written with one leading backslash. Readers remove one leading backslash from
body lines. This makes the format reversible: no body line can be mistaken for a header.

## Reading rules

1. If the file does not end with a newline, the last line is incomplete: ignore it and report the file as truncated.
2. A `COMPRESSION-START` without its `COMPRESSION-END` before end of file means truncation: drop that block.
3. A header inside an open compression block is a corruption: report a problem, drop the block, continue.
4. An entry without a valid `t` (except `SESSION`) is a problem; skip it and continue.
5. Trailing blank lines at the end of a body are not part of the body.
6. Lines before the first header are a problem and are ignored.

## Writing rules

1. Append each entry as one write, ending with a blank line, and flush after every entry (REQ-SES-02).
2. On recovery of a session that does not end cleanly, cut the file back to its last whole entry, then append
   `[INTERRUPTED t=...]`. The file is not changed in any other way.
   - A last line without a newline is dropped.
   - A last entry that is not followed by its blank line is dropped.
   - A `COMPRESSION-START` without its `COMPRESSION-END` is dropped together with its summary.
   - The dropped text is saved in a side file by the caller; recovery never throws text away.
   - `t` is the time of recovery when the caller knows it, otherwise the latest `t` among the kept entries.
   - If the kept text already ends with an `INTERRUPTED` entry, nothing is appended, so recovering twice is safe.
   - Session files are LF-only; writers never produce a carriage return, and recovery does not support CRLF files.
   - Read the file as bytes and decode UTF-8 leniently (replace bad bytes). A write torn inside a multi-byte character
     can only fall in the dropped tail, so `kept` is exact. Cut the file at `kept_utf8_bytes` and write the side file
     from the original bytes after that offset, not from the decoded `dropped_tail`.
   - Limit: a write that was torn exactly after a blank line inside a body cannot be detected. The entry then keeps the
     part that was written.
   Both implementations offer this as a pure text function: `SessionFormat.recover` (Kotlin) and
   `Recovery.from_text` (Python). They are tested against every prefix of `valid-basic.session.md`.

## Test inputs

`fixtures/sessions/*.session.md`. The Kotlin and Python parsers must produce the same results for them.
