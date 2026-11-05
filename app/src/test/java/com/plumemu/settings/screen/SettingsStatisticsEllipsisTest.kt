package com.plumemu.settings.screen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Statistics, Most played: a long title is cut with "…" right after its last letter, never after a space. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsEllipsisTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Titles whose first word grows by a letter a row, so the cut lands at a different place in each. */
    private val titles = (0 until 5).map { "Z".repeat(it) + " Legend of Zelda A Link to the Past Four Swords Adventures and the Minish Cap Remastered Edition" }

    private fun shown(tag: String): String {
        val node =
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and (hasTestTag(tag) or hasParent(hasTestTag(tag))), useUnmergedTree = true)
                .fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        val last = layout.lineCount - 1
        val whole = layout.layoutInput.text.text
        return if (layout.isLineEllipsized(last)) whole.substring(0, layout.getLineEnd(last, visibleEnd = true)) + "\u2026" else whole
    }

    @Test
    fun aCutTitleNeverHasASpaceBeforeTheEllipsis() {
        val games = titles.mapIndexed { i, t -> TopGame(i + 1L, t, "Super Nintendo", "snes", 3_600L * (10 - i)) }
        val figures: PlayStatistics = StatisticsShots.played.copy(topGames = games)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(figures)) } } }
        compose.waitForIdle()
        compose.onNodeWithTag(StatisticsTags.MOST_PLAYED, useUnmergedTree = true).assertExists()
        val rows = games.indices.map { shown(StatisticsTags.gameTitle(it)) }
        assertTrue("some title is cut: $rows", rows.any { it.endsWith("\u2026") })
        for (row in rows) assertTrue("a space before the ellipsis in \"$row\"", !Regex("\\s\u2026$").containsMatchIn(row))
    }
}
