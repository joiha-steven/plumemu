package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On the box art step the choice in force, while the cursor is on another, has no fill (accent text, weight 600, the outline):
 * the focused card's slab is the only filled card, so the two are never mistaken for each other.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupBoxArtChosenLookTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(PlumThemeSettings.Graphite)
    private var density = Density(1f)
    private var page = Color.Unspecified

    private fun px(dp: Dp) = with(density) { dp.toPx() }

    private fun gap(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    @Test
    fun theChosenCardHasNoFillAndTheFocusedOneDoes() {
        val state = SetupFakes.boxArtQuestion.copy(focus = SetupControl.UseBoxArt, boxArtSource = BoxArtSourceSetting.LibretroOnly)
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(theme) {
                page = PlumTheme.colors.bg
                Box(Modifier.fillMaxSize()) { SetupAt(state) }
            }
        }
        val problems = mutableListOf<String>()
        for ((name, look) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
            theme = look
            compose.waitForIdle()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            fun inside(control: SetupControl): Color {
                val box = compose.onNodeWithTag(SetupTags.control(control), useUnmergedTree = true).getBoundsInRoot()
                // Well inside the card, right of its text, halfway down.
                return pixels[px(box.right - 24.dp).toInt(), px((box.top + box.bottom) / 2).toInt()]
            }
            val chosen = inside(SetupControl.BoxArtLibretro)
            val focused = inside(SetupControl.UseBoxArt)
            if (gap(chosen, page) > FLAT) problems += "$name: the chosen card has a fill (${gap(chosen, page)} from the page)"
            if (gap(focused, chosen) < FOCUS_GAP) problems += "$name: the focused card does not stand off the chosen one (${gap(focused, chosen)})"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val FLAT = 1.5f / 255f
        const val FOCUS_GAP = 6f / 255f
    }
}
