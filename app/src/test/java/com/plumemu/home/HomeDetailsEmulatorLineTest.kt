package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details column on the Pocket 6 (1920 x 1080 at 2.25: 853 x 480 dp): the emulator's "Asked on Play" is one line,
 * not wrapped beside LAST PLAYED while the column has room, and a full-width fact spans the whole column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = POCKET6)
class HomeDetailsEmulatorLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Panel() {
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "", players = "1", description = "", rating = null)
        Column(Modifier.width(PlumDimens.detailsColumnWidth).testTag("details_panel")) { DetailsBlock(details, ShownPlay.Fixed(PlayFacts(null, 0, "Asked on Play"))) }
    }

    @Test
    fun theEmulatorLineIsOneLineAndFillsAtMostTheColumn() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Panel() } }
        compose.waitForIdle()
        val column = compose.onNodeWithTag("details_panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithTag("details_panel", useUnmergedTree = true).fetchSemanticsNode().let { root ->
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
                (if (SemanticsProperties.Text in n.config) listOf(n) else emptyList()) + n.children.flatMap(::walk)
            walk(root).first { it.config[SemanticsProperties.Text].joinToString("").startsWith("Asked on") }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("the emulator line wraps: ${node.boundsInRoot} in column $column", 1, layouts.first().lineCount)
    }

    /** The details column as a golden (`home_details_emulator_853x480_graphite.png`). */
    @Test
    fun theColumnGolden() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Panel() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/home_details_emulator_853x480_graphite.png")
    }
}
