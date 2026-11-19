package com.plumemu.settings.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
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
 * A panel list opened on its second card (Back from a collection's page) never shows the card above it cut across at the top: whole, or not at all. Measured by bounds
 * against the list's top, in both themes, at the default and the largest text.
 */
abstract class SettingsCollectionsRestBase(private val largest: Boolean) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(focused: Int, paper: Boolean) {
        val each = if (paper) PlumThemeSettings.Paper else PlumThemeSettings.Graphite
        val theme = each.copy(textSize = if (largest) TextSize.Largest else TextSize.Small)
        val focus = SettingsCollectionsShotsBase.list.copy(panelIndex = focused)
        compose.setContent { PlumTheme(theme) { SettingsAt(focus, content = SettingsCollectionsShotsBase.content) } }
        compose.waitForIdle()
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).getUnclippedBoundsInRoot()
        for (i in 0 until focused) {
            val nodes = compose.onAllNodesWithTag(SettingsTags.panelItem(i), useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) continue
            val top = nodes.first().positionInRoot.y / DENSITY
            // A card ending above the list's top is out of sight; one reaching into the list must start at or below it.
            val bottom = top + nodes.first().size.height / DENSITY
            assertTrue("paper=$paper card $i from $top to $bottom is cut by the list's top ${list.top.value}", bottom <= list.top.value || top >= list.top.value)
        }
    }

    @Test fun cardAboveTheSecondIsWholeOrGoneGraphite() = check(1, paper = false)

    @Test fun cardAboveTheSecondIsWholeOrGonePaper() = check(1, paper = true)

    private companion object {
        const val DENSITY = 2f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsCollectionsRest768Test : SettingsCollectionsRestBase(largest = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsCollectionsRest576Test : SettingsCollectionsRestBase(largest = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsCollectionsRestLargestTest : SettingsCollectionsRestBase(largest = true)
