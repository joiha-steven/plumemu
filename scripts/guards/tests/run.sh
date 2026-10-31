#!/usr/bin/env bash
# Self-test of the guards: each guard must catch its bad fixture and pass its good one.
# Static fixtures are in fixtures/; the ones that would themselves break a repository rule
# (non-English letters, key-like strings, very long files) are generated into a temp folder.
# Usage: scripts/guards/tests/run.sh [-v]   (-v prints each guard's output)
set -uo pipefail
# Git hooks export GIT_DIR (and friends) pointing at the real repository. Without this,
# the throwaway repositories below would `git init`/`git add` into the real one; that once
# set core.bare=true on the shared .git/config and broke every checkout.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_PREFIX GIT_COMMON_DIR GIT_OBJECT_DIRECTORY

TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARDS="$(cd "$TESTS_DIR/.." && pwd)"
FIXTURES="$TESTS_DIR/fixtures"
VERBOSE=0
[[ "${1:-}" == "-v" ]] && VERBOSE=1

TMP="$(mktemp -d "${TMPDIR:-/tmp}/plumemu-guard-tests.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

checks=0
failures=0
OUT="$TMP/out.txt"

ok() { checks=$((checks + 1)); printf 'ok    %s\n' "$1"; }
bad() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL  %s\n' "$1"; sed 's/^/        /' "$OUT"; }
# skip LABEL REASON: a check whose input is absent (a private file missing from a public clone).
skip() { printf 'SKIP  %s: %s\n' "$1" "$2"; }
show() { [[ $VERBOSE == 1 ]] && sed 's/^/        /' "$OUT"; return 0; }

# run DIR GUARD ARGS...: runs a guard from inside DIR, output in $OUT, sets $rc.
run() {
  local dir="$1" guard="$2"
  shift 2
  (cd "$dir" && python3 "$GUARDS/$guard.py" "$@") >"$OUT" 2>&1
  rc=$?
  show
}

violations() { grep -Eo '\(([0-9]+) violation' "$OUT" | grep -Eo '[0-9]+' || echo 0; }
warnings() { grep -Eo '([0-9]+) warning\(s\)' "$OUT" | grep -Eo '^[0-9]+' || echo 0; }

expect_pass() { # label dir guard args...
  local label="$1"
  shift
  run "$@"
  if [[ $rc == 0 ]]; then ok "$label"; else bad "$label (expected pass, exit $rc)"; fi
}

expect_count() { # label count dir guard args...: fails with exactly `count` violations
  local label="$1" want="$2"
  shift 2
  run "$@"
  local got
  got="$(violations)"
  if [[ $rc == 1 && "$got" == "$want" ]]; then ok "$label ($got violations)"
  else bad "$label (expected exit 1 with $want violations, got exit $rc with $got)"; fi
}

# expect_marked label dir guard args...: the reported path:line set equals the lines marked
# `// BAD` or `<!-- BAD -->` in the fixture folder.
expect_marked() {
  local label="$1" dir="$2"
  shift 2
  run "$dir" "$@"
  local marked reported
  marked="$(cd "$dir" && grep -rnE '// BAD$|<!-- BAD -->' . | cut -d: -f1,2 | sed 's|^\./||' | sort -u)"
  reported="$(grep -E '^[^ ]+:[0-9]+: ' "$OUT" | grep -v ': warning: ' | cut -d: -f1,2 | sed 's|^\./||' | sort -u)"
  if [[ $rc == 1 && -n "$marked" && "$marked" == "$reported" ]]; then
    ok "$label ($(wc -l <<<"$marked" | tr -d ' ') marked lines)"
  else
    bad "$label (exit $rc; marked vs reported differ)"
    diff <(echo "$marked") <(echo "$reported") | sed 's/^/        /'
  fi
}

# --- Generated fixtures -------------------------------------------------------------------------
python3 - "$TMP" <<'PY'
import os, sys
tmp = sys.argv[1]

def write(rel, text):
    path = os.path.join(tmp, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)

# english-only: Vietnamese (precomposed and combining), CJK, Cyrillic outside translation folders.
write("english/bad/docs/notes.md",
      "Plain English line.\n"
      "Vi\u1ec7t Nam\n"
      "\u6e38\u620f\n"
      "\u0418\u0433\u0440\u044b\n"
      "e\u0301 combining accent\n")
write("english/bad/app/src/main/res/values/strings.xml",
      "<resources><string name=\"x\">Tr\u00f2 ch\u01a1i</string></resources>\n")
write("english/good/translations/es/strings.xml",
      "<resources><string name=\"x\">Espa\u00f1ol</string></resources>\n")
write("english/good/docs/notes.md",
      "Settings \u203a About \u00b7 768\u00d7432 \u2014 done \u2192 next \u2265 1 \u2026 "
      "\u201cquoted\u201d \u2713 \u2500\u252c\u2500 Daiji" "sh\u014d\n")
write("english/bad/docs/design/canvas/board.dc.html", "<p>Danh s\u00e1ch</p>\n")
write("english/good/docs/design/canvas/board.dc.html", "<p>Settings \u203a About</p>\n")
write("english/good/app/src/main/res/values-vi/strings.xml",
      "<resources><string name=\"x\">Tr\u00f2 ch\u01a1i</string></resources>\n")

# secrets: forbidden file names and key-like contents, assembled so this script holds none.
write("secrets/bad/local.properties", "sdk.dir=/somewhere\n")
write("secrets/bad/keystore.properties", "storeFile=release.jks\n")
write("secrets/bad/.env.local", "\n")
with open(os.path.join(tmp, "secrets/bad/release.jks"), "wb") as handle:
    handle.write(b"\0\1binary")
write("secrets/bad/key.pem", "-----BEGIN " + "RSA PRIVATE KEY-----\nabc\n")
write("secrets/bad/config.txt",
      "aws = " + "AKIA" + "ABCDEFGHIJKLMNOP" + "\n"
      "github = " + "ghp_" + "a1" * 18 + "\n"
      "screenscraper_password = " + "Zx9" * 9 + "\n")
write("secrets/good/notes.md",
      "The password field label comes from strings.xml.\n"
      "token = stringResource(R.string.token_hint)\n"
      "apiKey = BuildConfig.SCREENSCRAPER_KEY\n")
write("secrets/good/local.properties.example", "sdk.dir=/path/to/sdk\n")

# code-length
def function(name, body_lines):
    return f"fun {name}() {{\n" + "".join(f"    println({i})\n" for i in range(body_lines)) + "}\n"

write("length/bad/src/main/java/Long.kt", "".join(f"val v{i} = {i}\n" for i in range(610)))
write("length/bad/src/main/java/Fun.kt",
      function("tooLong", 103) + function("rather", 63) + function("short", 3))
write("length/good/src/main/java/Medium.kt",
      "interface Source {\n    fun count(): Int\n}\n" + function("small", 10)
      + "".join(f"val v{i} = {i}\n" for i in range(440)))
write("length/good/src/test/java/LongTest.kt", function("tableOfCases", 120))
PY

# public-boundary: a repository with a list and files; the guard reads the list from the folder.
for name in good unclassified dead order; do
  mkdir -p "$TMP/boundary/$name/scripts" "$TMP/boundary/$name/docs" "$TMP/boundary/$name/app"
  printf 'x\n' >"$TMP/boundary/$name/docs/RULES.md"
  printf 'x\n' >"$TMP/boundary/$name/docs/SPEC.md"
  printf 'x\n' >"$TMP/boundary/$name/app/Main.kt"
  printf 'x\n' >"$TMP/boundary/$name/scripts/public-paths.txt"
done
# Exception after the broad entry: RULES.md is private, SPEC.md public. The list itself is private.
printf '# comment\npublic /app/\npublic /docs/\nprivate /docs/RULES.md  # exception\nprivate /scripts/\n' \
  >"$TMP/boundary/good/scripts/public-paths.txt"
printf 'public /app/\nprivate /docs/\nprivate /scripts/\n' >"$TMP/boundary/unclassified/scripts/public-paths.txt"
printf 'public /app/\npublic /docs/\nprivate /scripts/\nprivate /server/\n' >"$TMP/boundary/dead/scripts/public-paths.txt"
# Exception BEFORE the broad entry: the later broad entry overrides it, so the exception is dead.
printf 'public /app/\nprivate /docs/RULES.md\npublic /docs/\nprivate /scripts/\n' >"$TMP/boundary/order/scripts/public-paths.txt"

# --- Cases ---------------------------------------------------------------------------------------
F="$FIXTURES"
expect_count  "english-only catches non-English letters, design canvas included" 6 "$TMP/english/bad" english-only .
expect_pass   "english-only passes punctuation, allowed names, values-vi, translations/es" "$TMP/english/good" english-only .

expect_marked "ui-text catches literal UI text" "$F/ui-text/bad" ui-text .
expect_pass   "ui-text passes resources, logs, comments, tests" "$F/ui-text/good" ui-text .

expect_marked "theme-literals catches colour and size literals" "$F/theme-literals/bad" theme-literals .
expect_pass   "theme-literals passes tokens, theme package, tests" "$F/theme-literals/good" theme-literals .

expect_count  "licences catches GPL, LGPL, AGPL, unknown, missing" 6 "$F/licences" licences \
  --deps-file bad-deps.txt --cache cache --allowlist allowlist.txt
expect_pass   "licences passes Apache, MIT via parent, allowlisted" "$F/licences" licences \
  --deps-file good-deps.txt --cache cache --allowlist allowlist.txt
run "$F/licences" licences --deps-file no-release-deps.txt --cache cache --allowlist allowlist.txt
if [[ $rc == 2 ]]; then ok "licences refuses Gradle output without the release classpath"
else bad "licences refuses Gradle output without the release classpath (exit $rc, want 2)"; fi

run "$F/licences" licences --deps-file empty-tree-deps.txt --cache cache --allowlist allowlist.txt
if [[ $rc == 2 ]]; then ok "licences refuses Gradle output with no readable libraries"
else bad "licences refuses Gradle output with no readable libraries (exit $rc, want 2)"; fi

expect_marked "module-direction catches wrong-way dependencies" "$F/module-direction/bad" module-direction .
expect_pass   "module-direction passes app -> features -> core" "$F/module-direction/good" module-direction .

expect_count  "translations catches missing, extra and untranslatable keys, placeholder and plural mismatches, decomposed text, also in a values-b+ folder" 13 \
  "$F/translations/bad" translations .
expect_pass   "translations passes reordered keys, swapped placeholders, other-only and extra quantities, and skips values-night, -v31 and -land" \
  "$F/translations/good" translations .

expect_marked "coroutine-cancellation catches a broad catch in suspend code that lets CancellationException in" "$F/coroutine-cancellation/bad" coroutine-cancellation .
expect_pass   "coroutine-cancellation passes rethrow, earlier catch, ensureActive, specific types, plain functions, tests" "$F/coroutine-cancellation/good" coroutine-cancellation .

expect_marked "android-regex catches an unescaped } outside a class" "$F/android-regex/bad" android-regex .
if [[ "$(warnings)" == 6 ]]; then ok "android-regex warns on an unescaped ] outside a class"
else bad "android-regex warns on an unescaped ] outside a class (got $(warnings) warnings, want 6)"; fi
expect_pass   "android-regex passes escapes, classes, quantifiers, \\p{..}, tests" "$F/android-regex/good" android-regex .
if [[ "$(warnings)" == 0 ]]; then ok "android-regex has no warning on the good fixture"
else bad "android-regex has no warning on the good fixture (got $(warnings))"; fi

expect_count  "code-length catches a 610-line file and a 105-line function" 2 "$TMP/length/bad" code-length .
if [[ "$(warnings)" == 1 ]]; then ok "code-length warns on a 65-line function"
else bad "code-length warns on a 65-line function (got $(warnings) warnings)"; fi
expect_pass   "code-length passes a 450-line file and long test functions" "$TMP/length/good" code-length .
if [[ "$(warnings)" == 1 ]]; then ok "code-length warns on a 450-line file"
else bad "code-length warns on a 450-line file (got $(warnings) warnings)"; fi

expect_count  "secrets catches key files and key-like strings" 8 "$TMP/secrets/bad" secrets .
expect_pass   "secrets passes ordinary settings text" "$TMP/secrets/good" secrets .

expect_marked "manifest-permissions catches forbidden permissions" "$F/manifest-permissions/bad" \
  manifest-permissions AndroidManifest.xml
expect_pass   "manifest-permissions passes the allow list" "$F/manifest-permissions/good" \
  manifest-permissions AndroidManifest.xml

mkdir -p "$TMP/no-build"
run "$TMP" manifest-permissions --require-merged --intermediates "$TMP/no-build"
if [[ $rc == 2 ]]; then ok "manifest-permissions --require-merged refuses a missing merged manifest"
else bad "manifest-permissions --require-merged refuses a missing merged manifest (exit $rc, want 2)"; fi
expect_count  "manifest-permissions catches a permission only the merged manifest has" 1 "$TMP" \
  manifest-permissions --require-merged --intermediates "$F/manifest-permissions/merged-bad"
expect_pass   "manifest-permissions passes a clean merged manifest" "$TMP" \
  manifest-permissions --require-merged --intermediates "$F/manifest-permissions/merged-good"

expect_pass   "public-boundary passes a fully classified tree" "$TMP/boundary/good" public-boundary .
printf 'public /ap/\nprivate /docs/\nprivate /scripts/\n' >"$TMP/boundary/unclassified/scripts/public-paths.txt"
expect_count  "public-boundary catches an unclassified file and the entry that matches nothing" 2 "$TMP/boundary/unclassified" public-boundary .
expect_count  "public-boundary catches a dead entry" 1 "$TMP/boundary/dead" public-boundary .
run "$TMP/boundary/dead" public-boundary scripts/public-paths.txt app/Main.kt
if [[ $rc == 0 ]]; then ok "public-boundary skips the dead-entry check when given only some files"
else bad "public-boundary skips the dead-entry check when given only some files (exit $rc)"; fi
run "$TMP/boundary/good" public-boundary --sides .
if grep -qx 'private docs/RULES.md' "$OUT" && grep -qx 'public docs/SPEC.md' "$OUT" && grep -qx 'public app/Main.kt' "$OUT"; then
  ok "public-boundary: the last matching entry wins (exception after the broad entry)"
else bad "public-boundary: the last matching entry wins (exception after the broad entry)"; fi
expect_count  "public-boundary fails an exception placed before the broad entry that overrides it" 1 "$TMP/boundary/order" public-boundary .

# public-boundary content half (always on): comments of public files only. Every bad line carries exactly one hit.
CB="$TMP/content"
mkdir -p "$CB/bad/app" "$CB/good/app" "$CB/bad/docs" "$CB/bad/tools/names" "$CB/good/docs" "$CB/good/tools/names" "$CB/bad/scripts" "$CB/good/scripts"
for name in bad good; do
  printf 'public /app/\nprivate /docs/\nprivate /tools/\nprivate /CLAUDE.md\nprivate /README.md\nprivate /scripts/\n' >"$CB/$name/scripts/public-paths.txt"
  printf 'x\n' >"$CB/$name/CLAUDE.md"; printf 'x\n' >"$CB/$name/README.md"
  printf 'x\n' >"$CB/$name/docs/RULES.md"; printf 'x\n' >"$CB/$name/tools/names/build.py"
  printf 'x\n' >"$CB/$name/app/README.md"
done
cat >"$CB/bad/app/Bad.kt" <<'EOF'
// 2026-10-23 a date
/* Seen 2026-10 */ val a = 1
// the owner wanted it
/** KDoc, RULES R10 says so */
// (R30) hidden citation
// R4/R8 both cited
// see docs/RULES.md for why
// see CLAUDE.md
// tools/names builds the pack
// asked Claude to write it
// the viet-code helper
/* nested /* inner */ 2026-10-24 still a comment */
val s = "ok" // 2026-01-02 after code
/*
 * multi line
 * owner decision
 */
// the next agent can read this
// per rules R10 only
// step I3b added this
// E1b step 2d: the run
// the keyboard (S5c)
// see ROADMAP for the order
// the plan's rule
// (no board of its own)
EOF
printf '<resources>\n    <!-- 2026-10-23 -->\n    <string name="a">x</string>\n    <!--\n      the owner chose this\n    -->\n</resources>\n' >"$CB/bad/app/bad.xml"
printf '# R4 rule from RULES\n-keep class a.B\n# owner 2026-10-23\n' >"$CB/bad/app/bad.pro"
run "$CB/bad" public-boundary .
want="app/Bad.kt:1 app/Bad.kt:2 app/Bad.kt:3 app/Bad.kt:4 app/Bad.kt:5 app/Bad.kt:6 app/Bad.kt:7 app/Bad.kt:8 app/Bad.kt:9 app/Bad.kt:10 app/Bad.kt:11 app/Bad.kt:12 app/Bad.kt:13 app/Bad.kt:16 app/Bad.kt:18 app/Bad.kt:19 app/Bad.kt:20 app/Bad.kt:21 app/Bad.kt:22 app/Bad.kt:23 app/Bad.kt:24 app/Bad.kt:25 app/bad.pro:1 app/bad.pro:3 app/bad.xml:2 app/bad.xml:5"
got="$(grep -E '^[^ ]+:[0-9]+: ' "$OUT" | cut -d: -f1,2 | sort -u -t: -k1,1 -k2,2n | tr '\n' ' ' | sed 's/ $//')"
if [[ $rc == 1 && "$got" == "$want" ]]; then ok "public-boundary catches date, owner, RULES, link and agent in .kt, .xml, .pro comments (26 lines)"
else bad "public-boundary catches every pattern (exit $rc)"; diff <(tr ' ' '\n' <<<"$want") <(tr ' ' '\n' <<<"$got") | sed 's/^/        /'; fi
for pat in date owner rule link agent step; do
  if grep -qE "^[^ ]+:[0-9]+: $pat: " "$OUT"; then ok "public-boundary names the pattern '$pat' in its reason"
  else bad "public-boundary names the pattern '$pat' in its reason"; fi
done
run "$CB/bad" public-boundary --summary .
if grep -qE '^app +[0-9]+ +[0-9]+ +[0-9]+ +[0-9]+ +[0-9]+ +[0-9]+ +28$' "$OUT" && grep -qE '^ALL ' "$OUT"; then ok "public-boundary --summary counts hits per module and pattern"
else bad "public-boundary --summary counts hits per module and pattern"; fi
expect_count  "public-boundary with a path scans only that file" 23 "$CB/bad" public-boundary app/Bad.kt
expect_count  "public-boundary still accepts the old --content flag" 23 "$CB/bad" public-boundary --content app/Bad.kt
cat >"$CB/good/app/Good.kt" <<'PYEOF2'
package demo

import androidx.lifecycle.LifecycleOwner

// L1 / R1 and L1/R1 change the size; R2 trigger and R3 click stay free of the check.
// R.string.name and R.drawable.icon are resources, not rules.
// Step 2 of the walk and step 1 of the header are plain sequences; A4 paper and 0xE1b pass.
/** The LifecycleOwner and the lifecycle owner are Android's; so is the owner of this scope. */
// The ViewModelStoreOwner and the view model store owner hold it; ownership moves on.
// Sent with a user agent of its own; the User-Agent header is set once.
// R8 shrinks this; a Cloudflare R2 bucket holds the pack. Version 0.1.0 of build 12.
// A bare README.md is in many folders, and app/README.md is public.
// https://example.com/docs/plans/x.md is a link to the web, not to a private file.
/* a nested /* inner */ comment, still only words */
val dated = "// 2026-10-23 the owner, RULES R10, Claude, docs/RULES.md"
val url = "https://example.com/docs/RULES.md" // plain words
val raw = """
    // owner 2026-10-23 in a raw string, not a comment
    /* RULES R10 */
"""
val quote = '"' // a quote char does not open a string
val slash = '/' // nor does a slash
PYEOF2
printf '<resources>\n    <!-- Plain words about the string. -->\n    <string name="a">2026-10-23 the owner</string>\n</resources>\n' >"$CB/good/app/good.xml"
printf '# R8 keeps this class\n-keep class a.B\n' >"$CB/good/app/good.pro"
expect_pass   "public-boundary passes gamepad R1-R3, LifecycleOwner, user agent, plain 'step 2', string literals, URLs, nested comments" "$CB/good" public-boundary .
printf '// the owner 2026-10-23\n' >"$CB/good/docs/Note.kt"
expect_pass   "public-boundary skips the comments of private files" "$CB/good" public-boundary .

# public-boundary, Markdown and settings files: a public .md is checked line by line (owner, RULES, R-numbers,
# links to private files; dates are fine), `#` comments of .toml/.properties fully, a replaced file by its replacement.
MB="$TMP/markdown"
for name in bad good; do
  mkdir -p "$MB/$name/app" "$MB/$name/docs/plans" "$MB/$name/scripts/public"
  printf 'public /app/\nprivate /docs/\npublic /docs/README.md\npublic /docs/FOUNDATIONS.md\nprivate /scripts/\nprivate /CLAUDE.md\n' >"$MB/$name/scripts/public-paths.txt"
  printf 'x\n' >"$MB/$name/docs/RULES.md"; printf 'x\n' >"$MB/$name/docs/plans/p.md"; printf 'x\n' >"$MB/$name/docs/FOUNDATIONS.md"
  printf 'x\n' >"$MB/$name/CLAUDE.md"; printf 'x\n' >"$MB/$name/app/Other.md"
  printf 'x\n' >"$MB/$name/docs/README.md"
done
cat >"$MB/bad/app/Notes.md" <<'EOF'
# Notes
This follows RULES R45 to the letter.
It is settled in R37 and cannot move.
The owner approved it on 2026-10-23.
Owners decide the order.
See [the plan](../docs/plans/p.md) for the order.
Details live in docs/RULES.md and nowhere else.
Read [CLAUDE.md](../CLAUDE.md) first.
EOF
printf '# Replacement\nThe owner chose this.\n' >"$MB/bad/scripts/public/docs-README.md"
printf '# Libraries\n# Allowed by RULES R26 only\n# the owner approved this\nkey = "v"\n' >"$MB/bad/app/libs.toml"
printf '# Heap for the owner machine\norg.gradle.jvmargs=-Xmx4g\n' >"$MB/bad/app/gradle.properties"
run "$MB/bad" public-boundary .
want="app/Notes.md:2 app/Notes.md:3 app/Notes.md:4 app/Notes.md:5 app/Notes.md:6 app/Notes.md:7 app/Notes.md:8 app/gradle.properties:1 app/libs.toml:2 app/libs.toml:3 docs/README.md:2"
got="$(grep -E '^[^ ]+:[0-9]+: ' "$OUT" | cut -d: -f1,2 | sort -u -t: -k1,1 -k2,2n | tr '\n' ' ' | sed 's/ $//')"
if [[ $rc == 1 && "$got" == "$want" ]]; then ok "public-boundary catches owner, RULES, R-numbers and private links in public .md, .toml and .properties (11 lines)"
else bad "public-boundary catches Markdown and settings hits (exit $rc)"; diff <(tr ' ' '\n' <<<"$want") <(tr ' ' '\n' <<<"$got") | sed 's/^/        /'; fi
if grep -q '^app/Notes.md:4: owner' "$OUT" && ! grep -q 'date:' "$OUT"; then ok "public-boundary allows a date in Markdown but flags 'owner' on the same line"
else bad "public-boundary allows a date in Markdown but flags 'owner' on the same line"; fi
cat >"$MB/good/app/Notes.md" <<'EOF'
# Notes
Dated 2026-10-23 and 2026-10, which is fine in Markdown.
The `ownerOf` function and the `owner` field are code; so are the LifecycleOwner and "ownership".
The R1 and R2 buttons, `R.string.name` and Android's R8 shrinker are not rules.
See [the other page](Other.md) and [FOUNDATIONS](../docs/FOUNDATIONS.md), or https://example.com/docs/RULES.md on the web.
EOF
printf '# Replacement of the docs README\nSee [FOUNDATIONS](FOUNDATIONS.md).\n' >"$MB/good/scripts/public/docs-README.md"
printf '# Libraries used here\nkey = "v"\n' >"$MB/good/app/libs.toml"
printf 'x\n' >"$MB/good/docs/plans/q.md"
expect_pass   "public-boundary passes clean public Markdown, dates, inline-code identifiers, public links and a clean replacement" "$MB/good" public-boundary .
printf 'The owner chose this.\n' >"$MB/good/docs/plans/q.md"
printf 'The owner chose this and RULES R9 says so.\n' >"$MB/good/docs/RULES.md"
expect_pass   "public-boundary skips the Markdown of private files (and checks the replacement, not the original of a replaced file)" "$MB/good" public-boundary .

if [[ ! -f "$GUARDS/../comments-only.py" ]]; then
  skip "comments-only tests" "scripts/comments-only.py is not in this checkout"
else
# comments-only.py: a throwaway repository with one commit, then edits.
CO="$TMP/co"
mkdir -p "$CO/app"
(
  cd "$CO" && git init -q &&
  git config user.email t@example.com && git config user.name t &&
  printf 'package a\n\n/**\n * Does a thing\n * in two lines.\n */\nfun f() = "a // b"\n\n// old\nval x = 1\n' >app/K.kt &&
  printf '<resources>\n    <!-- old -->\n    <string name="a">x  y</string>\n</resources>\n' >app/s.xml &&
  printf '# old\n-keep class a.B\n' >app/r.pro &&
  printf 'notes\n' >app/N.md &&
  git add -A && git commit -q -m base
)
co() { (cd "$CO" && python3 "$GUARDS/../comments-only.py" "$@") >"$OUT" 2>&1; rc=$?; show; }
co_expect() { # label want-exit grep-pattern args...
  local label="$1" want="$2" pat="$3"; shift 3
  co "$@"
  if [[ $rc == $want ]] && grep -qE "$pat" "$OUT"; then ok "$label"; else bad "$label (exit $rc, want $want)"; fi
}
co_expect "comments-only passes with no change" 0 'comments-only: PASS'
printf 'package a\n\n/**\n * Does a thing, reflowed\n * over three\n * lines.\n */\nfun f() = "a // b"\n\n// new wording\n// and a second line\nval x = 1 // trailing\n' >"$CO/app/K.kt"
printf '<resources>\n    <!-- new\n         wording -->\n    <string name="a">x  y</string> <!-- tail -->\n</resources>\n' >"$CO/app/s.xml"
printf '# other words\n-keep class a.B  # tail\n' >"$CO/app/r.pro"
printf 'changed notes\n' >"$CO/app/N.md"
co_expect "comments-only passes edited and reflowed comments in .kt, .xml, .pro; skips .md" 0 'N.md: not code, skipped'
if grep -q 'K.kt: PASS' "$OUT" && grep -q 's.xml: PASS' "$OUT" && grep -q 'r.pro: PASS' "$OUT"; then ok "comments-only reports PASS per file"
else bad "comments-only reports PASS per file"; fi
printf 'package a\n\n/** Does a thing. */\nfun f() = "a // b"\n\nval x = 2\n' >"$CO/app/K.kt"
co_expect "comments-only fails a code edit and names the line" 1 'K.kt: FAIL \(was line 10: .val x = 1.; now line 6: .val x = 2.\)' app/K.kt
printf 'package a\n\nfun f() = "a // c"\n\nval x = 1\n' >"$CO/app/K.kt"
co_expect "comments-only fails a changed '//' inside a string literal" 1 'K.kt: FAIL' app/K.kt
printf 'package a\n\nfun f() = "a // b"\n\nval x = 1\n' >"$CO/app/K.kt"
co_expect "comments-only passes a Kotlin file whose comments were all removed" 0 'K.kt: PASS' app/K.kt
printf '<resources>\n    <!-- old -->\n    <string name="a">x y</string>\n</resources>\n' >"$CO/app/s.xml"
co_expect "comments-only fails changed text outside XML comments (strings.xml)" 1 's.xml: FAIL' app/s.xml
git -C "$CO" checkout -q app/s.xml
rm "$CO/app/r.pro"
co_expect "comments-only fails a deleted file" 1 'r.pro: FAIL \(deleted' app/r.pro
printf 'package a\n' >"$CO/app/New.kt"; git -C "$CO" add app/New.kt
co_expect "comments-only fails an added file" 1 'New.kt: FAIL \(added' app/New.kt
fi

HOOK="$GUARDS/../hooks/pre-commit"
if [[ ! -f "$HOOK" ]]; then
  skip "pre-commit hook tests" "scripts/hooks/pre-commit is not in this checkout"
else
  # pre-commit checks what is staged: untracked or unstaged non-English text passes, staged fails.
  REPO="$TMP/repo"
  mkdir -p "$REPO/scripts" "$REPO/app/src/main/java"
  cp -R "$GUARDS" "$REPO/scripts/guards"
  (
    cd "$REPO" && git init -q &&
    printf 'include(":app")\n' >settings.gradle.kts &&
    printf 'package a\n\nfun ok() = 1\n' >app/src/main/java/Ok.kt &&
    printf 'public /settings.gradle.kts\npublic /app/\nprivate /scripts/public-paths.txt\n' >scripts/public-paths.txt &&
    git add settings.gradle.kts app/src/main/java/Ok.kt scripts/public-paths.txt &&
    printf 'package a\n// Tr\303\262 ch\306\241i\n' >app/src/main/java/Scratch.kt &&
    printf '// Tr\303\262 ch\306\241i\n' >>app/src/main/java/Ok.kt
  )
  (cd "$REPO" && "$HOOK") >"$OUT" 2>&1
  rc=$?
  show
  if [[ $rc == 0 ]]; then ok "pre-commit ignores untracked files and unstaged edits"
  else bad "pre-commit ignores untracked files and unstaged edits (exit $rc)"; fi
  (cd "$REPO" && git add app/src/main/java/Scratch.kt && "$HOOK") >"$OUT" 2>&1
  rc=$?
  show
  if [[ $rc == 1 ]] && grep -q '^app/src/main/java/Scratch.kt:2: ' "$OUT"; then ok "pre-commit refuses staged non-English text"
  else bad "pre-commit refuses staged non-English text (exit $rc)"; fi
fi

if [[ ! -f "$GUARDS/../session-start.sh" ]]; then
  skip "session-start test" "scripts/session-start.sh is not in this checkout"
else
  # session-start outside a git checkout says so instead of printing an empty branch.
  mkdir -p "$TMP/nogit/scripts" "$TMP/nogit/docs"
  cp "$GUARDS/../session-start.sh" "$TMP/nogit/scripts/"
  printf '# Roadmap\n\n## Now\n\nStep one.\n\n## Next\n' >"$TMP/nogit/docs/ROADMAP.md"
  "$TMP/nogit/scripts/session-start.sh" >"$OUT" 2>&1
  rc=$?
  show
  if [[ $rc == 0 ]] && grep -q '^Not a git checkout' "$OUT" && grep -q '^Step one' "$OUT" && ! grep -q '^Branch' "$OUT"; then
    ok "session-start outside a git checkout"
  else bad "session-start outside a git checkout (exit $rc)"; fi
fi

# publish-source: the copy into the public repository (a private script; absent from the copy).
if [[ ! -f "$GUARDS/../tests/test_publish_source.py" ]]; then
  skip "publish-source tests" "scripts/tests/test_publish_source.py is not in this checkout"
else
  python3 -I "$GUARDS/../tests/test_publish_source.py" >"$OUT" 2>&1
  rc=$?
  show
  if [[ $rc == 0 ]]; then ok "publish-source tests ($(grep -Eo '^Ran [0-9]+' "$OUT" | grep -Eo '[0-9]+') tests)"
  else bad "publish-source tests (exit $rc)"; fi
fi

if [[ $failures == 0 ]]; then
  echo "guard self-tests: PASS ($checks checks)"
  exit 0
fi
echo "guard self-tests: FAIL ($failures of $checks checks)"
exit 1
