package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a game row shows at its right: the system's short name in the mixed lists, else the region or year. */
class RowTrailingTest {
    private val row = GameRowModel("1", "Chrono Trigger", "USA", details = GameDetails(1995, "Square", "RPG", "1", ""), systemAbbr = "SNES")

    @Test
    fun recentFavoritesAndSearchShowTheShortNameInPlaceOfTheYear() {
        assertEquals("SNES", row.trailing(hasBoxArt = true, showsSystem = true))
        assertEquals("SNES", row.trailing(hasBoxArt = false, showsSystem = true))
    }

    @Test
    fun aSystemListKeepsTheYearOrTheRegion() {
        assertEquals("1995", row.trailing(hasBoxArt = true, showsSystem = false))
        assertEquals("USA", row.trailing(hasBoxArt = false, showsSystem = false))
    }

    @Test
    fun aRowWithoutAShortNameFallsBackToTheUsualText() {
        assertEquals("USA", row.copy(systemAbbr = "").trailing(hasBoxArt = false, showsSystem = true))
    }

    @Test
    fun theListKindDecides() {
        assertEquals(listOf(EntryKind.Recent, EntryKind.Favorites), EntryKind.values().filter { it.mixesSystems })
        assertFalse(EntryKind.System.mixesSystems)
        assertFalse(EntryKind.Apps.mixesSystems)
        assertTrue(FakeHomeContent.mixedSystems().entries.first { it.kind == EntryKind.Recent }.rows.all { it.systemAbbr.isNotEmpty() })
    }

    @Test
    fun fakeSystemRowsCarryTheirShortNameButStayYearOrRegionInTheirOwnList() {
        val snes = FakeHomeContent.board().entries.first { it.id == "snes" }
        assertEquals("SNES", snes.rows.first().systemAbbr)
        assertEquals("USA", snes.rows.first().trailing(hasBoxArt = false, showsSystem = snes.kind.mixesSystems))
    }

    private val join: (String, String) -> String = { year, region -> "$year · $region" }

    @Test
    fun aSystemListWithBoxArtShowsYearAndRegionWhenBothAreKnown() {
        assertEquals("1995 · USA", row.trailing(hasBoxArt = true, joinPlace = join))
    }

    @Test
    fun theYearAloneWithoutAJoinOrWithoutARegion() {
        assertEquals("1995", row.trailing(hasBoxArt = true, joinPlace = null))
        assertEquals("1995", row.copy(region = "").trailing(hasBoxArt = true, joinPlace = join))
    }

    @Test
    fun theRegionAloneWhenNoYearIsKnown() {
        val noYear = row.copy(details = GameDetails(null, "Square", "RPG", "1", ""))
        assertEquals("USA", noYear.trailing(hasBoxArt = true, joinPlace = join))
        assertEquals("USA", row.copy(details = null).trailing(hasBoxArt = true, art = ArtSlot.Absent, joinPlace = join))
    }

    @Test
    fun nothingWhileTheResultIsStillUnknown() {
        assertEquals("", row.copy(details = null).trailing(hasBoxArt = true, art = ArtSlot.Unknown, joinPlace = join))
    }

    @Test
    fun withoutBoxArtTheRegionAloneEvenWithAJoin() {
        assertEquals("USA", row.trailing(hasBoxArt = false, joinPlace = join))
    }

    @Test
    fun aMixedListKeepsTheShortNameWhateverTheJoin() {
        assertEquals("SNES", row.trailing(hasBoxArt = true, showsSystem = true, joinPlace = join))
    }
}
