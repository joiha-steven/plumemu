package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game menu's list clips a little past its top (the slab's overhang), so a row cut at the top must be covered by the top fade over
 * all of what shows: no row part is drawn above the fade, where it would end in a hard edge under the title.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelTopFadeReachTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")

    private fun assertFadeCoversEveryRow(text: TextSize) {
        val facts = sampleFacts(canFixBoxArt = true, canSort = true).copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true), sort = SortGamesSetting.Name)
        val rows = OpenPanel(game, PanelScreen.Menu(facts)).content.focusableRows
        var focus by mutableIntStateOf(0)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(game, PanelScreen.Menu(facts), focus = focus))
            }
        }
        var checked = 0
        for (at in rows.indices) {
            focus = at
            compose.waitForIdle()
            if (compose.onAllNodesWithTag(GamePanelTags.FADE_TOP, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) continue
            // Unclipped: the strip itself, not the part a parent's bounds leave visible.
            val fadeTop = compose.onNodeWithTag(GamePanelTags.FADE_TOP, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * compose.density.density
            checked++
            for (row in rows) {
                val node = compose.onAllNodesWithTag(GamePanelTags.row(row.key), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: continue
                val box = node.boundsInRoot
                if (box.height <= 0f) continue
                assertTrue("$text focus $at: row ${row.key} shows from ${box.top}, above the top fade (${fadeTop})", box.top >= fadeTop - 0.5f)
            }
        }
        assertTrue("$text: the menu scrolled up at some focus", checked > 0)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun largeAt832() = assertFadeCoversEveryRow(TextSize.Large)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun largestAt832() = assertFadeCoversEveryRow(TextSize.Largest)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun largeAt768() = assertFadeCoversEveryRow(TextSize.Large)
}
