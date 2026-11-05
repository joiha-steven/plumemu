package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.footerGapToKeys
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * What the sections column DRAWS stops [footerGapToKeys] above the hint keys' drawn top, at every text size and with the
 * cursor on the first section, or (wide screens, where the sections stay) an option focused: a row the column cuts at its bottom fades out
 * before that gap and nothing of it, nor of the focused slab's overhang, shows inside it. Pixels, not bounds:
 * the column's drawn window is taller than its box (the glide's room), which `SettingsSectionsBarGapTest`'s bounds do not see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsGapPixelsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(cursor: String) {
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        var text = androidx.compose.ui.graphics.Color.White
        var background = androidx.compose.ui.graphics.Color.Black
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                text = PlumTheme.colors.text
                background = PlumTheme.colors.bg
                SettingsAt(
                    when (cursor) {
                        "First" -> SettingsFocus(section = SettingsSection.shownIn(SettingsContent()).first())
                        "Last" -> SettingsFocus(section = SettingsSection.shownIn(SettingsContent()).last())
                        else -> optionsAt(SettingsOption.Background)
                    },
                )
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val caps = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            val barTopPx = caps.minOf { it.boundsInRoot.top } - with(density) { (CollarSize.Hint.lip + CollarSize.Hint.groove).toPx() }
            val gapTop = (barTopPx - with(density) { footerGapToKeys(each).toPx() }).toInt() + 1
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val from = with(density) { 24.dp.roundToPx() }
            val to = with(density) { 170.dp.roundToPx() }
            val reference = with(density) { 176.dp.roundToPx() }
            var worst = 0f
            var at = ""
            for (y in gapTop until barTopPx.toInt()) {
                val bg = pixels[reference, y]
                for (x in from until to) {
                    val p = pixels[x, y]
                    val d = maxOf(abs(p.red - bg.red), abs(p.green - bg.green), abs(p.blue - bg.blue))
                    if (d > worst) {
                        worst = d
                        at = "x=${x}px y=${y}px"
                    }
                }
            }
            // The row a column cuts at its end fades out: just above that end the brightest pixel is well under the text's own contrast.
            val contrast = maxOf(abs(text.red - background.red), abs(text.green - background.green), abs(text.blue - background.blue))
            val band = with(density) { FADE_BAND.roundToPx() }
            for (tag in listOf(SettingsTags.SECTIONS, SettingsTags.OPTION_LIST)) {
                val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                if (nodes.isEmpty()) continue
                val box = nodes.first().boundsInRoot
                // Only a column that scrolls forward has a cut row (its fade node exists then).
                if (compose.onAllNodesWithTag(tag + FADE_BOTTOM_SUFFIX, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) continue
                val left = box.left.toInt() + with(density) { 24.dp.roundToPx() }
                val right = box.right.toInt() - with(density) { 24.dp.roundToPx() }
                var brightest = 0f
                for (y in (box.bottom.toInt() - band) until box.bottom.toInt()) for (x in left until right) {
                    val p = pixels[x, y]
                    val bg = pixels[box.left.toInt() + 2, y]
                    brightest = maxOf(brightest, abs(p.red - bg.red), abs(p.green - bg.green), abs(p.blue - bg.blue))
                }
                if (brightest > contrast / 2) problems += "$each: the $tag column's cut row is still ${(brightest / contrast * 100).toInt()}% of the text's contrast at its end"
            }
            if (worst > TOLERANCE) problems += "$each: the sections column draws into the gap above the keys ($at, off the background by ${(worst * 255).toInt()}/255)"
        }
        assertTrue("$cursor:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private companion object {
        const val TOLERANCE = 4f / 255f
        val FADE_BAND = 6.dp
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun firstAt832x468() = check("First")

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun optionFocusedAt832x468() = check("OptionFocused")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun firstAt768x432() = check("First")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun optionFocusedAt768x432() = check("OptionFocused")

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun firstAt576x432() = check("First")

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun firstAt640x480() = check("First")

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun lastAt640x480() = check("Last")
}
