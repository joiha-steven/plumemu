package com.plumemu.library.matching

import com.plumemu.library.corpus.RealData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Folder naming styles people really use (MinUI and Onion tags, stock firmware short names,
 * plain words, numbered folders, files in the picked folder) against the shipped platforms.json,
 * and the near names that must stay with their own system. Robolectric only for a working org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShippedFolderNamesTest {
    private val rules = RealData.systemRules()
    private val matcher = SystemMatcher(rules)

    private fun idOf(folder: String): String? = when (val m = matcher.matchFolder(folder)) {
        is FolderMatch.System -> m.rule.id
        is FolderMatch.Ambiguous -> m.rules.joinToString("|") { it.id }
        is FolderMatch.Path -> error("matchFolder never gives a path")
        FolderMatch.Unknown -> null
    }

    private fun assertFolders(expected: Map<String, String?>) {
        val wrong = expected.filter { (folder, id) -> idOf(folder) != id }.map { (folder, id) -> "$folder: expected $id, got ${idOf(folder)}" }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun minUiAndOnionNameWithTag() = assertFolders(
        mapOf(
            "Super Nintendo (SFC)" to "snes", "Sony PlayStation (PS)" to "psx", "Sega Genesis (MD)" to "megadrive",
            "Game Boy (GB)" to "gb", "Game Boy Color (GBC)" to "gbc", "Game Boy Advance (GBA)" to "gba",
            "PC Engine (PCE)" to "pcengine", "Sega Master System (SMS)" to "mastersystem", "Game Gear (GG)" to "gamegear",
            "Nintendo (FC)" to "nes", "Neo Geo Pocket Color (NGPC)" to "ngpc", "Sony PlayStation 2 (PS2)" to "ps2",
            "Sony PSP (PSP)" to "psp", "Nintendo 64 (N64)" to "n64", "Nintendo DS (NDS)" to "nds", "Arcade (FBNEO)" to "fbneo",
        ),
    )

    @Test
    fun stockFirmwareShortNames() = assertFolders(
        mapOf(
            "FC" to "nes", "NES" to "nes", "SFC" to "snes", "SNES" to "snes", "MD" to "megadrive", "GB" to "gb",
            "GBC" to "gbc", "GBA" to "gba", "PS" to "psx", "PS1" to "psx", "PSX" to "psx", "PS2" to "ps2", "PSP" to "psp",
            "NDS" to "nds", "N64" to "n64", "SMS" to "mastersystem", "GG" to "gamegear", "PCE" to "pcengine",
            "ARCADE" to "arcade", "NEOGEO" to "neogeo", "CPS1" to "cps1", "CPS2" to "cps2", "FBA" to "fba",
            // A person's Thor: GameCube games under "Cube" were an unknown folder.
            "Cube" to "gc", "GC" to "gc", "NGC" to "gc",
        ),
    )

    @Test
    fun plainWords() = assertFolders(
        mapOf(
            "Super Nintendo" to "snes", "Super Famicom" to "snes", "Famicom" to "nes", "Sega Genesis" to "megadrive",
            "Genesis" to "megadrive", "Mega Drive" to "megadrive", "PlayStation" to "psx", "PlayStation 1" to "psx",
            "PlayStation 2" to "ps2", "PlayStation Portable" to "psp", "Nintendo 64" to "n64", "Nintendo DS" to "nds",
            "Game Gear" to "gamegear", "Master System" to "mastersystem", "Dreamcast" to "dreamcast", "Saturn" to "saturn",
            "Game Boy" to "gb", "Game Boy Color" to "gbc", "Game Boy Advance" to "gba", "GameCube" to "gc",
            "PC Engine" to "pcengine", "Arcade" to "arcade", "MAME" to "mame", "FBNeo" to "fbneo", "FinalBurn Neo" to "fbneo",
        ),
    )

    @Test
    fun numberedFolders() = assertFolders(
        mapOf(
            "01 - SNES" to "snes", "1. NES" to "nes", "02 - Genesis" to "megadrive", "03 - Game Boy Advance" to "gba",
            "04 - PlayStation" to "psx", "5. N64" to "n64", "05_N64" to "n64", "06 - PS2" to "ps2", "10. PSP" to "psp",
            "11 - Saturn" to "saturn", "12 Dreamcast" to "dreamcast",
            // A list number goes first, so "3. DS" is DS; without one, "3DS" and "3 DS" stay 3DS.
            "3. DS" to "nds", "3 - DS" to "nds", "3_DS" to "nds", "3) DS" to "nds", "3DS" to "n3ds", "3 DS" to "n3ds",
            "64_DD" to "n64dd",
        ),
    )

    @Test
    fun nearNamesStayWithTheirOwnSystem() = assertFolders(
        mapOf(
            "GB" to "gb", "GBA" to "gba", "GBC" to "gbc", "Game Boy (GBA)" to "gba",
            "PS" to "psx", "PS2" to "ps2", "PSP" to "psp", "PS3" to "ps3", "Sony PlayStation (PS2)" to "ps2",
            "MD" to "megadrive", "Mega CD" to "megacd", "Sega CD" to "megacd", "MD CD" to null,
            "SMS" to "mastersystem", "SNES" to "snes", "GG" to "gamegear", "FC" to "nes", "FDS" to "fds",
            "PCE" to "pcengine", "PC Engine CD" to "pcenginecd", "NGP" to "ngp", "NGPC" to "ngpc",
            "SS" to null, "DC" to null, "PlayStation 3" to "ps3", "01 - PS3" to "ps3", "1. GBC" to "gbc",
        ),
    )

    @Test
    fun aSystemFolderWithQualifiersKeepsItsSystem() = assertFolders(
        mapOf(
            "SNES (Hacks)" to "snes", "GBA (Translations)" to "gba", "Nintendo - SNES (Romhacks)" to "snes",
            "PS1 (Homebrew)" to "psx", "Genesis (Unlicensed)" to "megadrive", "NES (Aftermarket)" to "nes",
            "SNES (MSU-1)" to "snesmsu1", "SNES (MSU)" to "snes", "GBA (Favorites)" to "gba", "N64 (ROM Hacks)" to "n64", "GB (Prototypes)" to "gb",
            "Mega Drive (Complete) (Favourites)" to "megadrive", "02 - SNES (Translated)" to "snes",
            // "SNES (MSU-1)" reads whole as the SNES MSU-1 system, which is better still.
            // Anything else in brackets never lets the words before it count.
            "SNES (Europe)" to null, "SNES (Hacks) (Japan)" to null, "Doom (Hacks)" to "doom",
        ),
    )

    @Test
    fun bracketWordsOfGameFoldersNameNoSystem() {
        val words = listOf(
            "USA", "Japan", "Europe", "World", "Disc 1", "Rev 1", "Hack", "Hacks", "Beta", "Proto", "Translated",
            "En,Fr,De", "Unl", "Demo", "Homebrew", "Imports", "Favorites", "v1.1", "Track 1", "1999",
        )
        assertFolders(words.associate { "Some Game ($it)" to null })
        // Systems named like games: the words before a region bracket are not tried alone.
        assertFolders(
            listOf("Doom (Europe)", "Quake (USA)", "Flashback (Europe)", "Cave Story (USA)", "RPG Maker (USA)", "Epic (Europe)")
                .associateWith { null },
        )
    }

    private fun systemOf(path: String): String? =
        (matcher.matchFile(matcher.matchPath("ROMs", path.substringBeforeLast('/', "")), path.substringAfterLast('/')) as? FileMatch.Matched)?.systemId

    @Test
    fun aGameFolderInsideASystemFolderKeepsThatSystem() {
        val expected = mapOf(
            "Games/PS1/Bust-A-Move 4 (Arcade)/Bust-A-Move 4 (USA).cue" to "psx",
            "Games/PS1/Final Fantasy VII (USA) (Disc 1)/Final Fantasy VII (USA) (Disc 1).chd" to "psx",
            // MinUI: the system folder is itself known only by its tag, and still wins over the game's own.
            "Sony PlayStation (PS)/Bust-A-Move 4 (Arcade)/Bust-A-Move 4 (USA).bin" to "psx",
            "Sony PlayStation (PS)/Doom (USA)/Doom (USA).cue" to "psx",
            "Super Nintendo (SFC)/Doom (Europe)/Doom (Europe).sfc" to "snes",
            "Super Nintendo (SFC)/Doom (Hacks)/Doom (Hacks).wad" to null, // not doom: the qualifier folder is weak
            "Super Nintendo (SFC)/Doom (Hacks)/Doom Hack.sfc" to "snes",
            "PS1/SNES (Hacks)/Game.cue" to "psx",
            "Game Boy Advance (GBA)/Pokemon (GB)/Pokemon Pinball.gba" to "gba",
            "Nintendo DS (NDS)/Lunar - Genesis (Europe)/Lunar - Genesis (Europe).nds" to "nds",
            "Nintendo DS (NDS)/Lunar - Genesis/Lunar - Genesis (Europe).nds" to "nds",
            // A system id that is also a game's name: each file takes the nearest folder that claims it.
            "SNES/Doom/Doom.sfc" to "snes",
            "ports/Doom/doom.wad" to "doom",
            "PSP/Sony - PSP/Patapon (USA).iso" to "psp",
            "Arcade/Neo Geo/mslug.zip" to "neogeo",
            "Consoles/Sega/Genesis/Sonic.md" to "megadrive",
            "Consoles/Sony/PlayStation 2/Okami.iso" to "ps2",
            "Card 4/Super Nintendo (SFC)/Mario.sfc" to "snes",
            "Emulation/Handhelds/Game Boy Color/Tetris DX.gbc" to "gbc",
        )
        val wrong = expected.filter { (path, id) -> systemOf(path) != id }.map { (path, id) -> "$path: expected $id, got ${systemOf(path)}" }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun filesInThePickedFolderGoByTheirExtension() {
        fun at(file: String) = matcher.matchFile(matcher.matchPath("ROMs", ""), file)
        for ((file, id) in listOf("a.gba" to "gba", "a.sfc" to "snes", "a.nes" to "nes", "a.nds" to "nds", "a.z64" to "n64",
            "a.a26" to "atari2600", "a.fds" to "fds", "a.gb" to "gb", "a.gbc" to "gbc", "a.md" to "megadrive",
            "a.sms" to "mastersystem", "a.gg" to "gamegear", "a.pce" to "pcengine", "a.nsp" to "switch",
            "a.ndd" to "n64dd", "a.v64" to "n64")) {
            assertEquals(file, FileMatch.Matched(id), at(file))
        }
        for (file in listOf("a.zip", "a.7z", "a.bin", "a.iso", "a.chd", "a.cue", "a.m3u", "a.pbp", "a.cso", "a.32x",
            "a.cdt", "a.bat", "a.conf", "a.lha", "a.sgx", "a.tar", "a.ngc", "a.rar")) {
            assertEquals(file, FileMatch.AmbiguousExtension, at(file))
        }
    }

    @Test
    fun listsWhereAnExtensionClaimedBySeveralSystemsGoesOutsideASystemFolder() {
        val picks = sortedSetOf<String>()
        for (ext in rules.flatMap { r -> r.extensions.map { it.lowercase() } }.toSet()) {
            val claimants = rules.filter { r -> r.extensions.any { it.lowercase() == ext } }
            if (claimants.size < 2) continue
            val result = matcher.matchFile(FolderMatch.Unknown, "x.$ext")
            if (result is FileMatch.Matched) picks += ".$ext -> ${result.systemId} (of ${claimants.joinToString(",") { it.id }})"
        }
        println("Extension picks outside a system folder (${picks.size}):\n" + picks.joinToString("\n"))
        assertTrue(picks.none { p -> listOf(".zip ", ".7z ", ".rar ", ".bin ", ".iso ", ".chd ").any { p.startsWith(it) } })
    }
}
