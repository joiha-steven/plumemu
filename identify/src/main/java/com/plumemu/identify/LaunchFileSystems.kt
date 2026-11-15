package com.plumemu.identify

import java.util.Locale

/**
 * Systems whose game files are not game data but a small launcher file holding what a client app
 * starts the game by: `steam` (a `.steam` file holds a Steam app id: ES-DE's convention,
 * GameNative's "Export for frontend"), `epic` and `windows` (GameNative's `.epic` / `.gog` /
 * `.amazon` / `.pcgame` id files and WinNative / Winlator `.desktop` shortcuts, which are INI text).
 * No DAT lists them and their bytes say nothing about the game, so they are never opened to be
 * named: the title is the file name ("Broforce.steam" is Broforce), and box art is looked up by
 * that name. The launcher reads the file when the game starts. `windows3x`, `windows9x`, `dos`
 * and `pc` hold real game files and are not in this set.
 */
object LaunchFileSystems {
    val IDS: Set<String> = setOf("steam", "epic", "windows")

    /** Why such a game is not read, for the scan's log line. */
    const val REASON = "its files hold an app id, not game data: no DAT and nothing to hash"

    fun isLaunchFile(systemId: String): Boolean = systemId.lowercase(Locale.ROOT) in IDS
}
