package com.plumemu.library.matching

import org.junit.Assert.assertEquals
import org.junit.Test

/** Folder names in the RetroArch / No-Intro style, as on an AYN Thor. Rules copied from platforms.json. */
class RetroArchFolderNamesTest {
    private fun rule(id: String, name: String, short: String, display: String, folders: Set<String>, ext: Set<String>) =
        SystemRule(id, folders, ext, setOf(name, short, display))

    private val disc = setOf("iso", "chd", "cue", "bin")
    private val systems = listOf(
        rule("arcade", "Arcade", "Arcade", "Arcade", setOf("arcade"), setOf("7z", "neo", "zip")),
        rule("mame", "Multiple Arcade Machine Emulator", "MAME", "MAME", setOf("mame"), setOf("7z", "chd", "zip")),
        rule("fbneo", "FinalBurn Neo", "FinalBurn Neo", "FinalBurn Neo", setOf("fbneo"), setOf("7z", "zip")),
        rule("fba", "FinalBurn Alpha", "FinalBurn Alpha", "FinalBurn Alpha", setOf("fba"), setOf("7z", "zip")),
        rule("n3ds", "Nintendo 3DS", "3DS", "Nintendo 3DS", setOf("n3ds", "3ds"), setOf("3ds", "cia")),
        rule("nds", "Nintendo DS", "DS", "Nintendo DS", setOf("nds", "ds", "nintendods"), setOf("nds", "zip")),
        rule("gb", "Nintendo Game Boy", "GB", "Game Boy", setOf("gb", "gameboy", "game boy"), setOf("gb", "zip")),
        rule("gba", "Nintendo Game Boy Advance", "GBA", "Game Boy Advance", setOf("gba", "gameboyadvance", "game boy advance"), setOf("gba", "zip")),
        rule("gbc", "Nintendo Game Boy Color", "GBC", "Game Boy Color", setOf("gbc", "gameboycolor", "game boy color"), setOf("gbc", "zip")),
        rule("gc", "Nintendo GameCube", "GameCube", "GameCube", setOf("gc", "gamecube", "ngc"), disc),
        rule("n64", "Nintendo 64", "N64", "Nintendo 64", setOf("n64", "nintendo64"), setOf("n64", "z64", "zip")),
        rule("nes", "Nintendo Entertainment System", "NES", "NES", setOf("nes", "famicom", "fc"), setOf("nes", "zip")),
        rule("snes", "Nintendo SNES (Super Nintendo)", "SNES", "Super Nintendo", setOf("snes", "sfc", "snesna", "superfamicom", "super nintendo"), setOf("sfc", "smc", "zip")),
        rule("switch", "Nintendo Switch", "Switch", "Nintendo Switch", setOf("switch"), setOf("nsp", "xci")),
        rule("neogeo", "SNK Neo Geo", "Neo Geo", "Neo Geo", setOf("neogeo"), setOf("7z", "neo", "zip")),
        rule("dreamcast", "Sega Dreamcast", "Dreamcast", "Dreamcast", setOf("dreamcast"), disc + "gdi"),
        rule("megadrive", "Sega Mega Drive", "Mega Drive", "Mega Drive", setOf("megadrive", "genesis", "megadrivejp", "md", "sega genesis"), setOf("md", "bin", "zip")),
        rule("pcengine", "NEC PC Engine", "PC Engine", "PC Engine", setOf("pcengine", "tg16"), setOf("pce", "zip")),
        rule("psx", "Sony PlayStation", "PS1", "PlayStation", setOf("psx", "ps1", "playstation", "psone"), disc + "pbp"),
        rule("ps2", "Sony PlayStation 2", "PS2", "PlayStation 2", setOf("ps2", "playstation2"), disc),
        rule("psp", "Sony PlayStation Portable", "PSP", "PSP", setOf("psp", "playstationportable"), disc + "cso"),
    )
    private val matcher = SystemMatcher(systems)

    private fun idOf(folder: String): String? = when (val m = matcher.matchFolder(folder)) {
        is FolderMatch.System -> m.rule.id
        is FolderMatch.Ambiguous -> "ambiguous:" + m.rules.joinToString(",") { it.id }
        is FolderMatch.Path -> error("matchFolder never gives a path")
        FolderMatch.Unknown -> null
    }

    @Test
    fun theOwnersSixteenFolders() {
        val expected = linkedMapOf(
            "Arcade - MAME 2003 Plus" to "arcade",
            "Nintendo - 3DS" to "n3ds",
            "Nintendo - DS" to "nds",
            "Nintendo - Game Boy" to "gb",
            "Nintendo - Game Boy Advance" to "gba",
            "Nintendo - Game Boy Color" to "gbc",
            "Nintendo - GameCube" to "gc",
            "Nintendo - N64" to "n64",
            "Nintendo - NES" to "nes",
            "Nintendo - SNES" to "snes",
            "Nintendo - Switch" to "switch",
            "SNK - Neo Geo" to "neogeo",
            "Sega - Dreamcast" to "dreamcast",
            "Sony - PS1" to "psx",
            "Sony - PS2" to "ps2",
            "Sony - PSP" to "psp",
        )
        assertEquals(expected, expected.keys.associateWith { idOf(it) })
    }

    @Test
    fun variantsWithoutSeparatorOrWithOtherSeparators() {
        assertEquals("ps2", idOf("Sony Playstation 2"))
        assertEquals("switch", idOf("Nintendo Switch"))
        assertEquals("ps2", idOf("Sony_PS2"))
        assertEquals("ps2", idOf("Sony – PS2"))
        assertEquals("megadrive", idOf("Sega - Mega Drive - Genesis"))
        assertEquals("pcengine", idOf("NEC - PC Engine - TurboGrafx 16"))
        assertEquals("psx", idOf("PlayStation"))
        assertEquals("gba", idOf("Game Boy Advance"))
    }

    @Test
    fun arcadeEmulatorFamiliesGoToTheGenericArcadeSystem() {
        for (name in listOf("MAME 2003", "MAME 2003 Plus", "MAME 2010", "MAME 2016", "FB Alpha", "Arcade - MAME", "Arcade - MAME 2010", "Arcade - FBNeo", "Arcade - FinalBurn Neo", "Arcade - FB Alpha", "Arcade - FBA")) {
            assertEquals(name, "arcade", idOf(name))
        }
        // Exact aliases and system names keep winning.
        assertEquals("mame", idOf("MAME"))
        assertEquals("fbneo", idOf("FBNeo"))
        assertEquals("fbneo", idOf("FinalBurn Neo"))
        assertEquals("fba", idOf("FBA"))
    }

    @Test
    fun nameSharedBySeveralSystemsStaysAmbiguousAndUnknownStaysUnknown() {
        val twin = SystemMatcher(
            listOf(
                SystemRule("a", setOf("a"), setOf("zip"), setOf("Shared Name")),
                SystemRule("b", setOf("b"), setOf("zip"), setOf("Shared Name")),
            ),
        )
        assertEquals(2, (twin.matchFolder("Maker - Shared Name") as FolderMatch.Ambiguous).rules.size)
        assertEquals(null, idOf("My Games"))
        assertEquals(null, idOf("Sony - Walkman"))
    }

    @Test
    fun discFileInPs2FolderIsMatchedToPs2() {
        val folder = matcher.matchFolder("Sony - PS2")
        assertEquals(FileMatch.Matched("ps2"), matcher.matchFile(folder, "Gran Turismo 4.iso"))
        assertEquals(FileMatch.Matched("psx"), matcher.matchFile(matcher.matchFolder("Sony - PS1"), "Crash.bin"))
        assertEquals(FileMatch.AmbiguousExtension, matcher.matchFile(FolderMatch.Unknown, "Game.iso"))
    }

    @Test
    fun underscoreAfterAnUnknownFirstWordIsNotAMakerSeparator() {
        val dos = SystemMatcher(listOf(SystemRule("dos", setOf("dos"), setOf("zip"), setOf("DOS")), SystemRule("psx", setOf("psx", "doom"), setOf("bin"))))
        assertEquals(FolderMatch.Unknown, dos.matchFolder("Ultimate_Doom"))
        assertEquals("dos", (dos.matchPath("dos", "Ultimate_Doom") as FolderMatch.System).rule.id)
    }

    @Test
    fun partsNamingDifferentSystemsAreAmbiguous() {
        val m = matcher.matchFolder("Sony - PSP - PS2") as FolderMatch.Ambiguous
        assertEquals(listOf("psp", "ps2").sorted(), m.rules.map { it.id }.sorted())
    }

    @Test
    fun everyDatHeaderNameAndPlaylistNameMatchesItsSystem() {
        val all = SystemMatcher(DAT_FOLDER_NAMES.keys.map { SystemRule(it, setOf(it), setOf("zip")) })
        for ((system, names) in DAT_FOLDER_NAMES) for (name in names) {
            val m = all.matchFolder(name)
            assertEquals(name, system, ((m as? FolderMatch.System)?.rule?.id))
        }
        for (name in listOf("Nintendo - Super Nintendo Entertainment System", "FBNeo - Arcade Games", "Nintendo - Family Computer Disk System",
            "NEC - PC Engine SuperGrafx", "Commodore - CD32", "Atari - 8-bit", "Nintendo - Game & Watch", "Sinclair - ZX Spectrum +3")) {
            assertEquals(name, true, all.matchFolder(name) is FolderMatch.System)
        }
    }
}
