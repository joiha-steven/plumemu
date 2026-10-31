# Code

- English everywhere: code, comments, documents and commit messages. Other languages appear only in
  translation files (`res/values-<lang>/strings.xml`), so every contributor can read all of it.
- Kotlin, official style (`ktlint` defaults). Formatting is fixed by the tool, not argued about.
- Names say what a thing does: `GameScanner.scanFolder`, not `Helper.process`. Booleans read as
  questions (`isHidden`, `hasArtwork`).
- Comments say **why**, only when the reason is not visible in the code. No dates, no history,
  no "fixed bug" notes; git holds those. Comments in public files carry no author, date, rule
  number, plan step or private file; `scripts/guards/public-boundary.py` enforces it.
- One file, one main idea. A file over 400 lines or a function over 60 lines is a sign to split;
  the guard in `verify.sh` fails at 600 / 100.
- Errors are never swallowed. Catch where you can act: show a message the person understands and
  log the cause with enough detail (`what`, which game or folder by id, the exception class).
  `catch (e: Exception) {}` is a defect.
- Logs never hold game titles, file names, paths or document URIs. Exception messages
  from content providers and `SecurityException`s often contain the document URI, so log the
  exception class (and ids), not its message, in scan and launch code.
- Results that can fail return a sealed type (`Result`-like) rather than `null` when the caller
  must tell failures apart.
- No `!!` outside tests. No `@Suppress` to silence a warning without a comment saying why.
- Coroutines: structured, scoped to a lifecycle or a ViewModel; blocking I/O on `Dispatchers.IO`.
- Public functions of a module are the module's door; everything else is `internal`.
