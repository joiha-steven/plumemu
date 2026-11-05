package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The collar around a flat-face cap, a circle key, a sunken field and a box cover, at rest, focused and in error,
 * and an off cap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CollarScreenshotTest {
    @Composable
    private fun Cap(
        text: String,
        focused: Boolean,
        error: Boolean = false,
        off: Boolean = false,
        pressed: Boolean = false,
        primary: Boolean = false,
    ) {
        val colors = PlumTheme.colors
        Box(
            Modifier
                .collar(CollarSize.Control, focused = focused, error = error)
                .width(112.dp)
                .height(38.dp)
                .drawBehind {
                    val face = if (off) colors.keyOff else if (primary) FlatFace.collarAccent(colors.accent) else colors.collarCap
                    drawFlatFace(face, press = if (off || pressed) 1f else 0f)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = PlumTheme.type.settingsOption.copy(color = if (off) colors.muted else if (primary) colors.onAccent else colors.text))
        }
    }

    @Composable
    private fun Key(
        text: String,
        focused: Boolean,
        error: Boolean = false,
    ) {
        val colors = PlumTheme.colors
        Box(
            Modifier.collar(CollarSize.Key, focused = focused, error = error).size(24.dp).drawBehind { drawFlatFace(colors.collarCap) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = PlumTheme.type.meta.copy(color = colors.text))
        }
    }

    @Composable
    private fun Field(
        focused: Boolean,
        error: Boolean = false,
    ) {
        val colors = PlumTheme.colors
        Box(
            Modifier
                .collar(CollarSize.Control, focused = focused, error = error)
                .width(140.dp)
                .height(36.dp)
                .drawBehind { drawSunken(colors.sel2, colors.inset) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText("Search", style = PlumTheme.type.settingsOption.copy(color = colors.muted))
        }
    }

    @Composable
    private fun Cover(
        focused: Boolean,
        error: Boolean = false,
    ) {
        val colors = PlumTheme.colors
        Box(
            Modifier
                .collar(CollarSize.Cover, radius = 4.dp, focused = focused, error = error)
                .size(width = 40.dp, height = 56.dp)
                .background(colors.accent, RoundedCornerShape(4.dp)),
        )
    }

    @Composable
    private fun Parts(
        focused: Boolean,
        error: Boolean = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Cap("Play", focused, error)
            Key("A", focused, error)
            Field(focused, error)
            Cover(focused, error)
        }
    }

    @Test
    fun parts() =
        ReferenceShots.capture("collar_parts") {
            Column(
                Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Parts(focused = false)
                Parts(focused = true)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cap("Cancel", focused = false, pressed = true)
                    Cap("Play", focused = false, primary = true)
                    Cap("Play", focused = true, primary = true)
                    Cap("Play", focused = false, primary = true, pressed = true)
                }
                Parts(focused = false, error = true)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cap("Cancel", focused = false, off = true)
                    // Focused and in error: the danger groove stays and an accent ring sits outside the lip.
                    Field(focused = true, error = true)
                }
            }
        }
}
