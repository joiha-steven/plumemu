package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hint keys in their collars (confirm in the accent, X held) and a field at rest, focused, in error and filled. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HintFieldCollarScreenshotTest {
    @Composable
    private fun Field(
        label: String,
        text: String = "",
        focused: Boolean = false,
        error: Boolean = false,
        message: String = "",
    ) {
        val colors = PlumTheme.colors
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldRowFrame(look = if (focused) OptionRowLook.Focused else OptionRowLook.Normal, label = label, labelWidth = 72.dp, error = error, modifier = Modifier.width(320.dp)) { style ->
                if (text.isEmpty()) BasicText("Username", style = style.copy(color = colors.muted)) else BasicText(text, style = style.copy(color = colors.text))
            }
            if (message.isNotEmpty()) BasicText(message, style = PlumTheme.type.meta.copy(color = colors.danger))
        }
    }

    @Test
    fun hintsAndFields() =
        ReferenceShots.capture("hint_field_collar") {
            CompositionLocalProvider(LocalHeldKeys provides remember { mutableStateOf(setOf("X")) }) {
                Column(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FittingHintBar {
                        Hint("A", "Play", accent = true)
                        Hint("B", "Back")
                        Hint("X", "Favourite")
                        Hint("Y", "Search")
                        Hint("L1 R1", "Size")
                        Hint("START", "Settings")
                        Hint("SELECT", "Sort")
                    }
                    Field("Rest")
                    Field("Focused", focused = true)
                    Field("Error", text = "jelly", error = true, message = "Wrong username or password")
                    Field("Filled", text = "jelly")
                }
            }
        }
}
