package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The letter key shared by L1/R1 and the peek's letter rail, the rail's letters and its window (the key is the first letter of the sort name the lists are ordered by).
 */
class LetterRailTest {
    private fun rows(vararg titles: String) = titles.mapIndexed { i, t -> GameRowModel("$i", t, "") }

    private fun key(title: String) = LetterKey.of(GameRowModel("k", title, ""))

    @Test
    fun keyIsTheFirstLetterOfTheSortNameUpperCased() {
        assertEquals('A', key("alpha"))
        assertEquals('A', key("Alpha"))
        assertEquals('S', key("super Metroid"))
    }

    @Test
    fun aLeadingArticleIsSkippedLikeTheSortOrder() {
        assertEquals('L', key("The Legend of Zelda"))
        assertEquals('B', key("A Boy and His Blob"))
        assertEquals('A', key("An Adventure"))
        assertEquals("only an article word", 'T', key("Theme Park"))
    }

    @Test
    fun accentsFoldToTheirLetter() {
        assertEquals('E', key("\u00c9dition sp\u00e9ciale"))
        assertEquals('E', key("\u00e9lan"))
        assertEquals('O', key("\u014ckami"))
        assertEquals('A', key("\u00c5ngstr\u00f6m"))
        // A decomposed accent (e + combining acute) folds the same way.
        assertEquals('E', key("e\u0301cole"))
    }

    @Test
    fun digitsAndBlankGoUnderHashAndLeadingSymbolsAreDropped() {
        for (title in listOf("007 Nightfire", "1942", "")) assertEquals("'$title'", '#', key(title))
        assertEquals("a leading symbol is dropped by the sort name", 'Z', key("'Zelda"))
        assertEquals('B', key("!Bang"))
        assertEquals('S', key("  spaced"))
    }

    @Test
    fun aStoredSortNameWinsOverTheTitle() {
        assertEquals('M', LetterKey.of(GameRowModel("1", "Whatever", "", sortName = "mario kart")))
        assertEquals('#', LetterKey.of(GameRowModel("1", "Whatever", "", sortName = "1942")))
    }

    @Test
    fun railLettersAreThosePresentInListOrder() {
        val list = rows("1942", "007", "Alpha", "Bravo", "Brick", "Zed", "Zulu")
        assertEquals(listOf('#', 'A', 'B', 'Z'), railLettersOf(list))
        assertEquals(emptyList<Char>(), railLettersOf(emptyList()))
    }

    @Test
    fun theRealSortNameOrderGivesLMTAndTheLegendLightsL() {
        // As the library orders them (ORDER BY sort_name): "The Legend of Zelda" sorts as "legend of zelda", before Lemmings.
        val games = listOf("The Legend of Zelda" to "legend of zelda", "Lemmings" to "lemmings", "Lost Vikings" to "lost vikings", "Mario Kart" to "mario kart", "Tetris" to "tetris")
        val list = games.mapIndexed { i, (title, sort) -> GameRowModel("$i", title, "", sortName = sort) }
        assertEquals(listOf('L', 'M', 'T'), railLettersOf(list))
        assertEquals('L', LetterKey.of(list[0]))
        // The same list with no stored sort names (the fakes) gives the same rail.
        assertEquals(listOf('L', 'M', 'T'), railLettersOf(rows(*games.map { it.first }.toTypedArray())))
        // L1 / R1 land on the same groups: forward from The Legend goes to Mario Kart, the first of M.
        assertEquals(3, letterJumpTarget(list, 0, forward = true))
    }

    @Test
    fun jumpAndRailAgree() {
        val list = rows("1942", "007", "Edge", "\u00c9lan", "eon", "Fable")
        assertEquals(listOf('#', 'E', 'F'), railLettersOf(list))
        assertEquals(2, letterJumpTarget(list, 0, forward = true))
        assertEquals(5, letterJumpTarget(list, 2, forward = true))
        assertEquals("back goes to the start of the group", 2, letterJumpTarget(list, 4, forward = false))
    }

    @Test
    fun noWindowWhenTheLettersFit() {
        val letters = ('A'..'E').toList()
        assertEquals(letters, railWindow(letters, 'C', 5))
        assertEquals(letters, railWindow(letters, 'C', 9))
    }

    @Test
    fun windowCentresOnTheFocusedLetterAndStaysInside() {
        val letters = ('A'..'Z').toList()
        assertEquals(('J'..'P').toList(), railWindow(letters, 'M', 7))
        assertEquals("at the start", ('A'..'G').toList(), railWindow(letters, 'B', 7))
        assertEquals("at the end", ('T'..'Z').toList(), railWindow(letters, 'Y', 7))
        assertEquals("an even size leans the extra letter below", ('J'..'Q').toList(), railWindow(letters, 'M', 8))
        assertEquals("nothing focused: the first ones", ('A'..'C').toList(), railWindow(letters, null, 3))
        assertEquals("a room of zero still shows one", listOf('M'), railWindow(letters, 'M', 0))
    }

    @Test
    fun appsLeaveThePinnedSettingsRowOutOfTheRail() {
        // InstalledApps pins Android's Settings first, then orders by label: the rail must not start with its S.
        val pinned = listOf("Settings", "Browser", "Calculator", "Clock", "Files").mapIndexed { i, t -> GameRowModel("$i", t, "", sortName = LetterKey.foldedLabel(t)) }
        val apps = HomeContent.of(emptyList(), emptyList(), pinned, emptyList()).entries.first { it.kind == EntryKind.Apps }
        assertEquals(1, apps.railStart)
        assertEquals(listOf('B', 'C', 'F'), apps.railLetters)
        // Without a pinned row (Settings not installed, or it sorts first anyway) nothing is left out.
        val plain = listOf("Browser", "Calculator", "Settings").mapIndexed { i, t -> GameRowModel("$i", t, "", sortName = LetterKey.foldedLabel(t)) }
        val plainApps = HomeContent.of(emptyList(), emptyList(), plain, emptyList()).entries.first { it.kind == EntryKind.Apps }
        assertEquals(0, plainApps.railStart)
        assertEquals(listOf('B', 'C', 'S'), plainApps.railLetters)
        // An app's article is not skipped: the list is by label.
        assertEquals('T', LetterKey.of(GameRowModel("t", "The Weather", "", sortName = LetterKey.foldedLabel("The Weather"))))
    }

    @Test
    fun recentAndLastPlayedHaveNoRail() {
        val games = rows("Alpha", "Bravo")
        val byName = HomeContent.of(games, games, games, listOf(SystemGames("snes", "Super Nintendo", games)))
        assertTrue(byName.entries.first { it.kind == EntryKind.Favorites }.hasLetterRail)
        assertTrue(byName.entries.first { it.kind == EntryKind.Apps }.hasLetterRail)
        assertTrue(byName.entries.first { it.kind == EntryKind.System }.hasLetterRail)
        assertFalse("Recent is by recency", byName.entries.first { it.kind == EntryKind.Recent }.hasLetterRail)
        assertEquals(emptyList<Char>(), byName.entries.first { it.kind == EntryKind.Recent }.railLetters)

        val byPlayed = HomeContent.of(games, games, games, listOf(SystemGames("snes", "Super Nintendo", games)), sortedByName = false)
        assertTrue(byPlayed.entries.filter { it.kind != EntryKind.Apps }.none { it.hasLetterRail })
        // The apps list is by label whatever the games' sort.
        assertTrue(byPlayed.entries.first { it.kind == EntryKind.Apps }.hasLetterRail)
        assertEquals(emptyList<Char>(), byPlayed.entries.first { it.kind == EntryKind.System }.railLetters)
    }
}
