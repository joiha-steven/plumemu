package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A cover without box art is a text tile (seen on an AYN Thor: "Captain Toad: Treasure Tracker"
 * drew as "Treasur" and "e"). At the Thor's top screen, at every text size, grid size and font, in a
 * square, a wide and a tall cell: the title breaks only between words, a word wider than the tile ends in
 * an ellipsis, no line is wider than the tile's text area and the title stays inside the tile.
 * A word that does not fit steps the title down to the ramp's smallest tile size first; only a word that
 * still does not fit is cut, on a grapheme boundary, with "…" last in the title. Run at xhdpi (2.0) and at
 * the Thor's real density (367 dpi = 2.29375, 1920x1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
abstract class TextTileWordsBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var gridSize by mutableStateOf(GridSize.Small)
    private var title by mutableStateOf("")
    private var shape by mutableStateOf(MixedGridShape)
    private var ownShape: Float? = null
    private var layout: PlumLayout? = null
    private var smallestStyleSize = androidx.compose.ui.unit.TextUnit.Unspecified

    private fun show() {
        compose.setContent {
            PlumTheme(settings) {
                ProvideGridSize(gridSize) {
                    layout = PlumTheme.layout
                    smallestStyleSize = PlumTheme.type.smallTileTitle.fontSize
                    val sized = PlumTheme.layout
                    val cell = DpSize(sized.gridCoverWidth(shape), sized.gridRowHeight(shape)).let { each -> ownShape?.let { fitInside(it, each) } ?: each }
                    key(title, shape, settings, gridSize) {
                        Box(Modifier.size(cell).testTag(TILE), contentAlignment = Alignment.TopStart) {
                            CoverArt(title, null, cell, Alignment.BottomCenter, 6.dp, if (cell.width < cell.height) PlumDimens.coverPaddingPortrait else PlumDimens.coverPaddingLandscape, subtitle = "1998", titleStyle = PlumTheme.type.coverTitle(gridSize))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOfTitle(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val tag = listOf(HomeTags.WIDE_TILE_TITLE, HomeTags.PORTRAIT_TILE_TITLE).first { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun check(
        label: String,
        original: String,
    ): String? {
        val result = layoutOfTitle()
        // The text as laid out: a word that cannot fit is shortened to a prefix and "…" before it is laid out.
        // Without the zero width spaces the fit adds where Android would not break (after a hyphen).
        val raw = result.layoutInput.text.text
        val text = raw.replace("\u200B", "")
        fun plain(offset: Int) = raw.substring(0, offset).count { it != '\u200B' }
        val problems = mutableListOf<String>()
        val breaks = java.text.BreakIterator.getLineInstance(java.util.Locale.ROOT).apply { setText(text) }
        for (line in 0 until result.lineCount) {
            val end = plain(result.getLineEnd(line, visibleEnd = false))
            val cut = result.isLineEllipsized(line)
            if (!cut && end < text.length && !breaks.isBoundary(end) && !betweenPictographs(text, end)) problems += "line $line of '$original' ends mid-word at ${text.substring(0, end)}| (lines ${(0 until result.lineCount).map { raw.substring(result.getLineStart(it), result.getLineEnd(it, visibleEnd = false)) }}, laid out '$text', ${result.layoutInput.style.fontSize}, box ${result.size.width} px)"
            if (line == result.lineCount - 1 && !cut && end < text.length) problems += "last line stops before the text end without an ellipsis"
        }
        val cutAt = text.indexOf('\u2026')
        if (cutAt >= 0 && !result.isLineEllipsized(result.lineCount - 1)) {
            if (cutAt != text.length - 1) problems += "text follows the ellipsis in '$text'"
            if (result.layoutInput.style.fontSize != smallestStyleSize) problems += "'$text' is cut before the title stepped down to ${smallestStyleSize}"
            val characters = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply { setText(original) }
            if (!original.startsWith(text.substring(0, cutAt)) || !characters.isBoundary(cutAt)) problems += "'$text' is cut inside a grapheme of '$original'"
        }
        val tile = compose.onNodeWithTag(TILE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val tag = listOf(HomeTags.WIDE_TILE_TITLE, HomeTags.PORTRAIT_TILE_TITLE).first { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pad = PlumDimens.coverPaddingPortrait.coerceAtMost(PlumDimens.coverPaddingLandscape)
        if (node.left < tile.left + pad - 0.5.dp || node.right > tile.right - pad + 0.5.dp) problems += "title $node leaves the tile's text area across ($tile)"
        if (node.top < tile.top - 0.5.dp || node.bottom > tile.bottom + 0.5.dp) problems += "title $node leaves the tile ($tile)"
        for (line in 0 until result.lineCount) {
            if (result.getLineRight(line) > result.size.width + 1f) problems += "line $line is wider than the title's box"
        }
        return if (problems.isEmpty()) null else "$label: ${problems.joinToString("; ")}"
    }

    /** Android's line breaker, like ICU's rules, may break between two emoji or ideographs; the JDK's line iterator keeps an emoji run whole. */
    private fun betweenPictographs(
        text: String,
        end: Int,
    ): Boolean {
        val characters = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply { setText(text) }
        if (!characters.isBoundary(end) || end == 0 || end >= text.length) return false
        fun pictograph(codePoint: Int) = codePoint >= 0x1F000 || Character.isIdeographic(codePoint) || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
        return pictograph(text.codePointBefore(end)) && pictograph(text.codePointAt(end))
    }

    private fun everySize(vararg titles: String) {
        show()
        val failures = mutableListOf<String>()
        val shapes = listOf("square" to MixedGridShape, "wide" to CoverShape(1.4f), "tall" to CoverShape(0.7f))
        for (text in titles) {
            title = text
            for ((name, each) in shapes) {
                shape = each
                for (font in PlumFont.entries) {
                    for (size in TextSize.entries) {
                        for (grid in GridSize.entries) {
                            settings = PlumThemeSettings.Graphite.copy(font = font, textSize = size)
                            gridSize = grid
                            compose.waitForIdle()
                            check("$name $font $size $grid", text)?.let { failures += it }
                        }
                    }
                }
            }
        }
        assertTrue("${failures.size} problems, first ones:\n" + failures.take(12).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aWordNearlyAsWideAsTheTileIsNotBrokenInTwo() = everySize("Captain Toad: Treasure Tracker", "Treasure")

    /**
     * The device's finding: a Switch box (tall, about 0.62) in Favorites at Large text and the Small grid showed
     * "Captain / Toad: / Treas… / Tracker". The Small grid's title is already the ramp's smallest tile step, so
     * "Treasure" is cut there, and now nothing follows the "…"; at the Medium grid the title steps down from 13 to
     * 11 first. The checks of [check] hold in both.
     */
    @Test
    fun aSwitchShapedTileAtLargeTextCutsOnlyAtTheSmallestStepAndStopsThere() {
        ownShape = 0.62f
        show()
        title = "Captain Toad: Treasure Tracker"
        settings = PlumThemeSettings.Graphite.copy(textSize = TextSize.Large)
        for (grid in listOf(GridSize.Small, GridSize.Medium)) {
            gridSize = grid
            compose.waitForIdle()
            val text = layoutOfTitle().layoutInput.text.text.replace("\u200B", "")
            assertTrue("$grid: '$text' keeps no text after an ellipsis", !text.contains('\u2026') || text.indexOf('\u2026') == text.length - 1)
            check("switch $grid", title)?.let { fail(it) }
        }
    }

    @Test
    fun aWordWiderThanTheTileEndsInAnEllipsisAndNothingFollows() = everySize("Supercalifragilisticexpialidocious", "Super Supercalifragilisticexpialidocious Mario")

    @Test
    fun japaneseBreaksBetweenCharactersAndCutsWholeCharacters() = everySize("\u30b9\u30fc\u30d1\u30fc\u30de\u30ea\u30aa\u30d6\u30e9\u30b6\u30fc\u30ba\u30e9\u30f3\u30c9\u30c8\u30e9\u30c3\u30af")

    @Test
    fun aHyphenBreaksAfterItAndAnEmojiIsNeverSplit() = everySize("Spider-Man: Web of Shadows", "Spider-Man-Spider-Man-Spider-Man", "Party \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89")

    @Test
    fun aDecomposedAccentStaysWithItsLetter() = everySize("Cafe\u0301 Cafe\u0301Cafe\u0301Cafe\u0301Cafe\u0301Cafe\u0301Cafe\u0301Cafe\u0301Cafe\u0301", "Re\u0301sume\u0301resume\u0301resume\u0301resume\u0301")

    private companion object {
        const val TILE = "text_tile_under_test"
    }
}

/** The xhdpi reference density. */
@Config(qualifiers = "w837dp-h471dp-land-xhdpi")
class TextTileWordsXhdpiTest : TextTileWordsBase()

/** The Thor's top screen as it is: 1920x1080 px at 367 dpi, 837 x 471 dp. */
@Config(qualifiers = "w837dp-h471dp-land-367dpi")
class TextTileWordsThorDensityTest : TextTileWordsBase()
