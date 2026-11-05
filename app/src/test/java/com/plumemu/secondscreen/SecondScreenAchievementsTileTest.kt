package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.LocalBadgeImages
import com.plumemu.graph.StoredAchievements
import com.plumemu.graph.StoredBadge
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game card's Achievements tile in the states its shots do not show: nothing unlocked, all unlocked with big numbers, a very long latest name. */
abstract class SecondScreenAchievementsTileBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } } }
    }

    private fun badges(first: String) = List(5) { StoredBadge(if (it == 0) first else "Badge $it", "https://example.test/badge/$it.png", 1_000L - it) }

    private val states =
        listOf(
            "none_unlocked" to StoredAchievements(earned = 0, total = 60, pointsEarned = 0, pointsTotal = 680),
            "all_unlocked_big" to StoredAchievements(earned = 1_234, total = 1_234, pointsEarned = 123_456, pointsTotal = 123_456, recent = badges("Done")),
            "long_latest" to BOARD_RA.copy(recent = badges("Defeated the final boss of the fortress without taking a single hit from anything")),
        )

    @Test
    fun theTileHoldsEveryStateWholeAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((name, ra) in states) {
                settings = PlumThemeSettings.Default.copy(textSize = textSize)
                content = BOARD_GAME.copy(ra = ra, achievements = ra.count)
                compose.waitForIdle()
                val at = "$name $size $textSize"
                val tile = compose.onNodeWithTag(GameCardTags.ACHIEVEMENTS).fetchSemanticsNode().boundsInRoot
                val figure = compose.onNodeWithTag(GameCardTags.ACHIEVEMENTS_FIGURE).fetchSemanticsNode()
                val layouts = mutableListOf<TextLayoutResult>()
                figure.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { assertTrue("$at: the figure is cut", it.multiParagraph.intrinsics.maxIntrinsicWidth <= figure.boundsInRoot.width + 1f && it.lineCount == 1) }
                for (tag in listOf(GameCardTags.ACHIEVEMENTS_FIGURE, GameCardTags.POINTS, GameCardTags.METER, GameCardTags.LATEST, GameCardTags.BADGE)) {
                    for (node in compose.onAllNodesWithTagSafe(tag)) {
                        val b = node.boundsInRoot
                        assertTrue("$at: $tag $b sticks out of the tile $tile", b.left >= tile.left - 1f && b.right <= tile.right + 1f && b.bottom <= tile.bottom + 1f)
                    }
                }
                val points = compose.onAllNodesWithTagSafe(GameCardTags.POINTS).firstOrNull()
                if (points != null) assertTrue("$at: the points overlap the figure", points.boundsInRoot.left >= figure.boundsInRoot.right - 1f)
            }
        }
    }

    private fun ComposeContentTestRule.onAllNodesWithTagSafe(tag: String) = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenAchievementsTileRoomyTest : SecondScreenAchievementsTileBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenAchievementsTileCompactTest : SecondScreenAchievementsTileBase(SecondSize.Compact)
