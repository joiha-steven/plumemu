package com.plumemu.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.FieldRow
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Flat-face keys and sunken tracks (decision 0011): hint keys, a dialog, switches, a field, the progress bar. Shots `flat_face_<size>_<theme>.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FlatFaceScreenshotTest {
    @Test
    fun keysDialogSwitchesFieldAndProgress() =
        ReferenceShots.capture("flat_face") {
            val colors = PlumTheme.colors
            Column(Modifier.fillMaxSize().background(colors.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for ((cap, label) in listOf("A" to "OK", "B" to "Back", "X" to "Menu", "Y" to "Search", "START" to "Settings", "SELECT" to "Info", "L1 R1" to "Size")) Hint(cap, label)
                }
                Column(Modifier.background(colors.sel2, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicText("Hide this game?", style = PlumTheme.type.setupButton.copy(color = colors.strong))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SetupButton("Hide", isPrimary = true, isFocused = true)
                        SetupButton("Keep", isPrimary = false, isFocused = false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetupSwitch(isOn = true, stateText = "on")
                    SetupSwitch(isOn = false, stateText = "off")
                }
                FieldRow("ScreenScraper", {}, OptionRowLook.Normal, "", label = "Username", labelWidth = 90.dp)
                SetupProgressBar(0.62f)
            }
        }
}
