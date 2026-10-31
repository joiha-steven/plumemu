package com.plumemu.identify

import java.util.Locale

/**
 * Systems whose game files are not game data but a small text file holding the id a client app
 * starts the game by: `steam`, whose `.steam` file holds a Steam app id (ES-DE's convention,
 * GameNative's "Export for frontend"). No DAT lists them and their bytes say nothing about the
 * game, so they are never opened to be named: the title is the file name ("Broforce.steam" is
 * Broforce), and box art is looked up by that name. The launcher reads the id when the game starts.
 */
object LaunchFileSystems {
    val IDS: Set<String> = setOf("steam")

    /** Why such a game is not read, for the scan's log line. */
    const val REASON = "its files hold an app id, not game data: no DAT and nothing to hash"

    fun isLaunchFile(systemId: String): Boolean = systemId.lowercase(Locale.ROOT) in IDS
}
