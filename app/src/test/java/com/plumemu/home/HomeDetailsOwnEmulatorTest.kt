package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.components.BAR_FADE_BOTTOM
import com.plumemu.game.EmulatorToStart
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details column names the emulator only for a game with its own emulator or core, chosen per game; and the wide column ends above the bottom bar fade.
 * Frame-ready states: [show] with `ownEmulator` false and true, at 768x432 and on the Thor (837x471), Large text, box art and the text-only column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class OwnEmulatorCases {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", description = "", rating = 17)

    /** One game focused in the wide column, with its play facts either with or without an emulator of its own. */
    protected fun show(
        ownEmulator: Boolean,
        boxArt: Boolean,
        size: TextSize = TextSize.Large,
        emulatorName: String = "RetroArch: Snes9x",
    ) {
        val content = LibraryLikeContent.of(hasBoxArt = boxArt, metroidDetails = if (boxArt) details else null)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(ownEmulator = ownEmulator, emulatorName = emulatorName)) {
                    HomeAt(content, LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun placedTexts(node: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) listOf(node) else emptyList()) + node.children.flatMap(::placedTexts)

    private fun shown(): List<SemanticsNode> = placedTexts(compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).fetchSemanticsNode())

    private fun words(): List<String> = shown().map { it.config[SemanticsProperties.Text].joinToString("") }

    @Test
    fun aGameWithoutItsOwnEmulatorHasNoEmulatorCell() {
        show(ownEmulator = false, boxArt = true)
        val texts = words()
        assertTrue("no Emulator label: $texts", "Emulator" !in texts)
        assertTrue("no emulator name: $texts", texts.none { it.contains("Snes9x") })
        assertTrue("the play cells stay: $texts", "Played" in texts && "Last played" in texts)
    }

    @Test
    fun aGameWithItsOwnEmulatorHasTheCell() {
        show(ownEmulator = true, boxArt = true)
        val texts = words()
        assertTrue("Emulator label: $texts", "Emulator" in texts)
        assertTrue("its name: $texts", "RetroArch: Snes9x" in texts)
    }

    @Test
    fun theTextColumnFollowsTheSameRule() {
        show(ownEmulator = false, boxArt = false)
        assertTrue("no Emulator row: ${words()}", "Emulator" !in words())
    }

    @Test
    fun theTextColumnShowsAnOwnEmulator() {
        show(ownEmulator = true, boxArt = false)
        assertTrue("Emulator row: ${words()}", "Emulator" in words())
    }

    @Test
    fun everyFactEndsAboveTheBottomFade() {
        show(ownEmulator = true, boxArt = true)
        val fadeTop = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val cells = shown()
        assertTrue("facts are placed: ${words()}", cells.size >= 6)
        assertTrue("the Emulator fact is among them (it is what the one-line layout keeps above the fade): ${words()}", "Emulator" in words())
        for (each in cells) assertTrue("'${each.config[SemanticsProperties.Text].joinToString("")}' ends at ${each.boundsInRoot.bottom}, the fade starts at $fadeTop", each.boundsInRoot.bottom <= fadeTop + 0.5f)
    }

    private fun assertFactsAboveFade(boxArt: Boolean) {
        show(ownEmulator = false, boxArt = boxArt)
        val fadeTop = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val cells = shown()
        assertTrue("facts are placed: ${words()}", cells.size >= 4)
        assertTrue("no Emulator fact: ${words()}", "Emulator" !in words())
        for (each in cells) assertTrue("'${each.config[SemanticsProperties.Text].joinToString("")}' ends at ${each.boundsInRoot.bottom}, the fade starts at $fadeTop", each.boundsInRoot.bottom <= fadeTop + 0.5f)
    }

    /** Large text, a game without its own emulator: dropping the Emulator row is what keeps the facts off the fade (768x432 and 837x471 too). */
    @Test
    fun withoutAnOwnEmulatorTheFactsEndAboveTheFadeAtLargeText() = assertFactsAboveFade(boxArt = true)

    @Test
    fun withoutAnOwnEmulatorTheTextColumnFactsEndAboveTheFadeAtLargeText() = assertFactsAboveFade(boxArt = false)

    /** Alone in its row the Emulator is one line, label and value side by side; a long name ends in an ellipsis inside the column, never wraps. */
    private fun assertLongNameOneLine(boxArt: Boolean) {
        run {
            show(ownEmulator = true, boxArt = boxArt, emulatorName = "RetroArch: A Very Long Core Name That Cannot Possibly Fit The Column")
            val cell = compose.onNodeWithTag(HomeTags.DETAILS_EMULATOR, useUnmergedTree = true).fetchSemanticsNode()
            val column = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val texts = placedTexts(cell)
            assertEquals("label and value, side by side: ${texts.map { it.boundsInRoot }}", 2, texts.size)
            assertTrue("same baseline row (tops within a few px)", kotlin.math.abs(texts[0].boundsInRoot.bottom - texts[1].boundsInRoot.bottom) < 6f)
            assertTrue("value is right of the label", texts[1].boundsInRoot.left >= texts[0].boundsInRoot.right)
            val layouts = mutableListOf<TextLayoutResult>()
            texts[1].config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals("one line", 1, layouts.first().lineCount)
            assertTrue("ellipsised", layouts.first().isLineEllipsized(0))
            assertTrue("inside the column: ${texts[1].boundsInRoot} vs $column", texts[1].boundsInRoot.right <= column.right + 0.5f)
        }
    }

    @Test
    fun aVeryLongEmulatorNameIsOneEllipsisedLineInsideTheColumn() = assertLongNameOneLine(boxArt = true)

    @Test
    fun aVeryLongEmulatorNameIsOneEllipsisedLineInsideTheTextColumn() {
        // The no-art column's table is rows of label and value already (one line each).
        show(ownEmulator = true, boxArt = false, emulatorName = "RetroArch: A Very Long Core Name That Cannot Possibly Fit The Column")
        val value = shown().first { it.config[SemanticsProperties.Text].joinToString("").startsWith("RetroArch") }
        val column = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        value.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("one line", 1, layouts.first().lineCount)
        assertTrue("ellipsised", layouts.first().isLineEllipsized(0))
        assertTrue("inside the column", value.boundsInRoot.right <= column.right + 0.5f)
    }

    @Test
    fun everyTextColumnFactEndsAboveTheBottomFade() {
        show(ownEmulator = true, boxArt = false)
        val fadeTop = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("facts are placed: ${words()}", shown().isNotEmpty() && "Emulator" in words())
        for (each in shown()) assertTrue("'${each.config[SemanticsProperties.Text].joinToString("")}' ends at ${each.boundsInRoot.bottom}, the fade starts at $fadeTop", each.boundsInRoot.bottom <= fadeTop + 0.5f)
    }

    /**
     * Picking a game's emulator starts the game; back from it the screen has new content (Recent changed) and the facts are read again: the Emulator
     * row appears without the focus moving. (A choice whose start fails shows its facts again on the next new content or visit.)
     */
    @Test
    fun theEmulatorRowAppearsWhenTheGameComesBackAfterAChoice() {
        val own = androidx.compose.runtime.mutableStateOf(false)
        val source = object : HomePlayFactsSource {
            override suspend fun read(gameId: Long) = GamePlay(2, 252, EmulatorToStart("RetroArch: Snes9x", isInstalled = true), hasOwnEmulator = own.value)
        }
        var content by androidx.compose.runtime.mutableStateOf(LibraryLikeContent.of())
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Small)) {
                CompositionLocalProvider(LocalHomePlayFacts provides source) { HomeAt(content, LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertTrue("no Emulator row at first: ${words()}", "Emulator" !in words())
        own.value = true
        content = LibraryLikeContent.of(favoriteMarked = false)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertTrue("the Emulator row is there: ${words()}", "Emulator" in words())
    }

    /** The publisher and year line beside a tall box (up to two lines, below a three-line title) is placed whole at the big text sizes. */
    private fun assertSubtitleWhole(size: TextSize, mustBeBeside: Boolean = false) {
        val details = GameDetails(year = 1990, publisher = "Konami", genre = "", players = "1", description = "", rating = null)
        val rows = listOf(GameRowModel("5", "Castlevania III: Dracula's Curse", "USA", scrapeKey = artKey("5"), systemAbbr = "NES", systemId = "nes", details = details))
        val board = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("nes", "Nintendo Entertainment System", rows, com.plumemu.coreui.theme.CoverShape(0.72f), code = "NES")), hasBoxArt = true, showApps = false)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(board, gridActions(POSTER_SNES_INDEX, 0)) }
            }
        }
        compose.waitForIdle()
        val box = compose.onNodeWithTag(HomeTags.DETAILS_COVER_BOX, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val isBeside = title.left >= box.right
        if (mustBeBeside) assertTrue("the title is beside the tall box at this size: $title vs $box", isBeside)
        // Beside the box only when the whole block fits; otherwise the title is under the box, as for a wide one.
        if (!isBeside) return
        val subtitle = compose.onNodeWithTag(HomeTags.DETAILS_SUBTITLE, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        subtitle.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = checkNotNull(layouts.firstOrNull()) { "the subtitle beside the box was dropped" }
        assertEquals("the subtitle node holds every line it has", layout.getLineBottom(layout.lineCount - 1), subtitle.size.height.toFloat(), 1f)
        assertTrue("the subtitle is not ellipsised", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
        val band = compose.onNodeWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the subtitle ends inside the cover band: ${subtitle.boundsInRoot} vs $band", subtitle.boundsInRoot.bottom <= band.bottom + 0.5f)
    }

    @Test
    fun theSubtitleBesideATallBoxIsWholeAtSmall() = assertSubtitleWhole(TextSize.Small, mustBeBeside = true)

    @Test
    fun theSubtitleBesideATallBoxIsWholeAtLarger() = assertSubtitleWhole(TextSize.Larger)

    @Test
    fun theSubtitleBesideATallBoxIsWholeAtLargest() = assertSubtitleWhole(TextSize.Largest)
}
