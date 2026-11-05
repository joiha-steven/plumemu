package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The focused row's slab next to resting rows: games-list rows (rest, focused, rest), then Settings-style rows (a resting row with a value,
 * a focused row with a switch, a resting row). Shots `focus_slab_rows_*` at 768x432, 576x432 and 832x468, Graphite and Paper, and at Largest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FocusSlabScreenshotTest {
    @Composable
    private fun SwitchRow(
        label: String,
        focused: Boolean,
    ) {
        val type = PlumTheme.type
        val colors = PlumTheme.colors
        Row(
            Modifier
                .fillMaxWidth()
                .height(PlumTheme.layout.rowHeight)
                .focusPill(focused)
                .padding(horizontal = PlumDimens.gameRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(label, style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = if (focused) colors.strong else colors.text2))
            Spacer(Modifier.weight(1f))
            // The slab already says the row is focused: the switch keeps its resting collar (`FocusSlabSwitchTest`).
            PlumSwitch(on = true, focused = false)
        }
    }

    @Composable
    private fun Rows() {
        Column(
            Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
        ) {
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
                GameRow("Super Mario World", "1990", GameRowLook.Normal)
                GameRow("Super Metroid", "1994", GameRowLook.Focused)
                GameRow("Super Punch-Out!!", "1994", GameRowLook.Normal)
                Spacer(Modifier.height(12.dp))
                OptionRow("Box art", OptionRowLook.Normal, value = "ScreenScraper")
                SwitchRow("Vibration", focused = true)
                OptionRow("Version", OptionRowLook.Normal, value = "0.1.2")
            }
        }
    }

    @Test
    fun rows() = ReferenceShots.capture("focus_slab_rows", sizes = ReferenceShots.Size.WithPocket6) { Rows() }

    @Test
    fun rowsAtLargest() = ReferenceShots.capture("focus_slab_rows", textSize = TextSize.Largest, sizes = ReferenceShots.Size.WithPocket6) { Rows() }
}
