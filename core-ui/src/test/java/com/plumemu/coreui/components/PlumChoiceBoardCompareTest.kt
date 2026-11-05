package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Writes each part at the board's scale (1.5x dp, accent #3E7BE0) to `build/v2compare/<part>_<theme>_ours.png` to set beside
 * the board's crop. It records pictures only; the comparison is by eye and by measuring. Only with V2COMPARE=1: a verify run would otherwise look for
 * these files as goldens, and build/ is not in git.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlumChoiceBoardCompareTest {
    private val boardColours = listOf(0xE5484D, 0xF0642A, 0xF2B630, 0x5FD38D, 0x3CC4C4, 0x3E7BE0, 0xB583E8, 0xE8E4DA).map { Color(0xFF000000 or it.toLong()) }

    private fun shot(
        part: String,
        widthDp: Int,
        heightDp: Int,
        content: @Composable () -> Unit,
    ) {
        assumeTrue(System.getenv("V2COMPARE") != null)
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-land-hdpi")
        for ((theme, base) in listOf("dark" to PlumThemeSettings.Graphite, "light" to PlumThemeSettings.Paper)) {
            captureRoboImage(filePath = "../build/v2compare/${part}_${theme}_ours.png") {
                PlumTheme(base.copy(accent = AccentChoice.Custom(Rgb(0x3E7BE0)))) {
                    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(12.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun segmented() = shot("segmented", 260, 70) { PlumSegmented(listOf("List", "Grid", "Shelf"), 0, focused = false, focusedIndex = null, enabled = true) {} }

    @Test
    fun segmentedFocused() = shot("segmented_focus", 260, 70) { PlumSegmented(listOf("List", "Grid", "Shelf"), 0, focused = true, focusedIndex = 0, enabled = true) {} }

    @Test
    fun swatches() =
        shot("swatches", 430, 70) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                boardColours.forEachIndexed { i, c -> PlumSwatch({ c }, selected = i == 5, focused = i == 2, enabled = true) }
            }
        }

    @Test
    fun choice() =
        shot("choice", 300, 170) {
            Column {
                PlumChoice("Box art and game info", selected = true, focused = false, enabled = true)
                PlumChoice("Box art only", selected = false, focused = false, enabled = true)
                PlumChoice("Plumemu covers", selected = false, focused = false, enabled = true)
            }
        }
}
