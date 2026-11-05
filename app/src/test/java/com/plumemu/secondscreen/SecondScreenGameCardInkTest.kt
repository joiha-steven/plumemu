package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game card's title and description are set on the shared styles' own line, which clips nothing: at Largest text, in every
 * font, Vietnamese capitals with stacked marks ("\u1EAA \u1EB0 \u1EA8") and descenders drawn on that line leave no ink above or below the
 * line box (the description's box clips at its edges, so ink outside the line would be cut there). The same styles the card uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenGameCardInkTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var font by mutableStateOf(PlumFont.Jost)
    private var role by mutableStateOf(TypeRole.SecondTitleRoomy)
    private var size by mutableStateOf(SecondScreenGameCardDimens.titleSize)
    private val marks = "\u1EAA \u1EB0 \u1EA8 \u1EA4\u1EC0\u1ED4\u1ED6\u1EE2 gjpqy \u1EAA \u1EB0 \u1EA8"

    private val padding = 20.dp

    @Test
    fun theTallestMarksStayInsideTheLineAtTheLargestTextInEveryFont() {
        var padPx = 0
        var inkPx = 0
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) {
                padPx = with(LocalDensity.current) { padding.roundToPx() }
                inkPx = with(LocalDensity.current) { SecondScreenGameCardDimens.descriptionInkTop.roundToPx() }
                Box(Modifier.background(Color.Black).padding(padding).width(600.dp)) { BasicText(marks, style = cardStyle(role, size)) }
            }
        }
        val failures = mutableListOf<String>()
        for (eachFont in PlumFont.entries) {
            for ((eachRole, eachSize) in listOf(TypeRole.SecondFactsRoomy to SecondScreenGameCardDimens.descriptionSize)) {
                font = eachFont
                role = eachRole
                size = eachSize
                compose.waitForIdle()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val lit = { y: Int -> (0 until bitmap.width).count { x -> (pixels[y * bitmap.width + x] and 0xFF) > 0x40 } }
                val total = pixels.count { (it and 0xFF) > 0x40 }
                assertTrue("$eachFont $eachRole: something is drawn", total > 100)
                val above = (0 until padPx - inkPx).sumOf(lit)
                val below = (bitmap.height - padPx until bitmap.height).sumOf(lit)
                if (above + below != 0) failures += "$eachFont $eachRole: $above lit pixels above and $below below the line box"
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    /** The title is not clipped and sits under the card's top margin: its marks never reach the window's top row. */
    @Test
    fun theTitleMarksReachNoFurtherThanTheMargin() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) {
                androidx.compose.runtime.CompositionLocalProvider(com.plumemu.game.LocalBadgeImages provides CacheOnlyBadges()) {
                    SecondScreenCard(BOARD_GAME.copy(title = marks))
                }
            }
        }
        for (each in PlumFont.entries) {
            font = each
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val row = IntArray(bitmap.width)
            bitmap.getPixels(row, 0, bitmap.width, 0, 0, bitmap.width, 1)
            assertEquals("$each: ink on the window's top row", 0, row.count { (it and 0xFF) > 0x40 })
        }
    }

    /** What the card does: the role's shared style, its size and line scaled to [size]. */
    @androidx.compose.runtime.Composable
    private fun cardStyle(
        role: TypeRole,
        size: TextUnit,
    ): TextStyle {
        val base = PlumTheme.type[role]
        val k = size.value / role.sizeSp
        return base.copy(color = Color.White, fontSize = base.fontSize * k, lineHeight = base.lineHeight * k)
    }
}
