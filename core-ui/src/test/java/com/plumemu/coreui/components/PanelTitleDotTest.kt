package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The title's dot sits after the last visible character, centred on that line, and never leaves the panel width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelTitleDotTest {
    @get:Rule
    val compose = createComposeRule()

    private val panelWidth = 220.dp

    /** Lays the title out in a [panelWidth] box and checks the dot against an independent layout of the same text. */
    private fun check(title: String, maxLines: Int, expectLines: (Int) -> Boolean) {
        var reference: TextLayoutResult? = null
        var density = 1f
        var lineHeight = 0f
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                density = LocalDensity.current.density
                lineHeight = with(LocalDensity.current) { PlumTheme.type.setupTitle(WidthClass.Narrow).lineHeight.toDp().value }
                val style = PlumTheme.type.setupTitle(com.plumemu.coreui.theme.WidthClass.Narrow)
                Box(Modifier.width(panelWidth).testTag("box")) {
                    PanelTitleWithDot(title, style, maxLines = maxLines, titleModifier = Modifier.testTag("title"))
                }
                // Same text in the room the title gets (the dot's width and gap held back).
                Box(Modifier.width(panelWidth - PlumDimens.panelTitleDot - PlumDimens.panelTitleDotGap)) {
                    BasicText(title, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, onTextLayout = { reference = it })
                }
            }
        }
        compose.waitForIdle()
        val ref = reference!!
        val last = ref.lineCount - 1
        assertTrue("lines ${ref.lineCount}", expectLines(ref.lineCount))
        val dot = compose.onNodeWithTag(PANEL_TITLE_DOT_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val box = compose.onNodeWithTag("box", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("dot after the last line", ref.getLineRight(last) / density + PlumDimens.panelTitleDotGap.value, dot.left.value - box.left.value, 0.6f)
        val mid = ref.getLineTop(last) / density + (lineHeight / 2f)
        assertEquals("dot centred on the last line", mid, (dot.top.value + dot.bottom.value) / 2f - box.top.value, 0.6f)
        assertTrue("dot inside the panel", dot.right.value <= box.right.value + 0.1f)
    }

    @Test
    fun aWrappedTitleHasTheDotAfterItsLastLine() = check("Animal Crossing: New Horizons Deluxe Edition", Int.MAX_VALUE) { it >= 2 }

    @Test
    fun aTitleCutWithAnEllipsisHasTheDotAfterTheCut() =
        check("The Legend of Zelda: A Link to the Past and Four Swords Collector's Edition Extra Long Title", 2) { it == 2 }

    @Test
    fun aShortTitleHasTheDotRightAfterIt() = check("Super Metroid", Int.MAX_VALUE) { it == 1 }
}
