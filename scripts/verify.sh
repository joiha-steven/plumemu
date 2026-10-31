#!/usr/bin/env bash
# The gate. Prints one PASS/FAIL line per check and exits non-zero if any check fails.
#
#   scripts/verify.sh --quick   what a coding agent runs: guard self-tests, the publish-update
#                               tests, every guard except licences, then
#                               `./gradlew assembleDebug lintDebug`
#   scripts/verify.sh           the full gate, run once by the main session before a merge:
#                               all guards, the statistics server's checks (server/: npm ci,
#                               typecheck, test; skipped without node),
#                               `assembleDebug testDebugUnitTest lintDebug`,
#                               `assembleRelease` (shows R8 and release-only problems early)
#                               and `verifyRoborazziDebug` when screenshot tests and their
#                               baselines exist
#   scripts/verify.sh --ci      the full gate without screenshot comparison (CI on Linux)
#
# Gradle output goes to build/verify/gradle.log; only the failure is echoed.
set -uo pipefail
# Git hooks export GIT_DIR (and friends) pointing at the real repository. Without this,
# the throwaway repositories below would `git init`/`git add` into the real one; that once
# set core.bare=true on the shared .git/config and broke every checkout.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_PREFIX GIT_COMMON_DIR GIT_OBJECT_DIRECTORY

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

MODE=full
ON_CI=0
for arg in "$@"; do
  case "$arg" in
    --quick) MODE=quick ;;
    --ci) ON_CI=1 ;;
    -h | --help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "verify: unknown option $arg (use --quick, --ci or --help)" >&2; exit 2 ;;
  esac
done
if [[ $MODE == quick && $ON_CI == 1 ]]; then
  echo "verify: --ci runs the full gate; do not combine it with --quick" >&2
  exit 2
fi

# JDK 17: keep a working JAVA_HOME (CI sets one), else find the Homebrew or system JDK 17.
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for candidate in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 \
                   "$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "verify: JDK 17 not found; run scripts/setup-toolchain.sh or set JAVA_HOME" >&2
  exit 2
fi

LOG_DIR="$REPO_DIR/build/verify"
mkdir -p "$LOG_DIR"
STARTED=$(date +%s)
FAILED=()

# report NAME RC OUTPUT_FILE: one line for the check, then its details indented.
report() {
  local name="$1" rc="$2" out="$3"
  if [[ $rc == 0 ]]; then
    printf 'PASS  %s\n' "$name"
    # Warnings and summaries (licence counts) are worth seeing even on a pass.
    grep -E ': warning: |^[0-9]+ (libraries|POMs) ' "$out" | sed 's/^/      /'
  else
    printf 'FAIL  %s\n' "$name"
    FAILED+=("$name")
    sed 's/^/      /' "$out"
  fi
}

run_check() { # name command...
  local name="$1"
  shift
  local out="$LOG_DIR/$name.txt"
  "$@" >"$out" 2>&1
  local rc=$?
  if [[ $rc != 0 ]]; then
    # Keep only the guard's own lines (violations and its summary) when it ends with one.
    grep -vE '^(ok    )' "$out" >"$out.tmp" && mv "$out.tmp" "$out"
  fi
  report "$name" "$rc" "$out"
}

# skip_check NAME REASON: a check whose input is not in this checkout (a public clone does not
# have every private file). Detected by the file or folder actually missing, never by a flag.
skip_check() { printf 'SKIP  %s: %s\n' "$1" "$2"; }

GUARDS="scripts/guards"
run_check "guard self-tests" "$GUARDS/tests/run.sh"
if [[ -f scripts/tests/test_publish_update.py ]]; then
  run_check "publish-update tests" python3 -I scripts/tests/test_publish_update.py
else
  skip_check "publish-update tests" "scripts/tests/test_publish_update.py is not in this checkout"
fi
for guard in english-only ui-text theme-literals module-direction code-length secrets android-regex coroutine-cancellation translations public-boundary; do
  if [[ $guard == public-boundary && ! -f scripts/public-paths.txt ]]; then
    skip_check "$guard" "scripts/public-paths.txt is not in this checkout"
    continue
  fi
  run_check "$guard" python3 "$GUARDS/$guard.py"
done
if [[ ! -f tools/names/systems.json ]]; then
  skip_check "dat-folder-names" "tools/names/systems.json (its input) is not in this checkout"
elif [[ ! -f tools/platforms/gen_folder_names.py ]]; then
  skip_check "dat-folder-names" "tools/platforms/gen_folder_names.py is not in this checkout"
else
  run_check "dat-folder-names" python3 -I tools/platforms/gen_folder_names.py --check
fi

# --- Statistics server (server/, a Cloudflare Worker; never in the APK) ----------------------------
# Full gate and CI only: npm ci takes a while and coding agents run these from server/ themselves.
# Without node: SKIP on a local full run, FAIL with --ci.
if [[ $MODE == full ]]; then
  if [[ ! -f server/package.json ]]; then
    skip_check "server npm ci, typecheck, test" "server/ is not in this checkout"
  elif command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
    run_check "server npm ci, typecheck, test" \
      bash -c 'cd server && npm ci --no-audit --no-fund && npm run typecheck && npm test'
  elif [[ $ON_CI == 1 ]]; then
    # CI installs Node (actions/setup-node in ci.yml): a missing node there is a broken gate.
    printf 'FAIL  %s\n' "server npm ci, typecheck, test: node not found"
    FAILED+=("server (node not found)")
  else
    echo "SKIP  server npm ci, typecheck, test: node not found (install Node 22 to check server/)"
  fi
fi

# --- Gradle ---------------------------------------------------------------------------------------
has_roborazzi() { # the screenshot-test plugin is declared somewhere in the build
  local files=()
  while IFS= read -r file; do files+=("$file"); done < <(
    git ls-files --cached --others --exclude-standard -- '*build.gradle.kts' 'gradle/libs.versions.toml' \
      | grep -v '^scripts/')
  [[ ${#files[@]} -gt 0 ]] && grep -qi 'roborazzi' "${files[@]}"
}

# A public clone has no reference images: they stay in the private repository.
has_screenshot_baselines() {
  compgen -G '*/src/test/screenshots/*' >/dev/null
}

if [[ $MODE == quick ]]; then
  TASKS=(assembleDebug lintDebug)
else
  TASKS=(assembleDebug testDebugUnitTest lintDebug assembleRelease)
  if has_roborazzi; then
    if ! has_screenshot_baselines; then
      skip_check "verifyRoborazziDebug" "no screenshot baselines (*/src/test/screenshots/) in this checkout"
    elif [[ $ON_CI == 1 ]]; then
      echo "NOTE  --ci: verifyRoborazziDebug skipped; screenshots are compared by the local full gate"
    else
      TASKS+=(verifyRoborazziDebug)
    fi
  fi
fi

GRADLE_LOG="$LOG_DIR/gradle.log"
GRADLE_STARTED=$(date +%s)
GRADLE_ARGS=()
# Shared CI runners are several times slower than the development machine: absolute time budgets in unit
# tests are skipped there (the ratio checks and every correctness assertion still run). --continue: one CI run
# reports every failing module, not just the first.
[[ $ON_CI == 1 ]] && GRADLE_ARGS+=(-Pplumemu.timingBudgets=false --continue)
./gradlew --console=plain ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} "${TASKS[@]}" >"$GRADLE_LOG" 2>&1
gradle_rc=$?
GRADLE_SECONDS=$(( $(date +%s) - GRADLE_STARTED ))
{
  if [[ $gradle_rc != 0 ]]; then
    # The failed tasks, compiler and lint errors, and Gradle's "What went wrong" block.
    grep -E '^> Task .* FAILED$|^e: |^\S+\.(kt|java|xml):[0-9]+: Error|Lint found [0-9]+ errors?' "$GRADLE_LOG" | head -40
    sed -n '/^\* What went wrong:/,/^\* Try:/p' "$GRADLE_LOG" | grep -v '^\* Try:' | head -40
    echo "full log: ${GRADLE_LOG#"$REPO_DIR"/}"
  fi
} >"$LOG_DIR/gradle-summary.txt"
report "gradle ${TASKS[*]} (${GRADLE_SECONDS}s)" "$gradle_rc" "$LOG_DIR/gradle-summary.txt"

# After the build so the merged manifest (with what libraries add) is current.
run_check "manifest-permissions" python3 "$GUARDS/manifest-permissions.py" --require-merged
if [[ $MODE == full ]]; then
  run_check "licences" python3 "$GUARDS/licences.py"
fi

SECONDS_TAKEN=$(( $(date +%s) - STARTED ))
echo
if [[ ${#FAILED[@]} == 0 ]]; then
  echo "verify ($MODE): PASS in ${SECONDS_TAKEN}s"
  if [[ $MODE == quick ]]; then
    echo "Now run the tests of the module you changed, e.g. ./gradlew :library:testDebugUnitTest"
  fi
  exit 0
fi
echo "verify ($MODE): FAIL in ${SECONDS_TAKEN}s: ${FAILED[*]}"
exit 1
