# ES-DE Android system and find rules

- Project: ES-DE Frontend, https://gitlab.com/es-de/emulationstation-de
- Copied: `resources/systems/android/es_systems.xml` and `es_find_rules.xml`, plus the repository `LICENSE`
- Commit: a529a9d38c0af09b6fd7e38d10c8076cb0a21050 (2026-10-10)
- Fetched: 2026-10-21 with a sparse `git clone --depth 1`
- Licence: MIT, Copyright (c) 2014 Alec Lofquist, 2020-2024 Leon Styhre, 2024-2026 Northwestern Software AB; text in `LICENSE`

Build-time input of `tools/platforms/convert.py` only. Never shipped in the APK and never read on a device.

Only edit to `es_systems.xml`: the two non-ASCII characters (an accented e in a system name) are written as XML character references (`&#233;`), because the repo's English-only guard rejects them. The parsed content is identical.
