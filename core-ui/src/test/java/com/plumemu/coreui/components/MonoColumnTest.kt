package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A games list's year and region columns hold their text on one line at every text size, with at most 2 dp to spare. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MonoColumnTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aColumnHoldsItsTextAndLittleMore() {
        val problems = mutableListOf<String>()
        compose.setContent {
            for (size in TextSize.entries) {
                PlumTheme(PlumThemeSettings(textSize = size)) {
                    for (widest in listOf("", "USA", "USA,EUR")) Check(size.name, widest, problems)
                }
            }
        }
        compose.waitForIdle()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Composable
    private fun Check(
        size: String,
        widest: String,
        problems: MutableList<String>,
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val type = PlumTheme.type
        val columns = rememberRowColumns(widest)
        for ((style, text, column) in listOf(Triple(type.rowYear, "1994", columns.year), Triple(type.tag, widest.ifEmpty { "JPN" }, columns.region))) {
            val columnPx = with(density) { column.toPx() }
            val width = measurer.measure(text, style, maxLines = 1).size.width
            if (width > columnPx) problems += "$size '$text': text $width px over the column $columnPx px"
            if (columnPx - width > 2f * density.density) problems += "$size '$text': column $columnPx px holds $width px"
        }
    }
}
