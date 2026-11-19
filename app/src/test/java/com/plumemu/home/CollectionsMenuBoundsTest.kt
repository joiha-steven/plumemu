package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.GamePanelTags
import com.plumemu.game.OpenPanel
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The collection panels measured: no text cut, clipped or overlapped, in Graphite and Paper at the two reference sizes. */
abstract class CollectionsMenuBoundsBase(
    private val widthDp: Int,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun measure(what: String, panel: OpenPanel, inRpg: Boolean = false) {
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(settings) { if (inRpg) HomeAt(CollectionsShotsBase.content(), CollectionsShotsBase.rpg, panel = panel) else HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        for (each in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            settings = each
            compose.waitForIdle()
            val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            val allow = FitAllowance(setOf(GamePanelTags.ROWS), isLargest = false)
            val problems = textProblems(root, allow)
            assertTrue("$what $widthDp ${each.background}: $problems", problems.isEmpty())
        }
    }

    @Test fun menu() = measure("menu", CollectionsMenuShotsBase.menu())

    @Test fun picker() = measure("picker", CollectionsMenuShotsBase.picker())

    @Test fun name() = measure("name", CollectionsMenuShotsBase.name())

    @Test fun menuRemove() = measure("menu remove", CollectionsMenuShotsBase.menuRemove(), inRpg = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CollectionsMenuBoundsRp5Test : CollectionsMenuBoundsBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class CollectionsMenuBoundsNovaTest : CollectionsMenuBoundsBase(576)
