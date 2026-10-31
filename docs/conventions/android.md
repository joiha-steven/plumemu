# Android

## Modules

The module list and dependency direction are checked by the module-direction guard. A new
module gets a row in the module list. Each module has `README.md` (what it holds,
file index) and a map file listing where to find things and what to watch for.

## Platform

- Native Android: Kotlin and Jetpack Compose. `minSdk` is 23; it rises to 24 only when a library the app
  cannot do without requires it, and the reason is written down here.
- `minSdk 23`, `targetSdk` newest stable; every API above 23 behind a version check, one Activity, Compose only (no XML layouts, no Fragments).
- Configuration changes and process death must not lose the person's place: focus and strip
  position are restored.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | ScreenScraper, platform data updates, statistics, update check |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | WorkManager's two normal permissions (granted at install, no prompt): box-art downloads keep going with the screen off and start again after a restart. `FOREGROUND_SERVICE` is removed (`tools:node="remove"` in the app manifest): no background work with a notification |
| `REQUEST_INSTALL_PACKAGES` | In-app updates install Plumemu's own update through a `PackageInstaller` session, only after the person chose Update |
| `REQUEST_DELETE_PACKAGES` | Uninstall from Apps: the app menu's Uninstall starts Android's own confirmation (`ACTION_DELETE`), which needs this normal permission from Android 9 |
| `VIBRATE` | Haptics: a light tick on each cursor move and a click on confirm, Settings › Advanced › Vibration (on by default); a normal permission, Android asks nobody |
| `<queries>` for emulator packages and `MAIN`/`LAUNCHER` | Detect emulators; list apps for "Apps" |

No storage permission: ROM folders come from Android's folder picker. Never
`MANAGE_EXTERNAL_STORAGE`. Never `QUERY_ALL_PACKAGES`, never location, contacts, camera or advertising id. A new permission
needs a documented reason in this table.

## Files

Plumemu never deletes, moves or renames game files. The ROM folders belong to the person; the
app only reads them, which also means a wrong guess can never cost anyone a game. A game that is
not wanted is hidden (game menu › Hide) or marked "not a game"; removing the file is done in a file
manager. Plumemu deletes only its own files: its database, settings, box-art cache and the backups
it is writing.

## Manifest

- The launcher Activity declares `HOME` and `DEFAULT` categories so it can become the home screen;
  becoming it is the person's choice (Settings › Home screen).
- `FileProvider` for emulators that take a content URI.

## Libraries

Only libraries on the project's allowed list. The licence guard in
`verify.sh` fails on GPL or AGPL anywhere in the dependency tree.
