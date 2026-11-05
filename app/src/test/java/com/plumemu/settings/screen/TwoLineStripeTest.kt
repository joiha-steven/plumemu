package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.ChoiceLook
import com.plumemu.setup.SetupChoice
import com.plumemu.setup.SetupInfoRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * On a focused card with a title and a line under it, the accent stripe stands beside the title's first line: its centre is that
 * line's centre to within half a dp, at every text size (not beside the middle of a long line under the title). Measured on the
 * pixels (the stripe is the accent's solid colour inside the card's left edge), for Setup's choices and info rows, a Settings
 * panel's items and the Confirm row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-xhdpi")
class TwoLineStripeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        title: String,
        width: Int,
        card: @Composable (Modifier) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        var accent = Color.Black
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                accent = PlumTheme.colors.accent
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(24.dp)) { card(Modifier.width(width.dp).testTag("card")) }
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val dp = with(density) { 1.dp.toPx() }
            var first = -1
            var last = -1
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                var hit = false
                for (x in bounds.left.toInt() until (bounds.left + 16 * dp).toInt()) {
                    val p = pixels[x, y]
                    if (abs(p.red - accent.red) < 6f / 255f && abs(p.green - accent.green) < 6f / 255f && abs(p.blue - accent.blue) < 6f / 255f) hit = true
                }
                if (hit) {
                    if (first < 0) first = y
                    last = y
                }
            }
            if (first < 0) {
                problems += "$name at $each: no stripe found"
                continue
            }
            val node = compose.onAllNodesWithText(title).fetchSemanticsNodes().first()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val titleMiddle = node.boundsInRoot.top + (results.first().getLineTop(0) + results.first().getLineBottom(0)) / 2f
            val stripeMiddle = (first + last + 1) / 2f
            if (abs(stripeMiddle - titleMiddle) > dp / 2) {
                problems += "$name at $each: stripe centre ${(stripeMiddle - bounds.top) / dp} dp, title line centre ${(titleMiddle - bounds.top) / dp} dp (card ${bounds.height / dp} dp high)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private val line = "Box art plus year, rating, genre, publisher and description. Needs a free ScreenScraper account."

    @Test
    fun setupChoice() = check("setup choice", "Box art and game info", 360) { SetupChoice("Box art and game info", line, ChoiceLook.Focused, it, subMaxLines = 4) }

    @Test
    fun setupInfoRow() = check("setup info row", "Emulators found", 360) { SetupInfoRow("Emulators found", line, isFocused = true, modifier = it) }

    @Test
    fun settingsPanelItem() = check("panel item", "Box art and game info", 205) { PanelItem("Box art and game info", isFocused = true, modifier = it, detail = line, padded = true) }

    @Test
    fun confirmRow() = check("confirm row", "Confirm with", 416) {
        OptionRow(SettingsOption.ConfirmBottom, OptionValue.Text("A"), OptionLook.Focused, it, label = "Confirm with", verticalPadding = PlumDimens.settingsTwoLineRowPadding)
    }
}
