#!/usr/bin/env bash
# Builds a fake emulator APK that Android sees as PACKAGE with the activity ACTIVITY (an alias of
# FakeEmulatorActivity), signed with the local debug key. Used only by RealLaunchTest; never shipped.
#
#   launcher/src/androidTest/fake-emulator/build.sh PACKAGE ACTIVITY OUT.apk [REPORT_PACKAGE [HOLD_MS]]
#
# REPORT_PACKAGE: which package receives the report broadcast (default com.plumemu.launcher.test,
# RealLaunchTest; the app's end-to-end test passes com.plumemu). HOLD_MS: how long the activity
# stays open before finishing (default 0).
#
# Needs ANDROID_HOME (or local.properties sdk.dir), JDK 17, build-tools 37 and platform android-37.0.
set -euo pipefail

if [[ $# -lt 3 || $# -gt 5 ]]; then
  echo "usage: build.sh PACKAGE ACTIVITY OUT.apk [REPORT_PACKAGE [HOLD_MS]]" >&2
  exit 2
fi
PACKAGE="$1"
ACTIVITY="$2"
OUT="$3"
REPORT_PACKAGE="${4:-com.plumemu.launcher.test}"
HOLD_MS="${5:-0}"
if [[ ! $HOLD_MS =~ ^[0-9]+$ ]]; then
  echo "build.sh: HOLD_MS must be a whole number of milliseconds" >&2
  exit 2
fi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$REPO/local.properties")}"
TOOLS="$SDK/build-tools/37.0.0"
ANDROID_JAR="$SDK/platforms/android-37.0/android.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

sed -e "s/PACKAGE_NAME/$PACKAGE/" -e "s/ACTIVITY_NAME/$ACTIVITY/" -e "s/REPORT_PACKAGE_VALUE/$REPORT_PACKAGE/" \
  -e "s/HOLD_MS_VALUE/$HOLD_MS/" "$HERE/AndroidManifest.template.xml" >"$WORK/AndroidManifest.xml"
mkdir -p "$WORK/classes" "$WORK/dex"
javac -source 8 -target 8 -nowarn -Xlint:-options -cp "$ANDROID_JAR" -d "$WORK/classes" "$HERE/FakeEmulatorActivity.java"
"$TOOLS/d8" --min-api 23 --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/classes" -name '*.class')
"$TOOLS/aapt2" link -o "$WORK/unsigned.apk" --manifest "$WORK/AndroidManifest.xml" -I "$ANDROID_JAR" \
  --min-sdk-version 23 --target-sdk-version 37
(cd "$WORK/dex" && zip -q "$WORK/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
# The well-known password of Android's local debug keystore, not a secret.
"$TOOLS/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$WORK/aligned.apk"
echo "built $OUT ($PACKAGE / $ACTIVITY, reports to $REPORT_PACKAGE, holds $HOLD_MS ms)"
