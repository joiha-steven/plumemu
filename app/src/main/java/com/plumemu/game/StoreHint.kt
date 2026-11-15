package com.plumemu.game

import com.plumemu.R

/**
 * PC store apps that can write a `.steam` file (or the like) per game into a folder Plumemu scans, if the person turns the
 * setting on. Their app menu carries one muted line saying so, only while their games are missing from the library.
 * GameHub Lite has no export, so it has no line.
 */
internal enum class StoreHint(val packageName: String, val message: Int, val systems: Set<String>) {
    GameNative("app.gamenative", R.string.app_menu_hint_gamenative, setOf("steam", "epic")),
    DroidDeck("com.droiddeck.launcher", R.string.app_menu_hint_droiddeck, setOf("steam")),
    ;

    companion object {
        /** The hint for [packageName] while none of its [systems] has a game in [systemsWithGames]; null for any other app. */
        fun of(packageName: String, systemsWithGames: Set<String>): StoreHint? =
            entries.firstOrNull { it.packageName == packageName && it.systems.none(systemsWithGames::contains) }
    }
}
