package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A focused field row shows its focus once, on the field's collar: the label column (its top-left corner, where a focus pill
 * would be filled) stays the page's own colour, on a dark and a light theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FieldRowFocusOnceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun cornerPixelDistance(settings: PlumThemeSettings): Int {
        var bg = Color.Unspecified
        compose.setContent {
            PlumTheme(settings) {
                bg = PlumTheme.colors.bg
                Box(Modifier.background(PlumTheme.colors.bg).padding(8.dp).testTag("shot")) {
                    FieldRowFrame(look = OptionRowLook.Focused, label = "Name", labelWidth = 80.dp) { BasicText("x", style = it) }
                }
            }
        }
        val image = compose.onNodeWithTag("shot").captureToImage().toPixelMap()
        // The label column's top-left corner: 8 dp in for the padding (2 px a dp), a few pixels into the row.
        val pixel = image[8 * 2 + 3, 8 * 2 + 3]
        return listOf(abs(pixel.red - bg.red), abs(pixel.green - bg.green), abs(pixel.blue - bg.blue)).maxOf { (it * 255).toInt() }
    }

    @Test
    fun darkThemeRowHasNoFill() = assertEquals(0, cornerPixelDistance(PlumThemeSettings.Graphite))

    @Test
    fun lightThemeRowHasNoFill() = assertEquals(0, cornerPixelDistance(PlumThemeSettings.Paper))
}
