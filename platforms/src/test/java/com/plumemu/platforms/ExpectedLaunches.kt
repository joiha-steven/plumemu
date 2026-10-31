package com.plumemu.platforms

// What every emulator of the twelve tested systems must be launched with.
//
// How this file was made: generated once by a throwaway script that read the raw source files
// (tools/platforms/sources/esde and daijisho) directly, not platforms.json and not convert.py,
// then committed as plain text. The package, activity, action, data and extras are therefore
// independent of the converter. The ORDER of the entries follows the converter's rule (ES-DE
// order, Daijisho additions after, standalone emulators before RetroArch cores, one emulator moved
// first) and is NOT independent; the first
// emulator of each system is checked against a hand-written list in OrderTest.kt. A "//" line
// quotes the source command an entry was written from.

internal val EXPECTED_SYSTEMS: List<ExpectedSystem> = EXPECTED_A + EXPECTED_B + EXPECTED_C
