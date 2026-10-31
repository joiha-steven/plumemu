package com.plumemu.identify.arcade

import com.plumemu.identify.name.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArcadeTitlesTest {
    private val cases = listOf(
        "Zork-Men (2 Players ver AAA)" to "Zork-Men",
        "Zork Fighter II' - Hyper Zorking (US 921209)" to "Zork Fighter II' - Hyper Zorking",
        // " / " names a second title: only the first is kept, also after a bracket group.
        "Ninja Zork (US) / Shadow Zork (World)" to "Ninja Zork",
        "Zork / Zork Two" to "Zork",
        // " / " inside brackets is part of the group.
        "Zork (Japan / Korea) / Other" to "Zork",
        // Nested and mixed groups go whole; a group left open runs to the end.
        "Zork (World (rev 2), set 1) Deluxe" to "Zork Deluxe",
        "Zork [bootleg (alt)] (Japan)" to "Zork",
        "Zork (unclosed / group" to "Zork",
        // A stray closing bracket is dropped (MAME has such typos); a slash without spaces is text.
        "Zork 2) stray" to "Zork 2 stray",
        "Zork Reel (Testsoft) (TEST) set 1)" to "Zork Reel set 1",
        "Zork 1/2" to "Zork 1/2",
        "  Zork \t  Quest  " to "Zork Quest",
        // Nothing left: the description itself.
        "(Only Brackets)" to "(Only Brackets)",
        "" to "",
        "Zork" to "Zork",
    )

    @Test fun shortTitles() {
        for ((description, expected) in cases) {
            assertEquals(description, expected, ArcadeTitles.shortTitle(description))
        }
    }

    @Test fun aShortTitleGivesItselfBack() {
        for ((description, _) in cases) {
            val short = ArcadeTitles.shortTitle(description)
            assertEquals(description, short, ArcadeTitles.shortTitle(short))
        }
    }

    /** Real MAME descriptions (from the arcade pack) and the regions the list shows for them. */
    private val regionCases: List<Pair<String, List<Region>>> = listOf(
        "Cadillacs and Dinosaurs (World 930201)" to listOf(Region.WORLD),
        "Cadillacs and Dinosaurs (USA 930201)" to listOf(Region.USA),
        "Marvel Super Heroes Vs. Street Fighter (Japan 970707)" to listOf(Region.JAPAN),
        // "/" splits; the order found is kept.
        "Robocop 2 (Euro/Asia v0.10)" to listOf(Region.EUROPE, Region.ASIA),
        "Rohga: Armor Force (Asia/Europe v5.0)" to listOf(Region.ASIA, Region.EUROPE),
        // A part starts with the region word; what follows it does not matter.
        "Commando (US set 1)" to listOf(Region.USA),
        "Final Fight (USA, set 1)" to listOf(Region.USA),
        "Ghosts'n Goblins (World? set 1)" to listOf(Region.WORLD),
        "Gun.Smoke (USA and Canada, 1985-11-15, set 2)" to listOf(Region.USA, Region.CANADA),
        "Captain America and The Avengers (US Version 1.9)" to listOf(Region.USA),
        "Bubble Bobble (US, Ver 1.0)" to listOf(Region.USA),
        "Puyo Puyo (Japan, Rev A)" to listOf(Region.JAPAN),
        // Every group is read.
        "Alien Storm (World, 2 Players) (FD1094 317-0154)" to listOf(Region.WORLD),
        "Aero Fighters (World / USA + Canada / Korea / Hong Kong / Taiwan) (newer hardware)" to
            listOf(Region.WORLD, Region.USA, Region.CANADA, Region.KOREA, Region.HONG_KONG, Region.TAIWAN),
        // Not read: a language, a region word inside a part, version letters, Oceania.
        "Wonder Boy: Monster Land (English bootleg set 1)" to emptyList(),
        "Ikari (Joystick hack bootleg)" to emptyList(),
        "Bucky O'Hare (ver UAB)" to emptyList(),
        "Teenage Mutant Ninja Turtles (Oceania 2 Players, version ?)" to emptyList(),
        "Snow Bros. - Nick & Tom (set 3)" to emptyList(),
        "Berzerk (revision RC28)" to emptyList(),
        "Gauntlet II" to emptyList(),
    )

    @Test fun regionsOfRealDescriptions() {
        for ((description, expected) in regionCases) {
            assertEquals(description, expected, ArcadeTitles.regions(description))
        }
    }

    @Test fun regionWordsAreWholeWordsInAnyCaseOutsideTitles() {
        val cases: List<Pair<String, List<Region>>> = listOf(
            // A word that only starts like a region is not one.
            "Zork (Usagi set) (Korean) (Asiatic)" to emptyList(),
            "Zork (japan) (EURO)" to listOf(Region.JAPAN, Region.EUROPE),
            // The title and words outside brackets are never read.
            "World Zork USA Japan" to emptyList(),
            // Each region once; a nested group is part of its group; an open group runs to the end.
            "Zork (USA, US) (us)" to listOf(Region.USA),
            "Zork (World (rev 2), set 1) [Japan]" to listOf(Region.WORLD, Region.JAPAN),
            "Zork (Brazil, UK, France, Germany, Spain, Italy, Australia, China" to
                listOf(Region.BRAZIL, Region.UK, Region.FRANCE, Region.GERMANY, Region.SPAIN, Region.ITALY, Region.AUSTRALIA, Region.CHINA),
            // "and" splits only as a word.
            "Zork (Europe AND Asia) (Candy, Korea)" to listOf(Region.EUROPE, Region.ASIA, Region.KOREA),
            "" to emptyList(),
        )
        for ((description, expected) in cases) {
            assertEquals(description, expected, ArcadeTitles.regions(description))
        }
    }

    @Test fun detailsAreTheBracketTextsOnly() {
        val cases: List<Pair<String, List<String>>> = listOf(
            "Cadillacs and Dinosaurs (USA 930201)" to listOf("USA 930201"),
            "Alien Storm (World, 2 Players) (FD1094 317-0154)" to listOf("World, 2 Players", "FD1094 317-0154"),
            "Gauntlet II" to emptyList(),
            // A second title outside brackets is not a detail.
            "Ninja Gaiden (US) / Shadow Warriors" to listOf("US"),
            "Zork (World (rev 2),  set 1) [Japan]" to listOf("World (rev 2), set 1", "Japan"),
            "Zork () (unclosed  group" to listOf("unclosed group"),
            "" to emptyList(),
        )
        for ((description, expected) in cases) {
            assertEquals(description, expected, ArcadeTitles.details(description))
        }
    }

    /** The pack's tier-2 aliases carry titles the Python tool shortened: both must agree. */
    @Test fun pythonPortCasesGiveTheSameShortTitles() {
        com.plumemu.identify.pack.PackAssets.assumeNamesTool(PYTHON_TEST)
        val rows = File(PYTHON_TEST).readLines()
            .dropWhile { !it.contains("CASES = [") }.drop(1)
            .takeWhile { it.trim() != "]" }
            .map { line -> requireNotNull(pythonRow.matchEntire(line.trim())) { "unreadable row in $PYTHON_TEST: $line" } }
        assertTrue("no rows read from $PYTHON_TEST", rows.size >= 10)
        for (row in rows) {
            val (description, expected) = row.destructured
            assertEquals(description, expected, ArcadeTitles.shortTitle(description))
        }
    }

    private companion object {
        const val PYTHON_TEST = "../tools/names/test_arcade_aliases.py"
        val pythonRow = Regex("""^\("([^"\\]*)", "([^"\\]*)"\),$""")
    }
}
