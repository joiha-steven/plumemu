package com.plumemu.home

import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which entries have a grid and in what shapes. */
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
            systems = listOf(SystemGames("psp", "PSP", listOf(psp), CoverShape(0.58f)), SystemGames("snes", "Super Nintendo", listOf(snes), CoverShape(1.4f))),
            hasBoxArt = true,
            hiddenSystems = listOf(SystemGames("nes", "NES", listOf(hiddenNes), CoverShape(0.7f))),
        )
    private val recent = content.entries[0]
    private val favorites = content.entries[1]
    private val apps = content.entries[2]

    @Test
    fun recentFavoritesAndAppsHaveAGrid() {
        assertEquals(true, recent.hasGridView)
        assertEquals(true, favorites.hasGridView)
        assertEquals("Apps' icon grid", true, apps.hasGridView)
        assertEquals(true, content.entries[3].hasGridView)
        val all = setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID)
        assertEquals(listOf(true, true, true), content.entries.take(3).map { content.showsGrid(it, all) })
    }

    @Test
    fun eachGameKeepsItsOwnSystemsShapeHiddenSystemsIncluded() {
        assertEquals(CoverShape(0.58f), recent.shapeOf(psp))
        assertEquals(CoverShape(1.4f), recent.shapeOf(snes))
        assertEquals("a system hidden from the column still lends its shape", CoverShape(0.7f), recent.shapeOf(hiddenNes))
        assertEquals("a system with no shape: landscape, like a system the data does not know", CoverShape.Landscape, recent.shapeOf(unknown))
        assertEquals(CoverShape(1.4f), favorites.shapeOf(snes))
        assertEquals("a system's own games take its shape", CoverShape(0.58f), content.entries[3].shapeOf(psp))
    }

    @Test
    fun recentAndFavoritesCellsAreTheSquareClasssColumns() {
        val layout = PlumLayout(width = 768.dp, height = 432.dp)
        assertEquals(CoverClass.Square, MixedGridShape.coverClass)
        assertEquals(CoverClass.Square, recent.coverShape.coverClass)
        val strip = content.stripWith(setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID, HomeContent.APPS_ID), layout)
        val square = layout.gridColumns(CoverClass.Square)
        // Apps' grid takes its own columns from the width, never the square class's.
        assertEquals(listOf(square, square, appsGridColumns(layout)), strip.systems.take(3).map { it.gridColumns })
    }

    @Test
    fun withoutBoxArtThereIsNoGridForThemEither() {
        val noArt = HomeContent.of(listOf(psp), listOf(psp), emptyList(), listOf(SystemGames("psp", "PSP", listOf(psp))))
        assertEquals(false, noArt.showsGrid(noArt.entries[0], setOf(HomeContent.RECENT_ID)))
        assertEquals(false, noArt.showsGrid(noArt.entries[1], setOf(HomeContent.FAVORITES_ID)))
    }
}
