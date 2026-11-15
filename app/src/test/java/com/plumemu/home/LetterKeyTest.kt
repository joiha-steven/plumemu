package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The letter key behind the jump by letter (L1/R1) and which lists have letters (the key is the first letter of the sort name the lists are ordered by).
 */
class LetterKeyTest {
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
    fun theRealSortNameOrderFilesTheLegendUnderL() {
        // As the library orders them (ORDER BY sort_name): "The Legend of Zelda" sorts as "legend of zelda", before Lemmings.
        val games = listOf("The Legend of Zelda" to "legend of zelda", "Lemmings" to "lemmings", "Lost Vikings" to "lost vikings", "Mario Kart" to "mario kart", "Tetris" to "tetris")
        val list = games.mapIndexed { i, (title, sort) -> GameRowModel("$i", title, "", sortName = sort) }
        assertEquals('L', LetterKey.of(list[0]))
        // L1 / R1 land on the same groups: forward from The Legend goes to Mario Kart, the first of M.
        assertEquals(3, letterJumpTarget(list, 0, forward = true))
    }

    @Test
    fun jumpsLandOnTheFirstRowOfEachLetter() {
        val list = rows("1942", "007", "Edge", "\u00c9lan", "eon", "Fable")
        assertEquals(2, letterJumpTarget(list, 0, forward = true))
        assertEquals(5, letterJumpTarget(list, 2, forward = true))
        assertEquals("back goes to the start of the group", 2, letterJumpTarget(list, 4, forward = false))
    }

    @Test
    fun anAppsLabelIsFoldedAndItsArticleIsNotSkipped() {
        assertEquals('T', LetterKey.of(GameRowModel("t", "The Weather", "", sortName = LetterKey.foldedLabel("The Weather"))))
    }

    @Test
    fun recentAndLastPlayedHaveNoLetterJump() {
        val games = rows("Alpha", "Bravo")
        val byName = HomeContent.of(games, games, games, listOf(SystemGames("snes", "Super Nintendo", games)))
        assertTrue(byName.entries.first { it.kind == EntryKind.Favorites }.hasLetterRail)
        assertTrue(byName.entries.first { it.kind == EntryKind.Apps }.hasLetterRail)
        assertTrue(byName.entries.first { it.kind == EntryKind.System }.hasLetterRail)
        assertFalse("Recent is by recency", byName.entries.first { it.kind == EntryKind.Recent }.hasLetterRail)

        val byPlayed = HomeContent.of(games, games, games, listOf(SystemGames("snes", "Super Nintendo", games)), sortedByName = false)
        assertTrue(byPlayed.entries.filter { it.kind != EntryKind.Apps }.none { it.hasLetterRail })
        // The apps list is by label whatever the games' sort.
        assertTrue(byPlayed.entries.first { it.kind == EntryKind.Apps }.hasLetterRail)
    }
}
