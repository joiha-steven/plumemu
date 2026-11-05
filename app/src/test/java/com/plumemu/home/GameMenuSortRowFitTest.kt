package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A game menu row whose value fits beside its label keeps it there: "Sort by…" is as tall as a row with no value, at the default size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameMenuSortRowFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun sortRowIsOneLine(text: TextSize) {
        val facts = sampleFacts().copy(canSort = true, sort = SortGamesSetting.Name)
        val panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Menu(facts), focus = 0)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        compose.waitForIdle()
        fun height(key: String) = compose.onNodeWithTag(GamePanelTags.row(key), useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
        assertEquals("Sort by… with its value on the same line, as tall as Favorite", height("Favorite"), height("SortBy"), 0.5f)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun sortRowIsOneLineAt576Large() = sortRowIsOneLine(TextSize.Large)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun sortRowIsOneLineAt768Large() = sortRowIsOneLine(TextSize.Large)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun sortRowIsOneLineAt576Huge() = sortRowIsOneLine(TextSize.Huge)
}
