package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.textsize.DETAILS_ONE_LINE
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details column never runs past the right margin: a wide screen's column at every stop, a narrow screen's at the games stop
 * (where it takes the room the margin leaves, or is left out when that is under the cover's 140 dp) and at the third stop, at every text size and
 * the four reference widths. Its titles wrap to two lines then end in an ellipsis and its other texts are not cut. Box art on.
 */
abstract class HomeDetailsMarginBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private enum class Stop { Systems, Games, Details }

    private fun content(title: String) =
        HomeContent.of(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                SystemGames(
                    "ps",
                    "PlayStation",
                    listOf(
                        GameRowModel("1", title, "USA", systemAbbr = "PS1", details = GameDetails(1998, "Sony Computer Entertainment", "Platform", "1", "")),
                        GameRowModel("2", "Ape Escape", "USA", systemAbbr = "PS1"),
                    ),
                ),
            ),
            hasBoxArt = true,
            showApps = false,
        )

    private fun actions(stop: Stop): List<Action> {
        val games = listOf(Action.TapItem(Column.Systems, 2), Action.Enter, Action.TapItem(Column.Games, 0))
        return when (stop) {
            Stop.Systems -> games + Action.Back
            Stop.Games -> games
            Stop.Details -> games + Action.Move(Direction.Right)
        }
    }

    private fun problemsAt(
        title: String,
        allow: FitAllowance,
    ): List<String> {
        var size by mutableStateOf(TextSize.Largest)
        var stop by mutableStateOf(Stop.Games)
        val content = content(title)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = PlumFont.Sans)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) {
                    key(stop) { HomeAt(content, actions(stop)) }
                }
            }
        }
        val problems = mutableListOf<String>()
        for (eachStop in Stop.entries) {
            for (each in TextSize.entries) {
                stop = eachStop
                size = each
                compose.waitForIdle()
                val label = "${eachStop.name} at ${each.name}"
                val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val details = findTagged(root, HomeTags.DETAILS) ?: continue
                val margin = root.boundsInRoot.right - MARGIN_DP * compose.density.density
                val narrow = root.boundsInRoot.right / compose.density.density < WIDE_MIN_DP
                // With the systems focused a narrow screen's column is off screen: not a resting column.
                if (narrow && eachStop == Stop.Systems) continue
                if (details.boundsInRoot.right > margin + 1f) problems += "$label: the details end at ${details.boundsInRoot.right} px, the margin is at $margin px"
                problems += textProblems(details, allow).map { "$label: $it" }
                problems += cutTitle(details, label)
            }
        }
        return problems
    }

    /** The title may end in an ellipsis after two lines only; it is never wider than its node. */
    private fun cutTitle(
        details: SemanticsNode,
        label: String,
    ): List<String> {
        val title = findTagged(details, HomeTags.DETAILS_TITLE) ?: return emptyList()
        return if (title.boundsInRoot.right > details.boundsInRoot.right + 1f) listOf("$label: the title runs past the column") else emptyList()
    }

    @Test
    fun theColumnEndsAtTheMarginAtEveryStopAndSize() {
        val problems = problemsAt("Ape Escape", FitAllowance(oneLine = DETAILS_ONE_LINE))
        assertTrue("${problems.size} problems:\n" + problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aLongTitleWrapsThenEndsInAnEllipsisInsideTheMargin() {
        val problems = problemsAt("Final Fantasy Anthology - Final Fantasy V and Final Fantasy VI Special Edition", FitAllowance(cutToFit = HomeTags.DETAILS_TITLE, oneLine = DETAILS_ONE_LINE))
        assertTrue("${problems.size} problems:\n" + problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    /** The games stop at Larger (the Nova's default): the list beside a details column that ends at the margin (or is left out when there is too little room). */
    protected fun golden(name: String) {
        val content = content("Ape Escape")
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Larger)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(content, actions(Stop.Games)) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
    }

    private companion object {
        const val MARGIN_DP = 24f
        const val WIDE_MIN_DP = 768f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeDetailsMargin576Test : HomeDetailsMarginBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsMargin640Test : HomeDetailsMarginBase() {
    @Test
    fun theListGolden() = golden("home_details_list_640x480_larger_graphite")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsMargin768Test : HomeDetailsMarginBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-xhdpi")
class HomeDetailsMarginThorTest : HomeDetailsMarginBase()
