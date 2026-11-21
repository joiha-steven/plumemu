package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A list in another order than by name says so in the top bar ("Super Nintendo · 3 games ·
 * Recently added"): with nothing on screen saying it, a list sorted Recently added read as broken.
 * Lists that keep their own order (Recent, Apps) and the systems column say nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HeaderSortTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val snes = SystemGames("snes", "Super Nintendo", listOf("Zelda", "Mario", "Actraiser").mapIndexed { i, t -> GameRowModel("snes-$i", t, "USA") })

    private fun place(sort: SortGamesSetting, column: Column): String {
        val content = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(snes), otherSort = sort.takeIf { it != SortGamesSetting.Name })
        val snesIndex = content.entries.indexOfFirst { it.id == "snes" }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(), initialFocus = StripFocus(column = column, systemIndex = snesIndex, systemId = "snes")) }
        }
        compose.waitForIdle()
        val bar = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).fetchSemanticsNode()
        return generateSequence(listOf(bar)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }
            // The bar draws its emphasis apart from the rest; the clock's text has a colon.
            .filterNot { it.contains(":") }.joinToString(" · ")
    }

    @Test
    fun aListSortedOtherThanByNameNamesItsSort() =
        assertEquals("Super Nintendo · 3 games · Recently added", place(SortGamesSetting.RecentlyAdded, Column.Games))

    @Test
    fun aListByNameSaysOnlyItsCount() = assertEquals("Super Nintendo · 3 games", place(SortGamesSetting.Name, Column.Games))

    @Test
    fun theSystemsColumnSaysNoSort() = assertEquals("1 system · 3 games", place(SortGamesSetting.LastPlayed, Column.Systems))
}

/** The top bar of a list sorted Recently added, at the reference sizes: the sort fits beside the count. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HeaderSortScreenshotTest {
    @Test
    fun recentlyAdded() =
        ReferenceShots.capture("home_header_sorted_recently_added") {
            val content =
                remember {
                    val titles = listOf("Yoshi's Island", "Super Metroid", "Chrono Trigger", "Mega Man X", "EarthBound", "Final Fantasy VI", "Donkey Kong Country 2", "Actraiser")
                    val games = titles.mapIndexed { i, t -> GameRowModel("snes-$i", t, "USA") }
                    HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", games)), otherSort = SortGamesSetting.RecentlyAdded)
                }
            HomeAt(content, listOf(Action.TapItem(Column.Systems, content.entries.indexOfFirst { it.id == "snes" }), Action.Enter))
        }
}
