# ES-DE Android system and find rules

- Project: ES-DE Frontend, https://gitlab.com/es-de/emulationstation-de
- Copied: `resources/systems/android/es_systems.xml` and `es_find_rules.xml`, plus the repository `LICENSE`
- Commit: ef267ae693caeee89ca8ca888a38e9eab9bce90a (2026-10-29)
- Fetched: 2026-11-01 file by file from the GitLab raw URLs at that commit
- Licence: MIT, Copyright (c) 2014 Alec Lofquist, 2020-2024 Leon Styhre, 2024-2026 Northwestern Software AB; text in `LICENSE`

Build-time input of `tools/platforms/convert.py` only. Never shipped in the APK and never read on a device.

Only edit to `es_systems.xml`: the two non-ASCII characters (an accented e in a system name) are written as XML character references (`&#233;`), because the repo's English-only guard rejects them. The parsed content is identical.
