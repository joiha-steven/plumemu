package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.AchievementCount
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A panel's rows fade into the card's edges instead of being hidden (design/rules.md › Lists that scroll). On a menu that scrolls, with the focus at the top:
 * a row at the card's bottom edge fades (its ink falls over the edge's last 12 dp, no hard edge between two pixel rows), the focused row is whole and drawn at
 * full strength, at the list's end the last row is whole, and a heading never stands alone above a row that is left out. Checked on the pixels.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PanelRowsFadeBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private var size by mutableStateOf(TextSize.Small)
    private var panel by mutableStateOf(OpenPanel(game, PanelScreen.Menu(sampleFacts())))
    private var isShown = false

    private fun show(
        open: OpenPanel,
        textSize: TextSize,
    ) {
        size = textSize
        panel = open
        if (!isShown) {
            isShown = true
            compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        }
        compose.waitForIdle()
        // The pill starts its glide from the first frame it is drawn in: draw one, then let it arrive.
        compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).captureToImage()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun list(): DpRect = compose.onNodeWithTag(GamePanelTags.ROWS, useUnmergedTree = true).getBoundsInRoot()

    private fun focusedKey() = panel.content.focusableRows.getOrNull(panel.focus)?.key

    private fun rowBounds(): List<Pair<String, DpRect>> =
        panel.content.items.mapNotNull { (it as? PanelItem.Row)?.row?.key }.mapNotNull { key ->
            val nodes = compose.onAllNodesWithTag(GamePanelTags.row(key), useUnmergedTree = true)
            if (nodes.fetchSemanticsNodes().isEmpty()) null else key to nodes[0].getUnclippedBoundsInRoot()
        }

    private class Shot(
        val image: android.graphics.Bitmap,
        val card: DpRect,
        val density: Float,
    ) {
        private fun luminance(x: Int, y: Int): Int {
            val c = image.getPixel(x, y)
            return (android.graphics.Color.red(c) * 3 + android.graphics.Color.green(c) * 6 + android.graphics.Color.blue(c)) / 10
        }

        /** Spread (0..255) of the luminance in the box (dp, root): flat card colour is near 0, text ink is far more. */
        fun spread(left: Dp, right: Dp, top: Dp, bottom: Dp): Int {
            val x0 = ((left - card.left).value * density).toInt().coerceIn(0, image.width - 1)
            val x1 = ((right - card.left).value * density).toInt().coerceIn(x0 + 1, image.width)
            val y0 = ((top - card.top).value * density).toInt().coerceIn(0, image.height - 1)
            val y1 = ((bottom - card.top).value * density).toInt().coerceIn(y0 + 1, image.height)
            var low = 255
            var high = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val l = luminance(x, y)
                low = minOf(low, l)
                high = maxOf(high, l)
            }
            return high - low
        }
    }

    private fun shot(): Shot {
        val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true)
        val box = card.getBoundsInRoot()
        val image = card.captureToImage().asAndroidBitmap()
        return Shot(image, box, image.width / (box.right - box.left).value)
    }

    /** The strongest ink a whole row clear of both bands and not focused has here: what a fully shown label spreads to. */
    private fun fullInk(shot: Shot): Int {
        val list = list()
        return rowBounds().filter { it.first != focusedKey() && it.second.top >= list.top + BAND && it.second.bottom <= list.bottom - BAND }
            .maxOfOrNull { (_, row) -> shot.spread(row.left + 16.dp, (row.left + row.right) / 2 - 20.dp, row.top + 4.dp, row.bottom - 12.dp) } ?: 0
    }

    /** The long menu (Fix box art, Sort, Remove from recent, Achievements): at Large and Largest it scrolls in the card. */
    private val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true, canRemoveFromRecent = true, achievementCount = AchievementCount(12, 40))))

    protected fun rowAtTheBottomFades() {
        val problems = mutableListOf<String>()
        var faded = 0
        for (each in listOf(TextSize.Small, TextSize.Large, TextSize.Largest)) {
            show(menu.copy(focus = 0), each)
            val list = list()
            val shot = shot()
            val full = fullInk(shot)
            val cut = rowBounds().firstOrNull { (key, row) -> key != focusedKey() && row.top < list.bottom - 0.5.dp && row.bottom > list.bottom + 0.5.dp } ?: continue
            if (full < MIN_FULL_INK) continue
            val label = "$each ${cut.first}"
            // The ink in strips 3 dp tall up the band: at most the share of a full label the ramp allows there (it is linear, from nothing at the edge), a little over for the text's edges.
            for (k in 0 until (BAND / STRIP).toInt()) {
                val bottom = list.bottom - STRIP * k
                val ink = shot.spread(list.left + 16.dp, (list.left + list.right) / 2 - 20.dp, bottom - STRIP, bottom)
                val allowed = full * ((k + 1) * STRIP.value / BAND.value) + SLACK
                if (ink > allowed) problems += "$label: ink $ink in the strip ${k * STRIP.value} dp from the edge, a ramp allows $allowed of a full $full"
            }
            val edge = shot.spread(list.left + 16.dp, (list.left + list.right) / 2 - 20.dp, list.bottom - 1.dp, list.bottom)
            if (edge > full / 5 + 8) problems += "$label: ink $edge in the last dp at the edge, of a full $full: a hard edge"
            val shown = (list.bottom - cut.second.top).value / (cut.second.bottom - cut.second.top).value
            val seen = shot.spread(cut.second.left + 16.dp, (cut.second.left + cut.second.right) / 2 - 20.dp, maxOf(cut.second.top, list.top), list.bottom - 2.dp)
            // Over half of the row shows, faded; under a third is left out (nothing of it). Between the two the row's text may be under half shown, which game/PanelListEnds.kt also leaves out.
            if (shown >= 1f / 2f + 0.05f) {
                if (seen > EDGE_INK) faded++ else problems += "$label: ${(shown * 100).toInt()}% of the row shows but it draws no ink ($seen)"
            } else if (shown < 1f / 3f - 0.05f && seen > EDGE_INK) {
                problems += "$label: ${(shown * 100).toInt()}% of the row shows, under a third, yet it draws (ink $seen)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("no text size showed a faded row at the card's bottom edge: the test proves nothing", faded > 0)
    }

    protected fun focusedRowWhole() {
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Large, TextSize.Largest)) {
            val order = menu.content.focusableRows.indices.toList()
            for (at in order) {
                show(menu.copy(focus = at), each)
                val list = list()
                val row = rowBounds().firstOrNull { it.first == focusedKey() }?.second
                if (row == null) {
                    problems += "$each focus $at: the focused row is not drawn"
                    continue
                }
                if (row.top < list.top - 0.5.dp || row.bottom > list.bottom + 0.5.dp) problems += "$each focus $at: the focused row $row is cut by the list $list"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun lastRowWhole() {
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Large, TextSize.Largest)) {
            val last = menu.content.focusableRows.lastIndex
            show(menu.copy(focus = last), each)
            val list = list()
            val bounds = rowBounds()
            val row = bounds.lastOrNull()?.second
            if (row == null) {
                problems += "$each: the last row is not drawn"
                continue
            }
            val key = bounds.last().first
            if (key != menu.content.items.mapNotNull { (it as? PanelItem.Row)?.row?.key }.last()) problems += "$each: the last row of the list is not composed"
            if (row.top < list.top - 0.5.dp || row.bottom > list.bottom + 0.5.dp) problems += "$each: the last row $row is cut by the list $list"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Play with has headings: whichever row the list leaves out at an edge, the heading above it is left out too, never drawn alone. */
    protected fun headingNotAlone() {
        val items = (1..8).map { EmulatorItem("e$it", "Emulator $it", "org.example.e$it", isLegalRisk = false, page = "https://example.org/e$it") }
        val open = OpenPanel(game, PanelScreen.PlayWith(sampleListing(current = items[0].id, installed = items.take(3), notInstalled = items.drop(3))))
        val problems = mutableListOf<String>()
        var headings = 0
        for (each in listOf(TextSize.Small, TextSize.Large, TextSize.Largest)) for (at in open.content.focusableRows.indices) {
            show(open.copy(focus = at), each)
            val list = list()
            val shot = shot()
            val all = panel.content.items
            for (i in all.indices) {
                if (all[i] !is PanelItem.Heading) continue
                val nodes = compose.onAllNodesWithTag(GamePanelTags.heading(i), useUnmergedTree = true)
                if (nodes.fetchSemanticsNodes().isEmpty()) continue
                val box = nodes[0].getUnclippedBoundsInRoot()
                val top = maxOf(box.top, list.top)
                val bottom = minOf(box.bottom, list.bottom) - 2.dp
                if (bottom <= top) continue
                headings++
                val ink = shot.spread(box.left + 16.dp, (box.left + box.right) / 2 - 20.dp, top, bottom)
                if (ink <= EDGE_INK) continue
                val next = all.drop(i + 1).firstNotNullOfOrNull { (it as? PanelItem.Row)?.row?.key } ?: continue
                val row = rowBounds().firstOrNull { it.first == next }?.second
                val rowInk = row?.let { shot.spread(it.left + 16.dp, (it.left + it.right) / 2 - 20.dp, maxOf(it.top, list.top), minOf(it.bottom, list.bottom) - 2.dp) } ?: 0
                if (rowInk <= LOW_INK) problems += "$each focus $at: heading $i draws (ink $ink) but its row $next does not (ink $rowInk)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("no heading was met in a window: the test proves nothing", headings > 0)
    }

    private companion object {
        val BAND = PlumDimens.listFadeBand
        val STRIP = 3.dp
        const val SETTLE_MS = 600L

        /** Spread a ramp of a full label may still show in its darkest strip, and at the edge itself. */
        const val SLACK = 24
        const val EDGE_INK = 24
        const val LOW_INK = 6

        /** A list whose whole rows spread less than this has no label to measure against. */
        const val MIN_FULL_INK = 60
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelRowsFade768Test : PanelRowsFadeBase() {
    @Test fun aRowAtTheBottomEdgeFades() = rowAtTheBottomFades()

    @Test fun theFocusedRowIsWhole() = focusedRowWhole()

    @Test fun atTheListsEndTheLastRowIsWhole() = lastRowWhole()

    @Test fun aHeadingNeverStandsAlone() = headingNotAlone()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PanelRowsFade576Test : PanelRowsFadeBase() {
    @Test fun aRowAtTheBottomEdgeFades() = rowAtTheBottomFades()

    @Test fun theFocusedRowIsWhole() = focusedRowWhole()

    @Test fun atTheListsEndTheLastRowIsWhole() = lastRowWhole()

    @Test fun aHeadingNeverStandsAlone() = headingNotAlone()
}
