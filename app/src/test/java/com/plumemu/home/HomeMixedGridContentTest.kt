package com.plumemu.home

import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which entries have a grid and in what shapes. Recent is a folder inside Collections since the Collections change, so its entry is `folderEntries[0]`. */
class HomeMixedGridContentTest {
    private val psp = GameRowModel("psp-0", "Daxter", "USA", systemAbbr = "PSP", systemId = "psp")
    private val snes = GameRowModel("snes-0", "Chrono Trigger", "USA", systemAbbr = "SNES", systemId = "snes")
    private val hiddenNes = GameRowModel("nes-0", "Zelda", "USA", systemAbbr = "NES", systemId = "nes")
    private val unknown = GameRowModel("x-0", "Link", "", systemId = "shortcuts")
    private val app = GameRowModel("app-0", "Clock", "")

    private val content =
        HomeContent.of(
            recent = listOf(psp, snes, hiddenNes, unknown),
            favorites = listOf(snes),
            apps = listOf(app),
            systems = listOf(SystemGames("psp", "PSP", listOf(psp), CoverShape(0.58f)), SystemGames("snes", "Super Nintendo", listOf(snes), CoverShape(1.4f), shortName = "SNES")),
            hasBoxArt = true,
            hiddenSystems = listOf(SystemGames("nes", "NES", listOf(hiddenNes), CoverShape(0.7f))),
        )
    private val recent = content.folderEntries.first { it.id == HomeContent.RECENT_ID }
    private val favorites = content.entries[0]
    private val apps = content.entries[FIRST_SYSTEM_INDEX - 1]

    @Test
    fun recentFavoritesAndAppsHaveAGrid() {
        assertEquals(true, recent.hasGridView)
        assertEquals(true, favorites.hasGridView)
        assertEquals("Apps' icon grid", true, apps.hasGridView)
        assertEquals(true, content.entries[FIRST_SYSTEM_INDEX].hasGridView)
        val all = setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID)
        assertEquals(listOf(true, true), listOf(recent, favorites).map { content.showsGrid(it, all) })
        assertEquals(true, content.showsGrid(apps, all))
        assertEquals("the folder list itself is never a grid", false, content.showsGrid(content.entries[1], all))
    }

    @Test
    fun eachGameKeepsItsOwnSystemsShapeHiddenSystemsIncluded() {
        assertEquals(CoverShape(0.58f), recent.shapeOf(psp))
        assertEquals(CoverShape(1.4f), recent.shapeOf(snes))
        assertEquals("a system hidden from the column still lends its shape", CoverShape(0.7f), recent.shapeOf(hiddenNes))
        assertEquals("a system with no shape: landscape, like a system the data does not know", CoverShape.Landscape, recent.shapeOf(unknown))
        assertEquals(CoverShape(1.4f), favorites.shapeOf(snes))
        assertEquals("a system's own games take its shape", CoverShape(0.58f), content.entries[FIRST_SYSTEM_INDEX].shapeOf(psp))
    }

    @Test
    fun recentAndFavoritesKnowEachSystemsShortNameWhereTheDataHasOne() {
        assertEquals(mapOf("snes" to "SNES"), recent.systemShortNames)
        assertEquals(mapOf("snes" to "SNES"), favorites.systemShortNames)
        assertEquals("a system's own entry needs none", emptyMap<String, String>(), content.entries[FIRST_SYSTEM_INDEX].systemShortNames)
    }

    @Test
    fun recentAndFavoritesCellsAreTheSquareClasssColumns() {
        val layout = PlumLayout(width = 768.dp, height = 432.dp)
        assertEquals(CoverClass.Square, MixedGridShape.coverClass)
        assertEquals(CoverClass.Square, recent.coverShape.coverClass)
        val strip = content.stripWith(setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID), layout)
        val square = layout.gridColumns(CoverClass.Square)
        // Apps' grid takes its own columns from the width, never the square class's; the folder list is one column and its Recent folder the square class's.
        assertEquals(listOf(square, 1, appsGridColumns(layout)), strip.systems.take(3).map { it.gridColumns })
        assertEquals(square, strip.systems[1].folders.first { it.id == HomeContent.RECENT_ID }.gridColumns)
    }

    @Test
    fun withoutBoxArtRecentAndFavoritesHaveAGridToo() {
        val noArt = HomeContent.of(listOf(psp, snes), listOf(psp), emptyList(), listOf(SystemGames("psp", "PSP", listOf(psp), coverShape = CoverShape(0.58f)), SystemGames("snes", "SNES", listOf(snes), coverShape = CoverShape(1.4f))))
        val noArtRecent = noArt.folderEntries.first { it.id == HomeContent.RECENT_ID }
        assertEquals(true, noArt.showsGrid(noArtRecent, setOf(HomeContent.RECENT_ID)))
        assertEquals(true, noArt.showsGrid(noArt.entries[0], setOf(HomeContent.FAVORITES_ID)))
        assertEquals("each game keeps its own system's shape", CoverShape(0.58f), noArtRecent.shapeOf(psp))
        assertEquals(CoverShape(1.4f), noArtRecent.shapeOf(snes))
        val noGames = HomeContent.of(emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("an entry without games stays a list", false, noGames.showsGrid(noGames.folderEntries.first { it.id == HomeContent.RECENT_ID }, setOf(HomeContent.RECENT_ID)))
    }
}
