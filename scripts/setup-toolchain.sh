#!/usr/bin/env bash
# Re-creates the Plumemu build toolchain on a Mac (Apple silicon, Homebrew):
# JDK 17, the Android SDK packages, the two reference emulators and local.properties.
# Safe to run again: it only adds what is missing and resets the emulator settings it owns.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JDK_HOME="/opt/homebrew/opt/openjdk@17"
# The android-commandlinetools cask keeps its SDK here; other projects on this Mac share it.
SDK_HOME="/opt/homebrew/share/android-commandlinetools"

# Keep in step with compileSdk / targetSdk in gradle/libs.versions.toml.
PLATFORM="platforms;android-37.0"
BUILD_TOOLS="build-tools;37.0.0"
LOWEND_IMAGE="system-images;android-23;google_apis;arm64-v8a"
MODERN_IMAGE="system-images;android-37.0;google_apis;arm64-v8a"

log() { printf '==> %s\n' "$*"; }
fail() { printf 'setup-toolchain: %s\n' "$*" >&2; exit 1; }

command -v brew >/dev/null 2>&1 || fail "Homebrew is required: https://brew.sh"

# 1. Homebrew packages.
if ! brew list --formula openjdk@17 >/dev/null 2>&1; then
  log "Installing openjdk@17"
  brew install openjdk@17
fi
if ! brew list --cask android-commandlinetools >/dev/null 2>&1; then
  log "Installing android-commandlinetools"
  brew install --cask android-commandlinetools
fi
[[ -x "$JDK_HOME/bin/java" ]] || fail "JDK 17 not found at $JDK_HOME"
[[ -d "$SDK_HOME" ]] || fail "Android SDK not found at $SDK_HOME"

export JAVA_HOME="$JDK_HOME"
export ANDROID_HOME="$SDK_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

# 2. Shell profile. No sudo here, so JAVA_HOME is exported instead of linking into /Library/Java.
PROFILE="$HOME/.zprofile"
touch "$PROFILE"
add_profile_line() {
  if ! grep -qxF "$1" "$PROFILE"; then
    log "Adding to $PROFILE: $1"
    printf '%s\n' "$1" >>"$PROFILE"
  fi
}
add_profile_line "export JAVA_HOME=$JDK_HOME"
add_profile_line "export ANDROID_HOME=$SDK_HOME"
add_profile_line 'export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"'

# 3. SDK packages. `yes` is cut off by a broken pipe once the licences are accepted; that is fine.
log "Accepting Android SDK licences"
{ yes 2>/dev/null || true; } | sdkmanager --licenses >/dev/null
log "Installing SDK packages (already installed ones are skipped)"
sdkmanager --install platform-tools emulator "$PLATFORM" "$BUILD_TOOLS" "$LOWEND_IMAGE" "$MODERN_IMAGE" >/dev/null

# 4. Emulators. Both are 1920x1080 at 400 dpi, i.e. 768x432 dp landscape (RULES R9).
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

set_key() { # file key value: replaces the key's line or appends it, leaving other lines alone
  local file="$1" key="$2" value="$3"
  if grep -q "^$key[ ]*=" "$file"; then
    sed -i '' "s|^$key[ ]*=.*|$key=$value|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >>"$file"
  fi
}

make_avd() { # name image ram_mb cores
  local name="$1" image="$2" ram="$3" cores="$4"
  local config="$AVD_HOME/$name.avd/config.ini"
  # The image path in config.ini uses slashes where the package name uses semicolons.
  local sysdir="${image//;//}/"
  if ! avdmanager list avd -c 2>/dev/null | grep -qxF "$name"; then
    log "Creating emulator $name"
    echo no | avdmanager create avd --name "$name" --package "$image" >/dev/null
  elif ! grep -qxF "image.sysdir.1=$sysdir" "$config" 2>/dev/null; then
    # A bumped image constant above must move an existing emulator too, not leave it on the old one.
    log "Re-creating emulator $name on $image"
    echo no | avdmanager create avd --force --name "$name" --package "$image" >/dev/null
  fi
  [[ -f "$config" ]] || fail "emulator $name has no $config"
  set_key "$config" hw.lcd.width 1920
  set_key "$config" hw.lcd.height 1080
  set_key "$config" hw.lcd.density 400
  set_key "$config" hw.initialOrientation landscape
  set_key "$config" hw.ramSize "$ram"
  set_key "$config" hw.cpu.ncore "$cores"
  set_key "$config" hw.keyboard yes
  set_key "$config" hw.gpu.enabled yes
  set_key "$config" hw.gpu.mode auto
  set_key "$config" showDeviceFrame no
}

# Stands in for weak, old handhelds.
make_avd plumemu-api23-lowend "$LOWEND_IMAGE" 2048 4
make_avd plumemu-modern "$MODERN_IMAGE" 4096 4

# 5. local.properties (git-ignored) tells Gradle where the SDK is.
LOCAL_PROPS="$REPO_DIR/local.properties"
if ! grep -qxF "sdk.dir=$SDK_HOME" "$LOCAL_PROPS" 2>/dev/null; then
  log "Setting sdk.dir in $LOCAL_PROPS"
  touch "$LOCAL_PROPS"
  set_key "$LOCAL_PROPS" sdk.dir "$SDK_HOME"
fi

log "Toolchain ready. Open a new terminal (or run: source $PROFILE), then: ./gradlew assembleDebug"
