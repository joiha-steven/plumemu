#!/usr/bin/env python3
"""Manifest permissions (docs/conventions/android.md › Permissions).

Every `uses-permission` must be on the allow list: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and
RECEIVE_BOOT_COMPLETED (WorkManager's, normal permissions: RULES R34), REQUEST_INSTALL_PACKAGES (in-app
updates, owner 2026-10-23), REQUEST_DELETE_PACKAGES (Uninstall from Apps, owner 2026-10-25), VIBRATE (haptics, owner 2026-10-26) and the signature-level self permission androidx.core adds for ContextCompat.registerReceiver
(`com.plumemu[.suffix].DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, see app/CLAUDE.md).
A new permission needs the owner. Checks the source manifests and the merged debug manifest of
the app (which includes what libraries add). A `uses-permission` with `tools:node="remove"`
removes a permission and is not a declaration.

Usage: manifest-permissions.py [--require-merged] [--intermediates DIR] [MANIFEST ...]
  --require-merged  exit 2 when no merged debug manifest is found (verify.sh, after the build)
  --intermediates   where to look for it (default app/build/intermediates)
  MANIFEST ...      check only these files
"""

import argparse
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "manifest-permissions"

ALLOWED = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    # WorkManager's (RULES R34); FOREGROUND_SERVICE stays off the list and is removed in the app manifest
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    # In-app updates install Plumemu's own update through a PackageInstaller session (owner decision,
    # 2026-10-23; docs/modules/updates.md). Android 8+ still asks the person once.
    "android.permission.REQUEST_INSTALL_PACKAGES",
    # Uninstall from Apps (owner, 2026-10-25): ACTION_DELETE needs it from Android 9; Android still asks to confirm.
    "android.permission.REQUEST_DELETE_PACKAGES",
    # Haptics (owner, 2026-10-26): a light tick on each cursor move and a click on confirm (Settings > Advanced > Vibration, on
    # by default). A normal permission: Android asks nobody.
    "android.permission.VIBRATE",
}
ALLOWED_PATTERN = re.compile(r"^com\.plumemu(\.[A-Za-z0-9_]+)*\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$")

NEVER = {
    "android.permission.QUERY_ALL_PACKAGES": "list apps through <queries> instead",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "ROM folders come from the folder picker",
    "android.permission.READ_EXTERNAL_STORAGE": "ROM folders come from the folder picker",
    "android.permission.WRITE_EXTERNAL_STORAGE": "ROM folders come from the folder picker",
}

USES_TAG = re.compile(r"<uses-permission(?:-sdk-\d+)?\b[^>]*>", re.S)
NAME_ATTRIBUTE = re.compile(r"android:name\s*=\s*\"([^\"]+)\"")
REMOVES = re.compile(r"tools:node\s*=\s*\"remove\"")
COMMENT = re.compile(r"<!--.*?-->", re.S)


def merged_debug_manifests(intermediates):
    """AndroidManifest.xml files under a `merged_manifest*` folder of the debug variant.

    Searched rather than one fixed path because AGP renames these folders between versions.
    """
    found = []
    for root, _, names in os.walk(intermediates):
        if "AndroidManifest.xml" not in names:
            continue
        parts = guardlib.norm(os.path.relpath(root, intermediates)).split("/")
        if parts[0].startswith("merged_manifest") and "debug" in parts:
            found.append(os.path.join(root, "AndroidManifest.xml"))
    return sorted(found)


def shown(path):
    absolute = os.path.abspath(path)
    if absolute.startswith(guardlib.REPO_ROOT + os.sep):
        return guardlib.norm(os.path.relpath(absolute, guardlib.REPO_ROOT))
    return guardlib.norm(path)


def main(argv):
    parser = argparse.ArgumentParser(description="Manifest permission guard")
    parser.add_argument("--require-merged", action="store_true")
    parser.add_argument("--intermediates", default=os.path.join(guardlib.REPO_ROOT, "app", "build", "intermediates"))
    parser.add_argument("manifests", nargs="*")
    args = parser.parse_args(argv)

    report = guardlib.Reporter(NAME)
    if args.manifests:
        manifests = [(p, guardlib.norm(p)) for p in args.manifests]
    else:
        sources = [p for p in guardlib.repo_files() if os.path.basename(p) == "AndroidManifest.xml"]
        manifests = [(os.path.join(guardlib.REPO_ROOT, p), p) for p in sources]
        merged = merged_debug_manifests(args.intermediates)
        if args.require_merged and not merged:
            guardlib.die(NAME, f"no merged debug AndroidManifest.xml under {shown(args.intermediates)}; "
                               f"build the app first (./gradlew assembleDebug)")
        manifests += [(p, shown(p)) for p in merged]
    if not manifests:
        guardlib.die(NAME, "no AndroidManifest.xml found")

    for full, rel in manifests:
        # A commented-out permission is not declared; keep line breaks so line numbers hold.
        text = COMMENT.sub(lambda m: "\n" * m.group(0).count("\n"), guardlib.read_text(full))
        for tag in USES_TAG.finditer(text):
            name = NAME_ATTRIBUTE.search(tag.group(0))
            if not name or REMOVES.search(tag.group(0)):
                continue
            permission = name.group(1)
            if permission in ALLOWED or ALLOWED_PATTERN.match(permission):
                continue
            why = NEVER.get(permission, "not on the allow list; a new permission needs a decision before it is added; the list is ALLOWED in scripts/guards/manifest-permissions.py")
            report.fail(rel, guardlib.line_of(text, tag.start()), f"{permission}: {why}")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
