package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The segmented control, swatches and one-of-several choice at rest, focused, selected and off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlumChoiceScreenshotTest {
    private val opts = listOf("List", "Grid", "Shelf")
    private val swatchColors = listOf(0xFFE5484D, 0xFFF2B630, 0xFF5FD38D, 0xFF3E7BE0, 0xFFE8E4DA, 0xFF111214).map { Color(it) }

    @Composable
    private fun Content() {
        Column(
            Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PlumSegmented(opts, 0, focused = false, focusedIndex = null, enabled = true) {}
                PlumSegmented(opts, 0, focused = true, focusedIndex = 0, enabled = true) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PlumSegmented(opts, 0, focused = true, focusedIndex = 2, enabled = true) {}
                PlumSegmented(opts, 1, focused = false, focusedIndex = null, enabled = false) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatchColors.forEachIndexed { i, c ->
                    PlumSwatch({ c }, selected = i == 3, focused = i == 1, enabled = true)
                }
                PlumSwatch({ swatchColors[3] }, selected = true, focused = false, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(240.dp)) {
                    PlumChoice("Box art and game info", selected = true, focused = false, enabled = true)
                    PlumChoice("Box art only", selected = false, focused = true, enabled = true, supporting = "Hides the title line")
                    PlumChoice("Plumemu covers", selected = false, focused = false, enabled = true)
                }
                Column(Modifier.width(240.dp)) {
                    PlumChoice("Focused and chosen", selected = true, focused = true, enabled = true)
                    PlumChoice("Off and chosen", selected = true, focused = false, enabled = false, supporting = "Needs a library")
                    PlumChoice("Off", selected = false, focused = false, enabled = false)
                }
            }
        }
    }

    @Test
    fun parts() = ReferenceShots.capture("plum_choice") { Content() }
}
