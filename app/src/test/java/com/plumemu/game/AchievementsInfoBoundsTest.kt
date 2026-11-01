package com.plumemu.game

import android.content.Context
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.AchievementsState
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Info's achievement list measured in dp at both reference sizes and both presets: every left edge on the facts'
 * text line, badges `achievementBadgeSize` square, the rows `achievementRowGap` apart, the summary the same
 * gap above the first row, and nothing past the panel's right padding.
 */
abstract class AchievementsInfoBoundsBase(
    private val preset: ReferenceShots.Preset,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val set = AchievementsSamples.set()

    private fun show(state: AchievementsState) {
        val screen = PanelScreen.Info(AchievementsSamples.facts(state))
        val panel = OpenPanel(GameRef("1", "Super Metroid"), screen)
        compose.setContent { PlumTheme(preset.settings) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        compose.waitForIdle()
    }

    private val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    /** Layout bounds in dp, not clipped to the viewport: rows below the fold are measured whole. */
    private fun boundsOf(node: SemanticsNodeInteraction): DpRect {
        val found = node.fetchSemanticsNode()
        val at = found.positionInRoot
        return DpRect(
            left = (at.x / density).dp,
            top = (at.y / density).dp,
            right = ((at.x + found.size.width) / density).dp,
            bottom = ((at.y + found.size.height) / density).dp,
        )
    }

    private fun bounds(tag: String): DpRect = boundsOf(compose.onNodeWithTag(tag, useUnmergedTree = true))

    private fun near(
        message: String,
        expected: Float,
        actual: Float,
    ) = assertTrue("$message: expected $expected dp, was $actual dp", abs(expected - actual) <= TOLERANCE)

    @Test
    fun theListIsEvenAndOnTheFactsTextLine() {
        show(set)
        val textLine = bounds(GamePanelTags.fact(0)).left.value
        val section = bounds(GamePanelTags.ACHIEVEMENTS)
        near("the section starts on the facts' text line", textLine, section.left.value)
        near("the summary starts on the facts' text line", textLine, bounds(GamePanelTags.ACHIEVEMENTS_SUMMARY).left.value)
        val summary = bounds(GamePanelTags.ACHIEVEMENTS_SUMMARY)
        val rows = set.achievements.indices.map { bounds(GamePanelTags.achievement(it)) }
        near("summary to the first row", PlumDimens.achievementRowGap.value, rows.first().top.value - summary.bottom.value)
        rows.forEachIndexed { index, row ->
            near("row $index starts on the text line", textLine, row.left.value)
            assertTrue("row $index stays inside the panel's right padding (${row.right} <= ${section.right})", row.right.value <= section.right.value + TOLERANCE)
            assertTrue("row $index is at least a badge high", row.height.value >= PlumDimens.achievementBadgeSize.value - TOLERANCE)
            if (index > 0) near("gap between row ${index - 1} and $index", PlumDimens.achievementRowGap.value, row.top.value - rows[index - 1].bottom.value)
        }
    }

    @Test
    fun everyBadgeIsASquareOfTheTokenSizeOnItsRowsLeftEdgeAndTopEdge() {
        show(set)
        val badges = compose.onAllNodesWithTag(GamePanelTags.ACHIEVEMENT_BADGE, useUnmergedTree = true)
        assertEquals(set.achievements.size, badges.fetchSemanticsNodes().size)
        for (index in set.achievements.indices) {
            val row = bounds(GamePanelTags.achievement(index))
            val badge =
                boundsOf(compose.onNode(hasTestTag(GamePanelTags.ACHIEVEMENT_BADGE) and hasAnyAncestor(hasTestTag(GamePanelTags.achievement(index))), useUnmergedTree = true))
            near("badge $index width", PlumDimens.achievementBadgeSize.value, badge.width.value)
            near("badge $index height", PlumDimens.achievementBadgeSize.value, badge.height.value)
            near("badge $index left edge is its row's", row.left.value, badge.left.value)
            near("badge $index top edge is its row's", row.top.value, badge.top.value)
        }
    }

    @Test
    fun theQuietLineStartsOnTheFactsTextLine() {
        show(AchievementsState.NoSet)
        near("quiet line", bounds(GamePanelTags.fact(0)).left.value, bounds(GamePanelTags.ACHIEVEMENTS_LINE).left.value)
    }

    private companion object {
        const val TOLERANCE = 0.6f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AchievementsInfoBounds768GraphiteTest : AchievementsInfoBoundsBase(ReferenceShots.Preset.Graphite)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AchievementsInfoBounds768PaperTest : AchievementsInfoBoundsBase(ReferenceShots.Preset.Paper)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class AchievementsInfoBounds576GraphiteTest : AchievementsInfoBoundsBase(ReferenceShots.Preset.Graphite)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class AchievementsInfoBounds576PaperTest : AchievementsInfoBoundsBase(ReferenceShots.Preset.Paper)
