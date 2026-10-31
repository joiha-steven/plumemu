package com.plumemu.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.keyCapLook
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.toComposeColor
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
import kotlin.math.abs

/**
 * The hint key caps as drawn: in Super, Charcoal, Ivory, Banana and Pocket the A, B, X and Y caps are
 * filled with their printed letter's colour, also with "Confirm with B"; START and L1 R1, and every
 * cap in the other sets or in Custom colours, keep the `chip` ring. Read from the drawn pixels: the
 * top middle of each cap, inside its 1.5 dp ring (the ring is the fill's colour when filled).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintGlyphColorsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(PlumThemeSettings.Default)
    private var confirm by mutableStateOf(ConfirmButtonSetting.Right)

    private fun themeOf(set: ColorSet) = PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent, buttonSet = set)

    /** The bar's caps in order: confirm, back, X (search), Y (favorite), START, L1 R1. */
    private fun show() {
        compose.setContent {
            PlumTheme(theme) {
                val letters = ButtonLetters.of(confirm)
                CompositionLocalProvider(LocalButtonLetters provides letters) {
                    // The theme's background behind the caps, as on every screen.
                    Row(Modifier.background(PlumTheme.colors.bg)) {
                        Hint(stringResource(letters.confirm), "Play")
                        Hint(stringResource(letters.back), "Back")
                        Hint(stringResource(letters.search), "Search")
                        Hint(stringResource(letters.favorite), "Favorite")
                        Hint(stringResource(R.string.home_key_start), "Menu")
                        Hint(stringResource(R.string.home_key_l1_r1), "Switch")
                    }
                }
            }
        }
    }

    /** Each cap as drawn, in the bar's order. */
    private fun caps(): List<PixelMap> {
        compose.waitForIdle()
        val count = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG).fetchSemanticsNodes().size
        return (0 until count).map { compose.onAllNodesWithTag(HINT_KEY_CAP_TAG)[it].captureToImage().toPixelMap() }
    }

    /** The colour at the top middle of each cap, 1 px in: its ring. */
    private fun capEdges(): List<Color> = caps().map { it[it.width / 2, 1] }

    /** Inside the cap, between its ring and the letter: 5 px in from the left, half way down. */
    private fun PixelMap.inside(): Color = this[INSIDE_X, height / 2]

    /** Whether the letter is drawn in [ink]: a few pixels of the cap are that colour. */
    private fun PixelMap.hasInk(ink: Color): Boolean {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (near(ink, this[x, y])) found++
        return found >= INK_PIXELS
    }

    private fun near(
        want: Color,
        got: Color,
    ) = abs(want.red - got.red) < TOLERANCE && abs(want.green - got.green) < TOLERANCE && abs(want.blue - got.blue) < TOLERANCE

    private fun assertNear(
        what: String,
        want: Color,
        got: Color,
    ) {
        assertTrue("$what: want $want, got $got", near(want, got))
    }

    @Test
    fun theSetsWithGlyphColoursFillABXAndYByLetterAndLeaveTheOthers() {
        show()
        for (set in listOf(ColorSet.Super, ColorSet.Charcoal, ColorSet.Ivory, ColorSet.Banana, ColorSet.Pocket)) {
            theme = themeOf(set)
            val buttons = set.buttons!!
            val chip = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor).chip.toComposeColor()
            val edges = capEdges()
            assertEquals(6, edges.size)
            // The ring is the fill's colour, or `chip` where the fill melts into the background (under 3:1).
            // (Its rule has its own test, KeyCapLookTest; here the ring is what is drawn.)
            val composeColors = PlumComposeColors.from(PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))
            fun ring(letter: String) = keyCapLook(letter, buttons, composeColors).edge
            assertNear("$set A", ring("A"), edges[0])
            assertNear("$set B", ring("B"), edges[1])
            assertNear("$set X", ring("X"), edges[2])
            assertNear("$set Y", ring("Y"), edges[3])
            assertNear("$set START keeps its ring", chip, edges[4])
            assertNear("$set L1 R1 keeps its ring", chip, edges[5])
            // Filled inside, not only ringed, and the letter in its own colour on it.
            val drawn = caps()
            val letters = listOf(buttons.a, buttons.b, buttons.x, buttons.y)
            for (i in 0..3) {
                assertNear("$set cap $i is filled", letters[i].fill.toComposeColor(), drawn[i].inside())
                assertTrue("$set cap $i's letter is ${letters[i].letter}", drawn[i].hasInk(letters[i].letter.toComposeColor()))
            }
            val bg = set.backgroundColor.toComposeColor()
            assertNear("$set START is not filled", bg, drawn[4].inside())
        }
    }

    /** "Confirm with B": the confirm hint reads B and is B's colour; the back hint reads A and is A's. */
    @Test
    fun withConfirmOnBTheColoursFollowTheLetters() {
        confirm = ConfirmButtonSetting.Bottom
        theme = themeOf(ColorSet.Super)
        show()
        val buttons = ColorSet.Super.buttons!!
        val edges = capEdges()
        // B's yellow melts into Super's grey: its ring is chip, but the cap is still filled with it.
        assertNear("confirm (B) filled", buttons.b.fill.toComposeColor(), caps()[0].inside())
        assertNear("back (A)", buttons.a.fill.toComposeColor(), edges[1])
    }

    @Test
    fun otherSetsAndCustomColoursKeepTheUsualRing() {
        show()
        val custom = themeOf(ColorSet.Super).copy(text = TextMode.Light.color, buttonSet = null)
        for (look in ColorSet.entries.filter { it.buttons == null }.map(::themeOf) + custom) {
            theme = look
            val chip = PlumColors.derive(look.backgroundColor, look.textMode, look.accentColor).chip.toComposeColor()
            val colors = PlumColors.derive(look.backgroundColor, look.textMode, look.accentColor)
            caps().forEachIndexed { i, cap ->
                assertNear("$look cap $i ring", chip, cap[cap.width / 2, 1])
                assertNear("$look cap $i not filled", colors.bg.toComposeColor(), cap.inside())
                assertTrue("$look cap $i letter in text", cap.hasInk(colors.text.toComposeColor()))
            }
        }
    }

    /** Ivory's buttons stay on a background the person changed; a cap that melts into it gets the `chip` ring. */
    @Test
    fun aSetsButtonsSurviveAChangedBackgroundAndAMeltingCapGetsARing() {
        show()
        val banana = ColorSet.Banana.background
        theme = themeOf(ColorSet.Ivory).copy(background = banana, text = TextMode.Dark.color)
        val buttons = ColorSet.Ivory.buttons!!
        val colors = PlumColors.derive(theme.backgroundColor, theme.textMode, theme.accentColor)
        val edges = capEdges()
        assertNear("A keeps its red ring", buttons.a.fill.toComposeColor(), edges[0])
        assertNear("B (yellow on yellow) gets the chip ring", colors.chip.toComposeColor(), edges[1])
        assertNear("B is still filled with its yellow", buttons.b.fill.toComposeColor(), caps()[1].inside())
    }

    private companion object {
        // Antialiasing at the cap's rounded top; a fill and the ring differ by far more.
        const val TOLERANCE = 0.06f

        // xhdpi: the 1.5 dp ring is 3 px, the letter starts 13 px in.
        const val INSIDE_X = 5
        const val INK_PIXELS = 3
    }
}
