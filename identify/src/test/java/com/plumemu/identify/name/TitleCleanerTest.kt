package com.plumemu.identify.name

import com.plumemu.identify.name.Region.BRAZIL
import com.plumemu.identify.name.Region.EUROPE
import com.plumemu.identify.name.Region.JAPAN
import com.plumemu.identify.name.Region.KOREA
import com.plumemu.identify.name.Region.USA
import com.plumemu.identify.name.Region.WORLD
import com.plumemu.identify.pack.PackMasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TitleCleanerTest {
    private data class Case(val file: String, val title: String, val regions: List<Region>, val sortName: String? = null)

    private val cases = listOf(
        Case("Super Mario World (USA)", "Super Mario World", listOf(USA)),
        Case("Super Mario World (Europe)", "Super Mario World", listOf(EUROPE)),
        Case("Super Mario World (Japan)", "Super Mario World", listOf(JAPAN)),
        Case("Super Metroid (Japan, USA) (En,Ja)", "Super Metroid", listOf(JAPAN, USA)),
        Case("Pokemon - Emerald Version (USA, Europe)", "Pokemon - Emerald Version", listOf(USA, EUROPE)),
        Case("Tetris (World) (Rev 1)", "Tetris", listOf(WORLD)),
        Case("Sonic the Hedgehog (USA, Europe) (Rev A)", "Sonic the Hedgehog", listOf(USA, EUROPE)),
        Case("Donkey Kong Country (USA) (Rev 2) [!]", "Donkey Kong Country", listOf(USA)),
        Case("Street Fighter II Turbo (U) [!]", "Street Fighter II Turbo", listOf(USA)),
        Case("Earthbound (U) [b1]", "Earthbound", listOf(USA)),
        Case("Chrono Trigger (J) [T+Eng1.0]", "Chrono Trigger", listOf(JAPAN)),
        Case("Mega Man X (JU)", "Mega Man X", listOf(JAPAN, USA)),
        Case("Alex Kidd in Miracle World (UE) [h1]", "Alex Kidd in Miracle World", listOf(USA, EUROPE)),
        Case("Final Fantasy VII (USA) (Disc 1)", "Final Fantasy VII", listOf(USA)),
        Case("Metal Gear Solid (Europe) (Disc 2 of 2)", "Metal Gear Solid", listOf(EUROPE)),
        Case("Legend of Zelda, The - A Link to the Past (USA)", "The Legend of Zelda - A Link to the Past", listOf(USA), "legend of zelda - a link to the past"),
        Case("Legend of Zelda, The (USA)", "The Legend of Zelda", listOf(USA), "legend of zelda"),
        Case("Boy and His Blob, A - Trouble on Blobolonia (USA)", "A Boy and His Blob - Trouble on Blobolonia", listOf(USA), "boy and his blob - trouble on blobolonia"),
        Case("The Lion King (Europe)", "The Lion King", listOf(EUROPE), "lion king"),
        Case("An American Tail (USA)", "An American Tail", listOf(USA), "american tail"),
        Case("Dr. Mario (World)", "Dr. Mario", listOf(WORLD), "dr. mario"),
        Case("Super Mario Bros. 3 (USA) (Rev 1)", "Super Mario Bros. 3", listOf(USA)),
        Case("Phantasy Star (Brazil)", "Phantasy Star", listOf(BRAZIL)),
        Case("Pulseman (Korea)", "Pulseman", listOf(KOREA)),
        Case("Kirby's Dream Land (USA, Europe) (SGB Enhanced)", "Kirby's Dream Land", listOf(USA, EUROPE), "kirby's dream land"),
        Case("Doom (USA) (Beta) (1994-05-12)", "Doom", listOf(USA)),
        Case("Castlevania (USA) (Proto)", "Castlevania", listOf(USA)),
        Case("Contra (USA) [b]", "Contra", listOf(USA)),
        Case("Ys - The Vanished Omens (Japan) (Unl)", "Ys - The Vanished Omens", listOf(JAPAN)),
        Case("super_mario_kart", "super mario kart", emptyList()),
        Case("Gradius", "Gradius", emptyList()),
        Case("Bomberman  '94   (Japan)", "Bomberman '94", listOf(JAPAN), "bomberman '94"),
        Case("(USA)", "(USA)", listOf(USA), "usa)"),
        Case("Pok\u00e9mon - Red Version (USA, Europe)", "Pok\u00e9mon - Red Version", listOf(USA, EUROPE), "pokemon - red version"),
        Case("...Iru! (Japan)", "...Iru!", listOf(JAPAN), "iru!"),
        Case("Hong Kong 97 (Hong Kong)", "Hong Kong 97", listOf(Region.HONG_KONG)),
        // File names only, from the corpus failures: download-site marks.
        Case("Soldam-romsfun", "Soldam", emptyList()),
        Case("Pokemon Sapphire (Taiwan)-romsgames.com", "Pokemon Sapphire", listOf(Region.TAIWAN)),
        Case("romsmania.cc - Plushees (USA)", "Plushees", listOf(USA)),
        Case("MotoGP 07 (USA) - www.emuparadise.me", "MotoGP 07", listOf(USA)),
        Case("Jikkyou Powerful Pro Yakyuu 2012 (Japan) {www.loveroms.com}", "Jikkyou Powerful Pro Yakyuu 2012", listOf(JAPAN)),
        Case("Tongueman's Logic_retrostic", "Tongueman's Logic", emptyList()),
        Case("Inoue Ryouko - Last Scene (Japan) - Download - www.gamulator.com", "Inoue Ryouko - Last Scene", listOf(JAPAN)),
        Case("emulatorgames.net - Pac-Man 2 - The New Adventures (USA)", "Pac-Man 2 - The New Adventures", listOf(USA)),
        Case("G-ZERO World GP (World) - ROMSPEDIA", "G-ZERO World GP", listOf(WORLD)),
        Case("Fatal Frame II - Crimson Butterfly (USA) - Download - retrostic.com", "Fatal Frame II - Crimson Butterfly", listOf(USA)),
        Case("JRA PAT for Dreamcast V40 L11-cdromance", "JRA PAT for Dreamcast V40 L11", emptyList()),
        Case("Mischief Makers (USA)_[www.ziperto.com]", "Mischief Makers", listOf(USA)),
        Case("Zork Quest (USA) - romspure.cc", "Zork Quest", listOf(USA)),
        Case("Zork Quest (USA) [Download from romsmania.cc]", "Zork Quest", listOf(USA)),
        Case("Vimm's Lair - Zork Quest (Europe)", "Zork Quest", listOf(EUROPE)),
        Case("Title-coolrom.com", "Title", emptyList()),
        // A domain glued to the last word by "-" never takes that word with it.
        Case("Super Mario Bros. 3-coolrom.com", "Super Mario Bros. 3", emptyList()),
        Case("Tetris DX-romsfun.com", "Tetris DX", emptyList()),
        Case("Pokemon Emerald-www.coolrom.com", "Pokemon Emerald", emptyList()),
        Case("Street Fighter II-emuparadise.me", "Street Fighter II", emptyList()),
        Case(".hack_Infection_(USA)", ".hack Infection", listOf(USA), "hack infection"),
        Case(".hack.Infection.USA.PS2DVD-ZRY", ".hack Infection", listOf(USA), "hack infection"),
        // Dots in real titles are never site marks.
        Case("Namco Museum Vol. 1 (USA)", "Namco Museum Vol. 1", listOf(USA)),
        Case("I.Q Mania (Japan)", "I.Q Mania", listOf(JAPAN)),
        Case("Mr. Bloppy (USA)", "Mr. Bloppy", listOf(USA)),
        Case("Sonic 3D Blast (USA)", "Sonic 3D Blast", listOf(USA)),
        Case("Wipeout 2097 (Europe)", "Wipeout 2097", listOf(EUROPE)),
        // Scene release names and release numbers.
        Case("Sangokushi.IX.with.Power.Up.Kit.JPN.PSP-PLAYASiA", "Sangokushi IX with Power Up Kit", listOf(JAPAN)),
        Case("Play_Pre_2_Volume_1_2001_April_NTSC_PS2DVD-DVDiSO", "Play Pre 2 Volume 1 2001 April", emptyList()),
        Case("meine.tierpension.2.eur.multi5.nds-CTR", "meine tierpension 2", listOf(EUROPE)),
        Case("Network.Utility.Disc.Ver.1.00.USA.PSP-BAHAMUT", "Network Utility Disc Ver 1.00", listOf(USA)),
        Case("Disney_Chicken_Little_PAL_PS2DVD-SPARE", "Disney Chicken Little", emptyList()),
        Case("X-Men.Mutant.Academy.USA.PSX-PARADOX", "X-Men Mutant Academy", listOf(USA)),
        Case("Zork.Quest.GC.NTSC.NGC-WRG", "Zork Quest GC", emptyList()),
        // Scene names use region codes only: a region name is a word of the title.
        Case("Animal.Crossing.Wild.World.EUR.NDS-XXX", "Animal Crossing Wild World", listOf(EUROPE)),
        Case("Dragon.Ball.Z.Infinite.World.PAL.MULTI3.PS2DVD-ZRY", "Dragon Ball Z Infinite World", emptyList()),
        Case("Endless_Ocean_Blue_World_PAL_WII-ZRY", "Endless Ocean Blue World", emptyList()),
        Case("Pac-Man_World_GBA-X", "Pac-Man World", emptyList()),
        Case("Zork.Korea.America.NDS-X", "Zork Korea America", emptyList()),
        Case("Pokemon_Version_Rouge_FRA_NDS-X", "Pokemon Version Rouge", listOf(Region.FRANCE)),
        Case("Zork_Quest_GER_NDS-X", "Zork Quest", listOf(Region.GERMANY)),
        Case("0445 - Croket! - Yume no Banker Survival! (J)", "Croket! - Yume no Banker Survival!", listOf(JAPAN)),
        Case("1943 - The Battle of Midway (USA)", "1943 - The Battle of Midway", listOf(USA)),
        Case("1234 - Zork Quest (U)", "1234 - Zork Quest", listOf(USA)),
        Case("Spider-Man (USA)", "Spider-Man", listOf(USA)),
        // Dots and underscores for spaces.
        Case("Speedball.2.(Europe)", "Speedball 2", listOf(EUROPE)),
        Case("Windows93.Adventure.(World)", "Windows93 Adventure", listOf(WORLD)),
        Case("Big.Ichigeki!.Pachi-Slot.Daikouryaku...", "Big Ichigeki! Pachi-Slot Daikouryaku", emptyList()),
        Case("m.a.s.h", "m.a.s.h", emptyList()),
        Case("game_hits!_(usa)", "game hits!", listOf(USA)),
        // The file's case is kept here (title case is TitleCase.forSystem's, for the shown fallback).
        Case("DYNAMITE COP! (USA)", "DYNAMITE COP!", listOf(USA)),
        Case("world cup usa '94 (europe)", "world cup usa '94", listOf(EUROPE)),
        Case("legend of zelda, the - a link to the past (usa)", "the legend of zelda - a link to the past", listOf(USA), "legend of zelda - a link to the past"),
        Case("FINAL FANTASY III (JAPAN)", "FINAL FANTASY III", listOf(JAPAN)),
        Case("KAO the Kangaroo (USA)", "KAO the Kangaroo", listOf(USA)),
        Case("xmen2p", "xmen2p", emptyList()),
        Case("\u30c9\u30e9\u30b4\u30f3\u30af\u30a8\u30b9\u30c8V \u5929\u7a7a\u306e\u82b1\u5ac1 (Japan)", "\u30c9\u30e9\u30b4\u30f3\u30af\u30a8\u30b9\u30c8V \u5929\u7a7a\u306e\u82b1\u5ac1", listOf(JAPAN),
            // sortName drops the voicing marks NFD splits off kana.
            "\u30c8\u30e9\u30b3\u30f3\u30af\u30a8\u30b9\u30c8v \u5929\u7a7a\u306e\u82b1\u5ac1"),
        // Version, update and Switch codes; GoodTools codes.
        Case("Jewels Ocean - Star of Sierra Leone v1.3", "Jewels Ocean - Star of Sierra Leone", emptyList()),
        Case("Animal Crossing New Horizons [01006F8002326000][v0](eShop)", "Animal Crossing New Horizons", emptyList()),
        Case("Kirbys Return to Dream Land Deluxe [01006B601380E000][1.0.0][0][15.0.1]", "Kirbys Return to Dream Land Deluxe", emptyList()),
        Case("Captain Toast [01009BF0072D4000]+[v1.3.0+2DLC]", "Captain Toast", emptyList()),
        Case("Zork Quest[0100ABCDEF012000][US]", "Zork Quest", listOf(USA)),
        Case("Lion King 3, The (U) [a1]", "The Lion King 3", listOf(USA), "lion king 3"),
    )

    @Test
    fun cleansEveryCaseInTheTable() {
        val failures = cases.mapNotNull { case ->
            val cleaned = TitleCleaner.clean(case.file)
            val expectedSort = case.sortName ?: case.title.lowercase()
            val actual = Triple(cleaned.title, cleaned.regions, cleaned.sortName)
            val expected = Triple(case.title, case.regions, expectedSort)
            if (actual == expected) null else "${case.file}: expected $expected, got $actual"
        }
        assertEquals("${failures.size} of ${cases.size} failed:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun tableHasAtLeastThirtyCases() {
        assertEquals(true, cases.size >= 30)
    }

    @Test
    fun variantKeyKeepsOnlyTagsThatMakeADifferentGame() {
        assertEquals("", TitleCleaner.clean("Contra (Europe) (En,Fr,De) [!]").variantKey)
        assertEquals("", TitleCleaner.clean("Contra (USA) [b1]").variantKey)
        assertEquals("", TitleCleaner.clean("Final Fantasy VII (USA) (Disc 1)").variantKey)
        assertEquals("[t+eng]", TitleCleaner.clean("Mother 3 (Japan) [T+Eng]").variantKey)
        assertEquals("(rev 1)", TitleCleaner.clean("Contra (USA) (Rev 1)").variantKey)
        assertEquals("(hack) [h1]", TitleCleaner.clean("Contra (Hack) [h1]").variantKey)
    }

    /** Marks that say nothing about the game do not split copies; translations and hacks still do. */
    @Test
    fun siteMarksVersionAndUpdateCodesAreNotVariants() {
        val sameGame = listOf(
            "Zork Quest (USA) [www.CoolROM.com]", "Zork Quest (USA) (cdromance)", "Zork Quest (USA) {www.loveroms.com}",
            "Zork Quest (USA) [Download from romsmania.cc]", "Zork Quest (USA) (Vimm's Lair)", "Zork Quest [v131072]",
            "Zork Quest (USA) [UPD]", "Zork Quest (Update 1.2)", "Zork Quest [Base]", "Zork Quest [DLC]",
            "Zork Quest [eShop]", "Zork Quest (1.5 GB)", "Zork Quest [0100ABCDEF012000]", "Zork Quest[0100ABCDEF012000][US]",
            "Zork Quest [0100ABCDEF012000][1.0.0][0][15.0.1]", "Zork Quest [0100ABCDEF012800]+[v1.3.0+2DLC]",
            "Zork Quest (U) [!]", "Zork Quest (U) [b1]", "Zork Quest (U) [f1]", "Zork Quest (U) [o1]", "Zork Quest (U) (V1.1) [!]",
            "Zork Quest (USA) (v1.00)", "Zork Quest [v1.0.2]", "Zork Quest v1.3",
        )
        for (name in sameGame) {
            val cleaned = TitleCleaner.clean(name)
            assertEquals(name, "Zork Quest", cleaned.title)
            assertEquals(name, "", cleaned.variantKey)
        }
        assertEquals("[t+eng1.0_aeon genesis]", TitleCleaner.clean("Zork Quest (J) [T+Eng1.0_Aeon Genesis]").variantKey)
        assertEquals("[t-en by spinner 8 v1.1]", TitleCleaner.clean("Zork Quest (J) [T-En by Spinner 8 v1.1]").variantKey)
        assertEquals("[h1c]", TitleCleaner.clean("Zork Quest (U) [h1C]").variantKey)
        assertEquals("(rev a)", TitleCleaner.clean("Zork Quest (USA) (Rev A)").variantKey)
        // A number in round brackets may be part of a name: it still keeps copies apart.
        assertEquals("(1)", TitleCleaner.clean("zork_final(1)").variantKey)
    }

    @Test
    fun readsDiscNumberAndTotal() {
        assertEquals(DiscTag(1, null), TitleCleaner.clean("Final Fantasy VII (USA) (Disc 1)").disc)
        assertEquals(DiscTag(2, 3), TitleCleaner.clean("Game (Europe) (Disc 2 of 3)").disc)
        assertEquals(DiscTag(1, null), TitleCleaner.clean("Game (CD 1)").disc)
        assertEquals(null, TitleCleaner.clean("Game (USA)").disc)
    }

    @Test
    fun discFilesOfOneGameShareABaseKey() {
        assertEquals(TitleCleaner.discBaseKey("Game (USA) (Disc 1)"), TitleCleaner.discBaseKey("Game (USA) (Disc 2 of 2)"))
        assertEquals("game (usa)", TitleCleaner.discBaseKey("Game  (USA) (Disc 1)"))
        assertEquals("game (usa)", TitleCleaner.trackBaseKey("Game (USA) (Track 02)"))
    }

    @Test
    fun sortNameIgnoresCaseAccentsAndLeadingArticles() {
        assertEquals("legend of zelda", TitleCleaner.sortName("The Legend of Zelda"))
        assertEquals("theme park", TitleCleaner.sortName("Theme Park"))
        assertEquals("apple", TitleCleaner.sortName("An Apple"))
        assertEquals("ecco", TitleCleaner.sortName("\u00c9cco"))
    }

    @Test
    fun everyRegionHasItsPackSpellingAndReadsBack() {
        assertEquals(PackMasks.REGIONS.take(Region.entries.size), Region.entries.map { it.noIntroName })
        for (region in Region.entries) {
            assertEquals(region.name, listOf(region), Region.parseGroup(region.noIntroName))
            assertEquals(region.name, listOf(region), Region.fromNoIntroNames(listOf(region.noIntroName)))
        }
    }

    @Test
    fun packRegionNamesOutsideTheEnumAreDropped() {
        assertEquals(listOf(Region.USA, Region.HONG_KONG), Region.fromNoIntroNames(listOf("USA", "Greece", "hong kong", "Latin America", "USA")))
        assertEquals(emptyList<Region>(), Region.fromNoIntroNames(PackMasks.REGIONS.drop(Region.entries.size)))
    }

    /**
     * The Python port in the build tools splits pack titles with the same rules; its test lists
     * the rows of [cases] that apply to DAT names, regions in the pack's spelling. Read here so the
     * two cannot drift: every row must give the same title and regions in Kotlin, and must be a row
     * of [cases].
     */
    @Test
    fun pythonPortCasesGiveTheSameTitlesAndRegions() {
        com.plumemu.identify.pack.PackAssets.assumeNamesTool(PYTHON_TEST)
        val python = File(PYTHON_TEST).readLines().mapNotNull { pythonRow.matchEntire(it.trim()) }.map { match ->
            val (file, title, regions) = match.destructured
            Triple(unescape(file), unescape(title), regions.split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() })
        }
        assertTrue("no rows read from $PYTHON_TEST", python.size >= 25)
        val failures = python.mapNotNull { (file, title, regions) ->
            val cleaned = TitleCleaner.clean(file)
            val actual = cleaned.title to cleaned.regions.map { it.noIntroName }
            when {
                cases.none { it.file == file } -> "$file: not a row of TitleCleanerTest.cases"
                actual != (title to regions) -> "$file: Python expects ${title to regions}, Kotlin gives $actual"
                else -> null
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    private companion object {
        const val PYTHON_TEST = "../tools/names/test_build.py"
        val pythonRow = Regex("""^\("((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)", \[([^\]]*)]\),$""")
        val pythonEscape = Regex("""\\u([0-9a-fA-F]{4})""")

        fun unescape(text: String) = pythonEscape.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
    }

    @Test
    fun downloadAndCopySuffixesAreNotPartOfTheGame() {
        val plain = TitleCleaner.clean("Zelda (USA)")
        listOf("Zelda (USA) (1)", "Zelda (USA) (9)", "Zelda (USA) - Copy", "Zelda (USA) copy", "Zelda (USA) (2) - Copy").forEach {
            val c = TitleCleaner.clean(it, dropCopySuffix = true)
            assertEquals(it, plain.title, c.title)
            assertEquals(it, plain.variantKey, c.variantKey)
            assertEquals(it, plain.sortName, c.sortName)
            assertEquals(it, plain.regions, c.regions)
        }
        assertEquals("Zelda", TitleCleaner.clean("Zelda (1)", dropCopySuffix = true).title)
        // Disc tags and numbers that are part of the name keep working.
        assertEquals(DiscTag(1, null), TitleCleaner.clean("Game (USA) (Disc 1)").disc)
        assertEquals("Game", TitleCleaner.clean("Game (USA) (Disc 1) (1)", dropCopySuffix = true).title)
        assertEquals(DiscTag(1, null), TitleCleaner.clean("Game (USA) (Disc 1) (1)", dropCopySuffix = true).disc)
        assertEquals("Game 2", TitleCleaner.clean("Game 2").title)
        assertEquals("Copy Kitty", TitleCleaner.clean("Copy Kitty").title)
        assertEquals("(rev 1)", TitleCleaner.clean("Game (USA) (Rev 1) (1)", dropCopySuffix = true).variantKey)
        // By default the suffix stays: callers that see one name (GameCopies, RenameMatcher,
        // PlayedGames) never take Game (1) and Game (2) for copies of one another.
        val keys = listOf("Game (1)", "Game (2)", "Game (3)").map { TitleCleaner.clean(it).variantKey }
        assertEquals(3, keys.distinct().size)
    }

    @Test
    fun fullWidthNamesReadLikeAsciiOnes() {
        val wide = "\uFF26\uFF29\uFF2E\uFF21\uFF2C\u3000\uFF26\uFF21\uFF2E\uFF34\uFF21\uFF33\uFF39\u3000\uFF08\uFF2A\uFF41\uFF50\uFF41\uFF4E\uFF09"
        val c = TitleCleaner.clean(wide)
        assertEquals("FINAL FANTASY", c.title)
        assertEquals(listOf(JAPAN), c.regions)
        assertEquals(TitleCleaner.clean("FINAL FANTASY (Japan)").sortName, c.sortName)
        assertEquals(MatchKey.of("FINAL FANTASY (Japan)"), MatchKey.of(wide))
        assertEquals(MatchKey.of("Final Fantasy 7"), MatchKey.of("Final Fantasy \uFF17"))
    }
}
