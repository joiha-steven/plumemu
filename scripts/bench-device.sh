#!/usr/bin/env bash
# Runs one Macrobenchmark class or test on the emulator through a PRIVATE adb server, by hand:
# Gradle's connectedBenchmark tasks start their test engine against the default adb server
# (port 5037) whatever ANDROID_SERIAL says, so they can reach the owner's handhelds.
#
# Usage: scripts/bench-device.sh [--check] <label> <Class[#method]>
#   Needs, set by the caller: ADB_SERVER_SOCKET=tcp:localhost:<port> (or ANDROID_ADB_SERVER_PORT=<port>)
#   with a port other than 5037, and ADB_MDNS_AUTO_CONNECT=0. Refuses unless `adb devices` on that
#   server lists exactly one device and it is an emulator.
#   --check: only those checks. The APKs come from
#   ./gradlew :app:assembleBenchmarkRelease :benchmark:assembleBenchmarkRelease
#   The result is build/bench/<label>.json (see benchmark/README.md).
set -uo pipefail

check_private_adb() {
  local port="${ANDROID_ADB_SERVER_PORT:-}"
  if [[ -z "$port" && "${ADB_SERVER_SOCKET:-}" =~ ^tcp:[^:]+:([0-9]+)$ ]]; then port="${BASH_REMATCH[1]}"; fi
  if [[ -z "$port" ]]; then echo "bench-device: set ADB_SERVER_SOCKET=tcp:localhost:5038 (a private adb server)" >&2; return 1; fi
  if [[ "$port" == 5037 ]]; then echo "bench-device: port 5037 is the default adb server; use a private one" >&2; return 1; fi
  if [[ "${ADB_MDNS_AUTO_CONNECT:-}" != 0 ]]; then echo "bench-device: set ADB_MDNS_AUTO_CONNECT=0" >&2; return 1; fi
  local devices
  devices="$(ANDROID_ADB_SERVER_PORT="$port" adb devices | tail -n +2 | grep -v '^[[:space:]]*$')" || true
  if [[ "$(printf '%s\n' "$devices" | grep -c .)" != 1 ]] || ! printf '%s\n' "$devices" | grep -Eq '^emulator-[0-9]+[[:space:]]+device$'; then
    echo "bench-device: the private adb server must list exactly one emulator, it lists:" >&2
    printf '%s\n' "$devices" >&2
    return 1
  fi
  export ANDROID_ADB_SERVER_PORT="$port"
  ANDROID_SERIAL="$(printf '%s' "$devices" | cut -f1)"
  export ANDROID_SERIAL
}

if [[ "${1:-}" == "--check" ]]; then check_private_adb; exit $?; fi
if [[ $# != 2 ]]; then echo "usage: scripts/bench-device.sh [--check] <label> <Class[#method]>" >&2; exit 2; fi
label="$1"
target="$2"
check_private_adb || exit 1
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/build/bench"
mkdir -p "$out"
media=/storage/emulated/0/Android/media/com.plumemu.benchmark
adb install -r -t "$root/app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk" >/dev/null || exit 1
adb install -r -t "$root/benchmark/build/outputs/apk/benchmarkRelease/benchmark-benchmarkRelease.apk" >/dev/null || exit 1
adb shell rm -rf "$media"
adb shell "am instrument -w -e class com.plumemu.benchmark.$target -e androidx.benchmark.suppressErrors EMULATOR -e androidx.benchmark.enabledRules Macrobenchmark com.plumemu.benchmark/androidx.test.runner.AndroidJUnitRunner" >"$out/$label.log" 2>&1
rc=$?
tail -3 "$out/$label.log"
adb pull "$media" "$out/$label" >/dev/null 2>&1
cp "$out/$label"/*/*benchmarkData.json "$out/$label.json" 2>/dev/null || cp "$out/$label"/*benchmarkData.json "$out/$label.json" 2>/dev/null
echo "bench-device: rc=$rc, results in build/bench/$label.json and the traces in build/bench/$label/"
exit $rc
