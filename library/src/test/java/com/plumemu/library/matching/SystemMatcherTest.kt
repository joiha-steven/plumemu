package com.plumemu.library.matching

import com.plumemu.library.TestRules
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemMatcherTest {
    private val matcher = SystemMatcher(TestRules.ALL)

    @Test
    fun folderNamesMatchWithoutCaseSpacesOrPunctuation() {
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("snes"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("SNES"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("Super Famicom"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("super-nintendo"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("My Games"))
    }

    @Test
    fun folderNameSharedBySeveralSystemsIsAmbiguous() {
        assertEquals(FolderMatch.Ambiguous(listOf(TestRules.ARCADE, TestRules.FBNEO)), matcher.matchFolder("arcade"))
    }

    @Test
    fun unknownFolderNameIsMatchedByAnExtensionOnlyOneSystemUses() {
        assertEquals(FileMatch.Matched("snes"), matcher.matchFile(FolderMatch.Unknown, "Super Mario World (USA).sfc"))
        assertEquals(FileMatch.Matched("gba"), matcher.matchFile(FolderMatch.Unknown, "Pokemon.GBA"))
        assertEquals(FileMatch.Matched("megadrive"), matcher.matchFile(FolderMatch.Unknown, "Sonic.md"))
    }

    @Test
    fun ambiguousExtensionsAreDecidedByTheFolderName() {
        val psx = matcher.matchFolder("psx")
        val ps2 = matcher.matchFolder("ps2")
        val genesis = matcher.matchFolder("genesis")
        assertEquals(FileMatch.Matched("psx"), matcher.matchFile(psx, "Crash (USA).bin"))
        assertEquals(FileMatch.Matched("megadrive"), matcher.matchFile(genesis, "Sonic (USA).bin"))
        assertEquals(FileMatch.Matched("psx"), matcher.matchFile(psx, "Game.iso"))
        assertEquals(FileMatch.Matched("ps2"), matcher.matchFile(ps2, "Game.iso"))
        assertEquals(FileMatch.Matched("snes"), matcher.matchFile(matcher.matchFolder("snes"), "Game.zip"))
        assertEquals(FileMatch.Matched("gba"), matcher.matchFile(matcher.matchFolder("gba"), "Game.zip"))
    }

    @Test
    fun ambiguousExtensionInAnUnknownFolderIsNotGuessed() {
        assertEquals(FileMatch.AmbiguousExtension, matcher.matchFile(FolderMatch.Unknown, "Game.bin"))
        assertEquals(FileMatch.AmbiguousExtension, matcher.matchFile(FolderMatch.Unknown, "Game.iso"))
        assertEquals(FileMatch.AmbiguousExtension, matcher.matchFile(FolderMatch.Unknown, "Game.zip"))
    }

    @Test
    fun fileWhoseExtensionTheFolderSystemDoesNotUseIsSkipped() {
        assertEquals(FileMatch.UnknownExtension, matcher.matchFile(matcher.matchFolder("snes"), "noextension"))
        assertEquals(FileMatch.UnknownExtension, matcher.matchFile(FolderMatch.Unknown, "Game.xyz"))
    }

    @Test
    fun fileTheFolderSystemDoesNotClaimFallsBackToItsExtension() {
        val snes = matcher.matchFolder("snes")
        assertEquals(FileMatch.Matched("gba"), matcher.matchFile(snes, "Pokemon.gba"))
        // PSX shares no emulator with SNES, so its .pbp-like extensions do not follow into a SNES folder.
        assertEquals(FileMatch.UnknownExtension, matcher.matchFile(snes, "Game.pbp"))
        assertEquals(FileMatch.UnknownExtension, matcher.matchFile(snes, "Game.iso"))
    }

    @Test
    fun ambiguousFolderUsesTheExtensionThenRuleOrder() {
        val arcade = matcher.matchFolder("arcade")
        assertEquals(FileMatch.Matched("fbneo"), matcher.matchFile(arcade, "kof98.7z"))
        assertEquals(FileMatch.Matched("arcade"), matcher.matchFile(arcade, "sf2.zip"))
    }

    @Test
    fun nearestKnownFolderOnThePathDecides() {
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchPath("ROMs", "snes/hacks/translated"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchPath("snes", ""))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchPath("snes", "Hacks"))
        assertEquals(FolderMatch.Unknown, matcher.matchPath("ROMs", "My Games"))
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("ROMs", "psx/PlayStation"))
    }

    @Test
    fun withSeveralKnownFoldersEachFileTakesTheNearestThatClaimsIt() {
        val path = matcher.matchPath("ROMs", "snes/psx")
        assertEquals(FolderMatch.Path(listOf(FolderMatch.System(TestRules.PSX), FolderMatch.System(TestRules.SNES))), path)
        assertEquals(FileMatch.Matched("psx"), matcher.matchFile(path, "Game.cue"))
        assertEquals(FileMatch.Matched("snes"), matcher.matchFile(path, "Game.sfc"))
        // A close kin of a folder further up counts too (SNES shares an emulator with GBA in these rules).
        assertEquals(FileMatch.Matched("gba"), matcher.matchFile(path, "Game.gba"))
        // Claimed by none: the nearest decides as if alone.
        assertEquals(matcher.matchFile(FolderMatch.System(TestRules.PSX), "Game.md"), matcher.matchFile(path, "Game.md"))
        assertEquals(FileMatch.UnknownExtension, matcher.matchFile(path, "Game.xyz"))
        assertEquals(FileMatch.Matched("psx"), matcher.matchFile(path, "Game.bin"))
        assertEquals(FileMatch.Matched("snes"), matcher.matchFile(path, "Game.zip"))
    }

    @Test
    fun aTrailingBracketTagNamesTheSystem() {
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("Super Nintendo (SFC)"))
        assertEquals(FolderMatch.System(TestRules.MEGADRIVE), matcher.matchFolder("Sega Genesis (MD)"))
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchFolder("Super Nintendo (PS1)"))
        // The words before a bracket only when every group is a folder qualifier, never a region or disc.
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("SNES (Hacks)"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("01 - SNES (Hacks) (Favorites)"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("Genesis (Europe)"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("SNES (Hacks) (Europe)"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("Some Game (USA) (Disc 1)"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("Game ( )"))
    }

    @Test
    fun aLeadingNumberIsIgnored() {
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("01 - SNES"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchFolder("1. Super Famicom"))
        assertEquals(FolderMatch.System(TestRules.GBA), matcher.matchFolder("05_GBA"))
        assertEquals(FolderMatch.System(TestRules.PS2), matcher.matchFolder("10) PS2"))
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchFolder("3 PlayStation"))
        assertEquals(FolderMatch.System(TestRules.MEGADRIVE), matcher.matchFolder("02 - Sega Genesis (MD)"))
        // Digits glued to the name are part of it: "2SNES" is not SNES, a four-digit number is no prefix.
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("2SNES"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("1994 - SNES Games"))
        assertEquals(FolderMatch.Unknown, matcher.matchFolder("3 Ninjas Kick Back"))
    }

    @Test
    fun aGameFolderKnownOnlyByItsBracketDoesNotMoveTheGameToAnotherSystem() {
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("ROMs", "PS1/Bust-A-Move (Arcade)"))
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("PlayStation", "Sonic Jam (MD)/extras"))
        // Also when the system folder above is itself known only by its bracket (MinUI).
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("ROMs", "Sony PlayStation (PS1)/Bust-A-Move (Arcade)"))
        assertEquals(FolderMatch.System(TestRules.GBA), matcher.matchPath("ROMs", "Game Boy Advance (GBA)/Sonic (MD)"))
        // Alone, or below folders that name nothing, the bracket decides.
        assertEquals(FolderMatch.System(TestRules.MEGADRIVE), matcher.matchPath("ROMs", "Card 4/Sega Genesis (MD)"))
        assertEquals(FolderMatch.System(TestRules.SNES), matcher.matchPath("ROMs", "Super Nintendo (SFC)/Hacks"))
        // A qualifier folder is weak too: below a known folder it changes nothing.
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("ROMs", "PS1/SNES (Hacks)"))
        assertEquals(FolderMatch.System(TestRules.PSX), matcher.matchPath("ROMs", "Games/PS1/Final Fantasy VII (USA)"))
    }

    @Test
    fun aFileOutsideASystemFolderGoesToTheOneSystemItsExtensionPointsTo() {
        val nes = SystemRule("nes", setOf("nes", "fc"), setOf("nes", "fds", "unf", "zip"), emulators = setOf("mesen", "fceumm"))
        val fds = SystemRule("fds", setOf("fds"), setOf("nes", "fds", "zip"), emulators = setOf("fceumm"))
        val snes = SystemRule("snes", setOf("snes", "sfc"), setOf("sfc", "smc", "gb", "zip"), emulators = setOf("snes9x", "bsnes"))
        val msu1 = SystemRule("snesmsu1", setOf("snesmsu1"), setOf("sfc", "smc", "zip"), emulators = setOf("snes9x"))
        val gb = SystemRule("gb", setOf("gb"), setOf("gb", "sfc", "smc", "dmg", "zip"), emulators = setOf("gambatte", "bsnes"))
        val n64 = SystemRule("n64", setOf("n64"), setOf("n64", "z64", "v64", "ndd", "zip"), emulators = setOf("mupen"))
        val n64dd = SystemRule("n64dd", setOf("n64dd"), setOf("z64", "ndd"), emulators = setOf("mupen"))
        val psx = SystemRule("psx", setOf("psx"), setOf("bin", "iso", "chd", "pbp"), emulators = setOf("duckstation"))
        val psp = SystemRule("psp", setOf("psp"), setOf("iso", "cso", "chd", "pbp"), emulators = setOf("ppsspp"))
        val m = SystemMatcher(listOf(fds, nes, msu1, gb, snes, n64dd, n64, psx, psp))
        fun at(file: String) = m.matchFile(m.matchPath("ROMs", ""), file)
        assertEquals(FileMatch.Matched("nes"), at("Mario.nes")) // named by the extension, though fds claims it too
        assertEquals(FileMatch.Matched("fds"), at("Zelda.fds"))
        assertEquals(FileMatch.Matched("snes"), at("Mario World.sfc")) // the system also called "sfc"
        assertEquals(FileMatch.Matched("gb"), at("Tetris.gb"))
        assertEquals(FileMatch.Matched("n64"), at("Mario 64.z64")) // 64DD is a variant: named after it, same emulator, fewer extensions
        assertEquals(FileMatch.Matched("psp"), at("Game.cso"))
        // snes, its MSU-1 variant and gb claim .smc and none is the one all others are a variant of.
        assertEquals(FileMatch.AmbiguousExtension, at("Mario World.smc"))
        assertEquals(FileMatch.AmbiguousExtension, at("Game.iso"))
        assertEquals(FileMatch.AmbiguousExtension, at("Game.chd"))
        assertEquals(FileMatch.AmbiguousExtension, at("Game.zip"))
        assertEquals(FileMatch.AmbiguousExtension, at("Game.pbp"))
        // Sharing an emulator and fewer extensions is not enough: the variant must be named after the main system.
        val cpc = SystemRule("amstradcpc", setOf("amstradcpc"), setOf("cdt", "dsk"), emulators = setOf("cap32"))
        val gx4000 = SystemRule("gx4000", setOf("gx4000"), setOf("cdt", "dsk", "cpr"), emulators = setOf("cap32"))
        val amstrad = SystemMatcher(listOf(cpc, gx4000))
        assertEquals(FileMatch.AmbiguousExtension, amstrad.matchFile(FolderMatch.Unknown, "Tape.cdt"))
        assertEquals(FileMatch.Matched("gx4000"), amstrad.matchFile(FolderMatch.Unknown, "Cart.cpr"))
        // A system folder still decides first.
        assertEquals(FileMatch.Matched("snesmsu1"), m.matchFile(m.matchFolder("snesmsu1"), "Zelda.sfc"))
        assertEquals(FileMatch.Matched("n64dd"), m.matchFile(m.matchFolder("n64dd"), "Zelda.z64"))
    }

    @Test
    fun noRulesMeansNothingMatches() {
        val empty = SystemMatcher(emptyList())
        assertEquals(FolderMatch.Unknown, empty.matchFolder("snes"))
        assertEquals(FileMatch.UnknownExtension, empty.matchFile(FolderMatch.Unknown, "Game.sfc"))
    }
}
