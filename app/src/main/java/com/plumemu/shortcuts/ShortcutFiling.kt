package com.plumemu.shortcuts

import com.plumemu.platforms.Emulator
import com.plumemu.platforms.PlatformCatalog

/**
 * Which list a pinned shortcut joins, by the package of the app that pinned it.
 * The packages come from the platform data, never from a second list here:
 * - an app the `steam` system lists that opens `.steam` files (GameNative, GameHub Lite and its
 *   variants, DroidDeck) files it under [STEAM];
 * - else an app the `windows` system lists (the Winlator family, WinNative, MiceWine) under
 *   [WINDOWS]; WinNative is listed in Steam too, but for `.desktop` files only, so it lands here;
 * - anything else under [SHORTCUTS], the "Shortcuts" list of its own.
 * Every package of an emulator counts (its preferred one and its variants).
 */
class ShortcutFiling(catalog: PlatformCatalog?) {
    private val steamPackages: Set<String> = packagesOf(catalog, STEAM) { it.acceptsFile("game.$STEAM_FILE_EXTENSION") }
    private val windowsPackages: Set<String> = packagesOf(catalog, WINDOWS) { true }

    /** The system id a shortcut of [packageName] is listed under. */
    fun systemFor(packageName: String): String =
        when (packageName) {
            in steamPackages -> STEAM
            in windowsPackages -> WINDOWS
            else -> SHORTCUTS
        }

    companion object {
        const val STEAM = "steam"
        const val WINDOWS = "windows"

        /**
         * The "Shortcuts" list: a system of its own that the platform data does not know, named from
         * the app's strings (`LibraryHomeContent`). No platform system may take this id.
         */
        const val SHORTCUTS = "shortcuts"

        private const val STEAM_FILE_EXTENSION = "steam"

        private fun packagesOf(
            catalog: PlatformCatalog?,
            systemId: String,
            accepts: (Emulator) -> Boolean,
        ): Set<String> {
            val system = catalog?.system(systemId) ?: return emptySet()
            return catalog.emulatorsOf(system).filter(accepts).flatMap { emulator -> emulator.allPackages.map { it.packageName } }.toSet()
        }
    }
}
