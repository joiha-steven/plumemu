# scripts

| Script | What it does |
|---|---|
| `setup-toolchain.sh` | Installs JDK 17, the Android SDK and the two reference emulators on a Mac; writes `local.properties`. Safe to re-run. |
| `verify.sh` | The checks the source passes. `--quick`: the guards, `./gradlew assembleDebug lintDebug` and the manifest-permissions guard. No flag: also every module's unit tests, `assembleRelease` and the licence guard. `--ci`: the same, as the build workflow runs it. One `PASS`, `FAIL` or `SKIP` line per check; exit code non-zero on any failure. Logs go to `build/verify/`. |
| `export-translations.py` | Copies the app's strings into `translations/` of this repository for people to read and correct: `translations/en/strings.xml` (the English master without its `translatable="false"` strings) and `translations/<lang>/strings.xml` for every `res/values-<lang>/`. `--to DIR` writes elsewhere. |
| `import-translation.py` | Brings a corrected `translations/<lang>/strings.xml` back over `app/src/main/res/values-<lang>/strings.xml` (changed elements only, keys must equal the English master's translatable ones). Refuses a language the app lacks, files over 2 MB, DOCTYPE or entities, anything but `<string>`, `<plurals>`, `<string-array>`; runs `guards/translations.py` and restores the old file when it fails. Exit 0 imported, 1 refused or guard failed, 2 usage. |

## Guards

Each guard is a Python 3 script (standard library only) in `guards/` that runs on its own:
`python3 scripts/guards/<guard>.py` scans the repository (tracked files plus new files not yet
added, ignored files excluded); `python3 scripts/guards/<guard>.py PATH ...` scans only those
files or folders. Output is `path:line: message` per violation and a last line
`<guard>: PASS` or `<guard>: FAIL (...)`; exit 0 pass, 1 violation, 2 the guard could not run.

| Guard | Fails on | Gate |
|---|---|---|
| `english-only.py` | Letters outside basic Latin in any text file, except `res/values-<lang>/` translations; typographic symbols in its `ALLOWED_CHARS` and proper names in `ALLOWED_WORDS` pass | quick, full |
| `ui-text.py` | A string literal with letters anywhere in the arguments of `Text`, `BasicText`, `ClickableText`, `showSnackbar`, `Toast.makeText`, or as a named `text`, `contentDescription`, `stateDescription`, `paneTitle`, `onClickLabel` or `onLongClickLabel` = `"…"` argument (TalkBack reads all of them), in Kotlin main sources | quick, full |
| `theme-literals.py` | `Color(<number>…)`, any 8-digit hex literal, named colours, `android.graphics.Color` constants, a string that is only `"#RRGGBB"`, `<number>.dp` / `.sp` / `.em` (`0.dp` passes), `(<number>).dp`, `Dp(<number>)`, `TextUnit(<number>, …)` in Kotlin main sources outside `core-ui/…/coreui/theme/` | quick, full |
| `module-direction.py` | A module depending on `app`; `core-*` depending on a feature module; a feature module depending on `core-ui` | quick, full |
| `code-length.py` | Kotlin files over 600 lines (warns over 400); functions over 100 lines (warns over 60; main sources only) | quick, full |
| `secrets.py` | `*.jks`, `*.keystore`, `keystore.properties`, `secrets.properties`, `local.properties`, `.env*`, private-key PEM blocks, well-known API key and token formats | quick, full |
| `android-regex.py` | An unescaped `}` outside a character class (not closing a `{n}`, `{n,}`, `{n,m}` quantifier or a `\p{..}`) in a pattern given to `Regex(...)`, `Pattern.compile(...)`, `.toRegex()` or `.toPattern()` in Kotlin main sources (literals joined by `+`, string `val`s of the same file used by name or as `$NAME`; reported at the line that holds the bracket): Android's ICU refuses it, the JVM does not. An unescaped `]` there is a warning: ICU accepts it. Not read: `Pattern.matches`, constants of other files, the pattern named after other named arguments, patterns built at run time: those are left to the instrumented `StaticInitialisersTest` (library/README.md). False alarm on the safe side: `[]}]` (write `[\]}]`) | quick, full |
| `coroutine-cancellation.py` | A catch of `Exception`, `Throwable`, `RuntimeException` or `IllegalStateException` (all match `CancellationException`) in coroutine code (a `suspend fun`, or a lambda of `launch`, `async`, `withContext`, `flow` and the like; list in the guard's docstring) with no earlier `catch (e: CancellationException)` in the same `try`, no `throw <caught>` and no `ensureActive()` in its body, in Kotlin main sources. Not recognised: lambdas of functions that take a `suspend` parameter under another name. A catch around code that can never suspend is still reported: add the one-line rethrow | quick, full |
| `translations.py` | A language folder's `res/values-<lang>/strings.xml` (`values-xx`, `values-xx-rYY`, `values-b+…`; never `values-night`, `-v31`, `-land`) that differs from its module's English `res/values/strings.xml`: a missing key, an extra key, a key English marks `translatable="false"`, a `<string>` against a `<plurals>`, other placeholders (`%1$s`, `%2$d`, `%s`; `%%` and `%n` take none), a plural without `other` or with an unknown quantity, a plural item using a placeholder English's `other` lacks, or an `other` whose placeholders differ; text not in Unicode NFC (docs/conventions/translation.md) | quick, full |
| `public-boundary.py` | Used where the list of public paths is present (skipped in this repository): a file classified on neither side of the public source, a list entry that matches no file, and, in the comments of public .kt/.kts/.xml/.pro/.toml/.properties files, dates, references to files that are not public and plan step codes; in public Markdown, references to files that are not public and internal wording | quick, full |
| `manifest-permissions.py` | A `uses-permission` outside INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and RECEIVE_BOOT_COMPLETED (WorkManager's), VIBRATE (haptics) and androidx.core's `com.plumemu.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, in the source manifests and the merged debug manifest (`--require-merged`, as verify.sh runs it, fails when the app was not built); `tools:node="remove"` entries pass | quick, full (after the build) |
| `licences.py` | A GPL, LGPL or AGPL library, or one with an unknown licence, in the app's debug or release runtime classpath. Has Gradle resolve each POM and its parents (`guards/licence-poms.init.gradle.kts`, which also downloads POMs the cache has dropped), then reads their licences; exceptions go in `guards/licence-allowlist.txt` with a reason | full |

`guards/guardlib.py` holds what they share (file listing, the Kotlin comment and string
stripper, the report format).

## Self-tests

`guards/tests/run.sh` proves each guard catches its bad fixture and passes its good one;
`verify.sh` runs it first. Static fixtures are in `guards/tests/fixtures/` (lines that must be
reported end with `// BAD` or `<!-- BAD -->`); fixtures that would break a rule of the repository
itself (non-English letters, key-like strings, very long files) are generated into a temp folder.
A scan of the repository skips `guards/tests/fixtures/`. Changing a guard means changing its
fixtures in the same change.

## SKIP: checks whose input is not here

Some checks need files that are kept out of this source copy: the screenshot baselines, the name
pack sources, the statistics server and a few self-tests. `verify.sh` and `guards/tests/run.sh`
print one `SKIP <check>: <reason>` line for each and carry on. A `SKIP` does not change the exit
code.
