package com.plumemu.secondscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.random.Random
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** `fittedTitle` on its own: the edge cases of cutting a title before its star. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenTitleFitTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val ellipsis = "…"
    private val maxLines = 3
    private lateinit var measurer: TextMeasurer
    private lateinit var titleStyle: TextStyle
    private lateinit var starStyle: TextStyle
    private var gap: TextUnit = TextUnit.Unspecified

    private fun host(density: Float = 2f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Medium)) {
                    measurer = rememberTextMeasurer()
                    titleStyle = PlumTheme.type[TypeRole.SecondTitleRoomy].copy(color = Color.White)
                    starStyle = PlumTheme.type[TypeRole.SecondStarRoomy]
                    gap = with(LocalDensity.current) { SecondScreenDimens.starGap.toSp() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fit(
        title: String,
        isFavorite: Boolean,
        widthPx: Int,
        lines: Int = maxLines,
    ): AnnotatedString = fittedTitle(measurer, title, isFavorite, titleStyle, starStyle, Color.White, lines, widthPx, gap)

    private fun linesOf(
        shown: AnnotatedString,
        isFavorite: Boolean,
        widthPx: Int,
    ): Int = linesOf(measurer, shown, isFavorite, titleStyle, widthPx, gap)

    @Test
    fun aTitleThatFitsWithoutTheStarButNotWithItIsCutAndKeepsTheStar() {
        host()
        // Try widths and growing titles (words of changing length, so the last line's slack changes)
        // until one fits alone but needs a cut once the star is added.
        var found: Pair<String, Int>? = null
        for (width in 600..900 step 7) {
            val text =
                (1..300).map { count -> (0 until count).joinToString(" ") { "a".repeat(1 + (it * 7) % 5) } }.firstOrNull { text ->
                    fit(text, false, width).text == text && !fit(text, true, width).text.startsWith(text)
                }
            if (text != null) {
                found = text to width
                break
            }
        }
        assertTrue("a title that fits plain and needs a cut with the star exists", found != null)
        val (probe, width) = found!!
        val starred = fit(probe, true, width)
        assertTrue("cut before the star: '${starred.text}'", starred.text.dropLast(2).endsWith(ellipsis))
        assertTrue(starred.text.endsWith(FAVORITE_STAR))
        assertTrue(linesOf(starred, true, width) <= maxLines)
    }

    @Test
    fun oneVeryLongWordIsCutAndKeepsTheStar() {
        host()
        val shown = fit("W".repeat(300), true, 900)
        assertTrue(shown.text.dropLast(2).endsWith(ellipsis))
        assertTrue(shown.text.endsWith(FAVORITE_STAR))
        assertTrue(linesOf(shown, true, 900) <= maxLines)
    }

    @Test
    fun anEmptyTitleWithAStarIsJustTheStar() {
        host()
        val shown = fit("", true, 900)
        assertEquals(" $FAVORITE_STAR", shown.text)
        assertEquals(1, linesOf(shown, true, 900))
    }

    @Test
    fun aLongTitleWithoutAStarIsCutWithAnEllipsisAndHasNoStar() {
        host()
        val shown = fit(LONG_TITLE + LONG_TITLE, false, 900)
        assertTrue(shown.text.endsWith(ellipsis))
        assertFalse(shown.text.contains(FAVORITE_STAR))
        assertTrue(linesOf(shown, false, 900) <= maxLines)
    }

    /** Built from code points: the repo's sources hold no non-English characters. */
    private fun text(vararg codePoints: Int): String = String(codePoints, 0, codePoints.size)

    @Test
    fun aCutNeverSplitsASurrogatePairOrAnAccent() {
        host()
        for (unit in listOf(text(0x20BB7), text(0x65, 0x301), text(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467))) {
            for (favourite in listOf(false, true)) {
                val shown = fit(unit.repeat(150), favourite, 900)
                val kept = shown.text.substringBefore(ellipsis)
                assertTrue("a cut was needed for '$unit'", shown.text.contains(ellipsis))
                assertTrue("whole units only for '$unit': ${kept.length} chars", kept.isNotEmpty() && kept.length % unit.length == 0)
            }
        }
    }

    @Test
    fun aJustFittingTitleStillFitsWhenTheLayoutRoundsTheWidthAtAnAwkwardDensity() {
        // d = 2.0125: 40 dp margins are 80.5 px each; the layout gives the title the width it really has.
        host(2.0125f)
        val density = Density(2.0125f)
        val margin = with(density) { 40.dp.roundToPx() }
        val widthPx = with(density) { 538.dp.roundToPx() } - 2 * margin
        for (extra in 0..3) {
            val shown = fit(LONG_TITLE, true, widthPx + extra)
            assertTrue("$extra px wider: ${linesOf(shown, true, widthPx + extra)} lines", linesOf(shown, true, widthPx + extra) <= maxLines)
        }
    }

    @Test
    fun theRealCardAtAnAwkwardDensityKeepsAJustFittingTitleOnItsLines() {
        // d = 2.0125: the 40 dp margins are 80.5 px, drawn as 81. A title that fits 3 lines one pixel wider than the real
        // width (what dp arithmetic gave) but not at the real width must still be cut to fit what is drawn.
        val density = Density(2.0125f)
        val cardWidthPx = with(density) { 538.dp.roundToPx() }
        val realWidth = cardWidthPx - 2 * with(density) { 40.dp.roundToPx() }
        var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Medium)) {
                    measurer = rememberTextMeasurer()
                    titleStyle = PlumTheme.type[TypeRole.SecondTitleRoomy].copy(color = Color.White)
                    starStyle = PlumTheme.type[TypeRole.SecondStarRoomy]
                    gap = with(LocalDensity.current) { SecondScreenDimens.starGap.toSp() }
                    Box(Modifier.requiredSize(538.dp, 468.dp)) { SecondScreenCard(content) }
                }
            }
        }
        compose.waitForIdle()
        val random = Random(7)
        val letters = "abdefghmnoRWiltk"
        var found: String? = null
        search@ for (attempt in 0 until 3000) {
            val words = List(40) { "".padEnd(1 + random.nextInt(9), letters[random.nextInt(letters.length)]) }
            for (count in 10..40) {
                val text = words.take(count).joinToString(" ")
                val wider = fit(text, true, realWidth + 1)
                if (wider.text.startsWith(text) && linesOf(wider, true, realWidth) > maxLines) {
                    found = text
                    break@search
                }
            }
        }
        assertTrue("a title that fits one pixel wider than the real width but not at it exists", found != null)
        content = SecondScreenContent.Game(found!!, "Super Nintendo", "USA", isFavorite = true)
        compose.waitForIdle()
        val node = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertTrue("the title is drawn on at most $maxLines lines, not ${layout.lineCount}", layout.lineCount <= maxLines)
        assertFalse("the drawn title overflows its lines (the star line is clipped)", layout.hasVisualOverflow)
        assertTrue(node.config[SemanticsProperties.Text].joinToString("").endsWith(FAVORITE_STAR))
    }
}
