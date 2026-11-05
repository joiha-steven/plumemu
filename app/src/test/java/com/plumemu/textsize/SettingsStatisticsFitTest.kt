package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.FADE_BOTTOM_SUFFIX
import com.plumemu.settings.screen.FADE_TOP_SUFFIX
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.StatisticTile
import com.plumemu.settings.screen.StatisticsShots
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.StatisticsTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics' page at every text size, the way `TextSizeScreensTest` checks the other Settings sections (kept
 * apart: that file is at the length limit): at the six sizes in Plum Sans and at Large and Largest in Serif and Mono, no
 * text is ellipsized, clipped sideways, too short or on top of another (the page scrolls, so a line may be cut at its top
 * or bottom edge only), the focused game is whole and clear of the hint bar, and every tile's figure is whole.
 */
private const val PEEK_BELOW_DP = 640

private const val LONG_TITLE = "The Legend of Zelda: The Adventures of a Link to the Past and Other Very Long Stories Told Twice"

abstract class SettingsStatisticsFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** A game's title and a system's name keep to one line and end in an ellipsis when they do not fit (rows of one height); nothing else may. */
    private val names = (StatisticsShots.played.topGames.map { it.title } + StatisticsShots.played.topSystems.map { it.name } + LONG_TITLE).map { "'$it'" }
    private val nameCut = { problem: String -> names.any { "$it is ellipsized" in problem || "$it is wider than its node" in problem } }

    private fun check(
        name: String,
        ignoring: (String) -> Boolean = { false },
        peeks: Boolean = false,
        extra: (SemanticsNode, String, TextSize, PlumFont) -> List<String> = { _, _, _, _ -> emptyList() },
        screen: @Composable () -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { screen() } }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(setOf(StatisticsTags.PAGE, SettingsTags.SECTIONS), isLargest = each == TextSize.Largest, stripTags = if (peeks) setOf(StatisticsTags.PAGE) else emptySet())
            textProblems(root, allow).map { "$label: $it" }.filterNot(ignoring).mapTo(failures) { it }
            extra(root, label, each, eachFont).mapTo(failures) { it }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    /** Every tile that is drawn has its figure whole (no text cut by its width) and inside its tile. */
    private fun figuresWhole(
        root: SemanticsNode,
        label: String,
        textSize: TextSize,
        font: PlumFont,
        strict: Boolean,
    ): List<String> {
        val problems = mutableListOf<String>()
        var seen = 0
        for (tile in StatisticTile.entries) {
            val box = findTagged(root, StatisticsTags.tile(tile)) ?: continue
            val figure = findTagged(root, StatisticsTags.figure(tile))
            if (figure == null) {
                problems += "$label: the ${tile.name} tile has no figure"
                continue
            }
            // A figure scrolled out of the window (the page follows the cursor) has nothing to check.
            if (figure.boundsInRoot.width <= 0f) continue
            // Up to Large in Plum Sans a figure is one line; at the larger sizes it may take its second line (maxLines = 2), whole and not ellipsized.
            if (strict && font == PlumFont.Sans && textSize <= TextSize.Large) runCatching { assertNoTextCut(figure, "$label: ${tile.name} figure") }.exceptionOrNull()?.let { problems += it.message.orEmpty() }
            if (figure.size.width > box.size.width) problems += "$label: the ${tile.name} figure is wider than its tile (${figure.size.width} > ${box.size.width} px)"
            seen++
        }
        if (seen == 0) problems += "$label: no tile drawn"
        return problems
    }

    /** The focused game [index] is clear of the page's edge fades (a row under a fade reads as cut), at every size. */
    private fun clearOfFades(
        root: SemanticsNode,
        label: String,
        index: Int,
    ): List<String> {
        val game = findTagged(root, StatisticsTags.game(index)) ?: return listOf("$label: game $index is not drawn")
        val bottom = findTagged(root, StatisticsTags.PAGE + FADE_BOTTOM_SUFFIX)
        val top = findTagged(root, StatisticsTags.PAGE + FADE_TOP_SUFFIX)
        return listOfNotNull(
            bottom?.takeIf { game.boundsInRoot.bottom > it.boundsInRoot.top + 0.5f }?.let { "$label: game $index ends at ${game.boundsInRoot.bottom}, under the bottom fade from ${it.boundsInRoot.top}" },
            top?.takeIf { game.boundsInRoot.top < it.boundsInRoot.bottom - 0.5f }?.let { "$label: game $index starts at ${game.boundsInRoot.top}, under the top fade to ${it.boundsInRoot.bottom}" },
        )
    }

    private fun figures(strict: Boolean = true): (SemanticsNode, String, TextSize, PlumFont) -> List<String> = { root, label, size, font -> figuresWhole(root, label, size, font, strict) }

    private fun played(
        focusAt: Int,
        figures: PlayStatistics = StatisticsShots.played,
    ): @Composable () -> Unit = { StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(focusAt), content = StatisticsShots.contentOf(figures)) } }

    @Test
    fun theFirstGameAndTheTiles() =
        check(
            "statistics_first_game",
            ignoring = nameCut,
            extra = { root, label, size, font -> figures()(root, label, size, font) + clearOfFades(root, label, 0) },
            screen = played(0),
        )

    /** The cursor on the last game: the page has followed it, it is whole and its bottom is above the hint bar. */
    @Test
    fun theLastGameIsWholeAndClearsTheHintBar() =
        check(
            "statistics_last_game",
            ignoring = nameCut,
            extra = { root, label, _, _ ->
                val game = findTagged(root, StatisticsTags.game(4))
                val hint = findTagged(root, SettingsTags.HINT_BACK)
                when {
                    game == null || hint == null -> listOf("$label: the last game or the hint bar is not drawn")
                    notFullyShown(game, root) != null -> listOf("$label: the last game ${notFullyShown(game, root)}")
                    game.boundsInRoot.bottom > hint.boundsInRoot.top + 0.5f -> listOf("$label: the last game ends at ${game.boundsInRoot.bottom}, the hint bar starts at ${hint.boundsInRoot.top}")
                    else -> clearOfFades(root, label, 4)
                }
            },
            screen = played(4),
        )

    @Test
    fun aVeryLongTitleEndsInAnEllipsisOnItsLine() {
        val figures = StatisticsShots.played.copy(topGames = StatisticsShots.played.topGames.map { it.copy(title = LONG_TITLE) })
        // A 96-letter title ends in "..." on its one line; anything else wrong still fails.
        check("statistics_long_title", ignoring = nameCut, screen = played(1, figures))
    }

    /** A five-digit hour total wraps to its second line even at Medium in a four-tile row: whole and inside its tile at every size. */
    @Test
    fun aHugeTotalStaysInItsTile() {
        val figures = StatisticsShots.played.copy(totalSeconds = 12_345 * 3_600L + 59 * 60)
        check("statistics_huge_total", ignoring = nameCut, extra = figures(strict = false), screen = played(0, figures))
    }

    @Test
    fun nothingPlayed() =
        check("statistics_empty", extra = figures()) { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed)) }

    @Test
    fun anEmptyLibrary() = check("statistics_no_games") { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(PlayStatistics())) }

    @Test
    fun unreadable() = check("statistics_unreadable") { SettingsAt(StatisticsShots.focusAt(0), content = SettingsContent(statistics = StatisticsState.Failed)) }

    /**
     * The failed page's switch, explanation and footer are all whole, and clear of the hint bar, at every size but Largest in Serif
     * and Mono, or on a window under 432 dp high, where the page scrolls instead (reached by Down: `SettingsStatisticsFailedInputTest`).
     */
    @Test
    fun unreadableShowsTheSwitchTheExplanationAndTheFooterWhole() =
        check(
            "statistics_unreadable_whole",
            extra = { root, label, size, font ->
                val hint = findTagged(root, SettingsTags.HINT_BACK)
                if (size == TextSize.Largest && (font != PlumFont.Sans || heightDp < 432)) return@check emptyList()
                listOf(StatisticsTags.EXACT_PLAY_TIME, StatisticsTags.EXACT_PLAY_TIME_BODY, StatisticsTags.FOOTER).mapNotNull { tag ->
                    val node = findTagged(root, tag)
                    when {
                        node == null || hint == null -> "$label: $tag or the hint bar is not drawn"
                        notFullyShown(node, root) != null -> "$label: $tag ${notFullyShown(node, root)}"
                        node.boundsInRoot.bottom > hint.boundsInRoot.top + 0.5f -> "$label: $tag ends at ${node.boundsInRoot.bottom}, the hint bar starts at ${hint.boundsInRoot.top}"
                        else -> null
                    }
                }
            },
        ) { SettingsAt(StatisticsShots.focusAt(0), content = SettingsContent(statistics = StatisticsState.Failed)) }

    /** The sections column focused: only the tiles, two to a row, and their figures whole. */
    @Test
    fun thePreview() =
        // Narrow screens only peek at the preview: the strip runs out of the right edge (as every section's options do).
        check("statistics_preview", peeks = widthDp < PEEK_BELOW_DP, extra = if (widthDp < PEEK_BELOW_DP) { _, _, _, _ -> emptyList() } else figures()) {
            StatisticsShots.WithCovers {
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Statistics), content = StatisticsShots.contentOf(StatisticsShots.played))
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsFit768Test : SettingsStatisticsFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsStatisticsFit837Test : SettingsStatisticsFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsStatisticsFit667Test : SettingsStatisticsFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsFit576Test : SettingsStatisticsFitBase(576, 432)
