package com.plumemu.game

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.AchievementCount
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PanelLazyList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The slab's shadow and glow are whole at a list's first and last row: read from the pixels beside the row's text, the shadow above a first row and below a last row match a middle row's.
 * A clip at the list's end shows there as a flat line level with the slab. Every panel, at the sizes where it fits and where it scrolls.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PanelSlabShadowBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private var theme by mutableStateOf(PlumThemeSettings.Graphite)
    private var panel by mutableStateOf(OpenPanel(game, PanelScreen.Menu(sampleFacts())))
    private var isShown = false

    private val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true, canRemoveFromRecent = true, achievementCount = AchievementCount(12, 40))))

    private fun show(open: OpenPanel, settings: PlumThemeSettings) {
        theme = settings
        panel = open
        if (!isShown) {
            isShown = true
            compose.setContent { PlumTheme(theme) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        // The pill starts its glide from the first frame it is drawn in: draw one, then let it arrive.
        compose.onRoot().captureToImage()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    /** The luminance beside the row's text at [REACH_DP] dp past the row's top ([above]) or bottom. */
    private fun profile(image: Bitmap, row: DpRect, above: Boolean): List<Int> {
        val d = compose.density.density
        val x = ((row.left.value + SIDE_DP) * d).toInt()
        return (1..REACH_DP).map { step ->
            val y = (((if (above) row.top.value - step else row.bottom.value + step) * d).toInt()).coerceIn(0, image.height - 1)
            val c = image.getPixel(x, y)
            (android.graphics.Color.red(c) * 3 + android.graphics.Color.green(c) * 6 + android.graphics.Color.blue(c)) / 10
        }
    }

    /**
     * How much two profiles' difference varies past the slab's own edge (the first three steps are the row itself). The card's shading may lift a whole profile by a few levels
     * (8 and 9 beside the first row of a short menu, from the same code on the Mac); a cut shadow is a flat line where the other decays, so the difference changes along the profile.
     * Read on the Mac: whole shadows spread 0 to 4 levels, a cut one (`shadowPastEnds = false`) 7 to 11.
     */
    private fun spread(a: List<Int>, b: List<Int>): Int {
        val d = a.indices.drop(3).map { a[it] - b[it] }
        return d.max() - d.min()
    }

    private fun focusedRow(open: OpenPanel): DpRect? {
        val key = open.content.focusableRows.getOrNull(open.focus)?.key ?: return null
        val nodes = compose.onAllNodesWithTag(GamePanelTags.row(key), useUnmergedTree = true)
        return if (nodes.fetchSemanticsNodes().isEmpty()) null else nodes[0].getUnclippedBoundsInRoot()
    }

    private fun profiles(open: OpenPanel): Pair<List<Int>, List<Int>> {
        show(open, theme)
        val row = checkNotNull(focusedRow(open)) { "no focused row" }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        return profile(image, row, above = true) to profile(image, row, above = false)
    }

    private fun check(settings: PlumThemeSettings, label: String) {
        val problems = mutableListOf<String>()
        var scrolled = 0
        for (size in listOf(TextSize.Small, TextSize.Largest)) {
            theme = settings.copy(textSize = size)
            val (above, below) = profiles(menu.copy(focus = menu.content.focusableRows.size / 2))
            val panels =
                listOf(
                    "menu" to menu,
                    "short menu" to OpenPanel(game, PanelScreen.Menu(sampleFacts())),
                    "sort" to OpenPanel(game, PanelScreen.SortMenu(SortChoices(com.plumemu.coredata.settings.SortGamesSetting.Name, hasYear = true, hasRating = true))),
                    "info" to OpenPanel(game, PanelScreen.Info(sampleFacts())),
                )
            for ((name, open) in panels) {
                val last = open.content.focusableRows.lastIndex
                val (first, _) = profiles(open.copy(focus = 0))
                if (compose.onAllNodesWithTag(GamePanelTags.FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) scrolled++
                val (_, end) = profiles(open.copy(focus = last))
                if (spread(first, above) > SPREAD_SLACK) problems += "$label $size $name first row, shadow above: $first, a middle row's $above"
                if (spread(end, below) > SPREAD_SLACK) problems += "$label $size $name last row, shadow below: $end, a middle row's $below"
            }
        }
        // Walking down a menu that scrolls: at no stop is the focused row's shadow cut by the card's edge or the title.
        val (above, below) = profiles(menu.copy(focus = menu.content.focusableRows.size / 2))
        for (at in menu.content.focusableRows.indices) {
            val (over, under) = profiles(menu.copy(focus = at))
            if (spread(over, above) > SPREAD_SLACK) problems += "$label Largest walk, row $at, above: $over, a middle row's $above"
            if (spread(under, below) > SPREAD_SLACK) problems += "$label Largest walk, row $at, below: $under, a middle row's $below"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("no panel scrolled here: the test proves nothing", scrolled > 0)
    }

    /** Settings' panel lists: the same reading on a list of plain rows, short of the screen's edges, with the list running on past its window at both ends. */
    private fun checkSettingsList(settings: PlumThemeSettings) {
        val focus = mutableIntStateOf(0)
        compose.setContent {
            PlumTheme(settings) {
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(top = 60.dp, start = 60.dp)) {
                    Box(Modifier.width(300.dp).height(220.dp)) {
                        PanelLazyList(focus.intValue, Modifier) {
                            items(List(12) { it }) { Box(Modifier.fillMaxWidth().height(PlumTheme.layout.rowHeight).testTag("row$it")) }
                        }
                    }
                }
            }
        }
        fun at(row: Int): Pair<List<Int>, List<Int>> {
            focus.intValue = row
            settle()
            val bounds = compose.onNodeWithTag("row$row", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            return profile(image, bounds, above = true) to profile(image, bounds, above = false)
        }
        val (above, below) = at(4)
        val (first, _) = at(0)
        val (_, last) = at(11)
        assertTrue("first row, shadow above: $first, a middle row's $above", first.indices.all { abs(first[it] - above[it]) <= SLACK })
        assertTrue("last row, shadow below: $last, a middle row's $below", last.indices.all { abs(last[it] - below[it]) <= PAGE_SLACK })
    }

    @Test fun settingsListGraphite() = checkSettingsList(PlumThemeSettings.Graphite)

    @Test fun settingsListPaper() = checkSettingsList(PlumThemeSettings.Paper)

    @Test fun graphite() = check(PlumThemeSettings.Graphite, "Graphite")

    @Test fun paper() = check(PlumThemeSettings.Paper, "Paper")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class PanelSlabShadow832Test : PanelSlabShadowBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PanelSlabShadow576Test : PanelSlabShadowBase()

private const val SETTLE_MS = 2000L

/** Clear of the bar at the slab's start and of the text after it. */
private const val SIDE_DP = 14f

private const val REACH_DP = 12

/** The shadow's tail fading before the list's room ends, and the card's own shading: a cut stands far above this. */
private const val SLACK = 9

/** Spread of a profile pair's difference (see [spread]): measured 0 to 4 whole and 7 to 11 cut, so 5 sits between with a level of room either side. */
private const val SPREAD_SLACK = 5

/** On the page, with no card: the drop's tail fades over the last 12 dp, a smooth shorter tail. A cut stands at 40 and more. */
private const val PAGE_SLACK = 16

