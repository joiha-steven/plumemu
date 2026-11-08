package com.plumemu.secondscreen

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.testing.SecondScreenText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.PlumFonts
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board's line boxes and columns at 1.5 px per dp, worked out as the browser does: Jost's ascent (1.07 em) and
 * descent (0.375 em) rounded each on its own, the top taking the smaller half of the leading.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-hdpi")
class SecondScreenBoardLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** 16 px Jost: ascent 17, descent 6. */
    private val small = TextStyle(fontFamily = PlumFonts.Jost, fontSize = (32f / 3).sp)

    /** 30 px Jost: ascent 32, descent 11. */
    private val big = TextStyle(fontFamily = PlumFonts.Jost, fontSize = 20.sp)

    private fun px(value: Dp) = (value.value * 1.5f).roundToInt()

    private fun boxOf(
        style: TextStyle,
        sharing: Boolean = false,
    ): Pair<Int, Int> {
        compose.setContent {
            BasicText("Played 248", style = style, modifier = Modifier.testTag(TEXT).boardLine(style, isJost = true, sharing = if (sharing) small.fontSize else null))
        }
        val node = compose.onNodeWithTag(TEXT)
        val bounds = node.getBoundsInRoot()
        return px(bounds.bottom - bounds.top) to px(node.getAlignmentLinePosition(FirstBaseline))
    }

    @Test
    fun aLineShorterThanTheFontsOwnIsTheBoardsBoxWithTheBaselineWhereTheBrowserPutsIt() {
        // line 20; leading 20 - 23 = -3, the top takes floor(-1.5) = -2: baseline 15.
        assertEquals(20 to 15, boxOf(small.onBoardLine(1.25f, isJost = true)))
    }

    @Test
    fun normalIsTheFontsOwnRoundedHeight() {
        assertEquals(23 to 17, boxOf(small.onBoardLine(EntryCardDimens.JOST_OWN_LINE, isJost = true)))
    }

    @Test
    fun aSmallerSpanOnTheLineCanReachBelowTheFiguresBox() {
        // line 34: the figure's baseline 32 - 5 = 27, the unit's 17 + 5 = 22 leaves 12 below: 27 + 12 = 39, as on the board.
        assertEquals(39 to 27, boxOf(big.onBoardLine(EntryCardDimens.LINE_BIG, isJost = true), sharing = true))
    }

    @Test
    fun anIntrinsicMeasureGivesTheSameHeightAsTheLayout() {
        val style = small.onBoardLine(1.25f, isJost = true)
        compose.setContent {
            Row(Modifier.height(IntrinsicSize.Min).testTag(ROW)) { BasicText("Played 248", style = style, modifier = Modifier.boardLine(style, isJost = true)) }
        }
        val bounds = compose.onNodeWithTag(ROW).getBoundsInRoot()
        assertEquals(20, px(bounds.bottom - bounds.top))
    }

    @Test
    fun offJostTheTextKeepsItsOwnBox() {
        val style = small.copy(fontFamily = null)
        compose.setContent {
            Row {
                BasicText("Played", style = style, modifier = Modifier.testTag(TEXT).boardLine(style.onBoardLine(1.25f, isJost = false), isJost = false))
                BasicText("Played", style = style, modifier = Modifier.testTag(PLAIN))
            }
        }
        val bounds = compose.onNodeWithTag(TEXT).getBoundsInRoot()
        val plain = compose.onNodeWithTag(PLAIN).getBoundsInRoot()
        assertEquals(px(plain.bottom - plain.top), px(bounds.bottom - bounds.top))
    }

    @Test
    fun evenColumnsPlaceEachEdgeAtItsOwnRoundedShare() {
        // 450 px and a 6 px gap make a pitch of 456 / 7 = 65.14 px; a weighted Row would round every column the same way.
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                EvenColumns(4.dp) { repeat(COLUMNS) { Box(Modifier.height(4.dp).testTag("$COLUMN$it")) } }
            }
        }
        val pitch = (450f + 6f) / COLUMNS
        repeat(COLUMNS) { index ->
            val bounds = compose.onNodeWithTag("$COLUMN$index").getBoundsInRoot()
            assertEquals("left of $index", (index * pitch).roundToInt(), px(bounds.left))
            assertEquals("right of $index", ((index + 1) * pitch - 6f).roundToInt(), px(bounds.right))
        }
    }

    @Test
    fun theDescriptionBreaksWhereTheScaleDrawsIt() {
        // The board (weight 400) puts "she" on the first line (729 of 735 px); each glyph rounded to a pixel took 742 and one line more. The one weight scale (500) is a little wider
        // than the board's 400, so "she" goes down: the breaks are re-measured at the scale and pinned (7 lines, animated glyph advances).
        compose.setContent {
            PlumTheme(SecondScreenText.Graphite) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(BOARD_GAME) } }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(GameCardTags.DESCRIPTION, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val lines = (0 until layout.lineCount).map { BOARD_DESCRIPTION.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
        assertEquals(lines.joinToString("|"), 7, layout.lineCount)
        assertTrue(BOARD_DESCRIPTION.substring(0, layout.getLineEnd(0)).trimEnd().endsWith("its distress call"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun theBatteryAndFileLinesSitOnTheBoardsBoxes() {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100))
        // The board: the battery line 623..646 px (Jost's own 23 px box, baseline 640), the file name's baseline at 666, 14 px down
        // its 18 px Menlo box after the 6 px gap. Both lines are now drawn larger than the board's (the small step, 11.33 sp, and 12 sp
        // in the mono face): Jost's own box is 24 px, baseline 18, and the file's baseline 47 px below the battery's top.
        compose.setContent {
            PlumTheme(SecondScreenText.Graphite) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(BOARD_GAME.copy(batteryPace = BatteryPace(minutesOnCharge = 135.0, percentPerHour = 30.0))) } }
        }
        val battery = placed(GameCardTags.BATTERY)
        val file = placed(GameCardTags.FILE)
        val top = battery.getBoundsInRoot().top
        assertEquals("battery box", 24, px(battery.getBoundsInRoot().bottom - top))
        assertEquals("battery baseline", 18, px(battery.getAlignmentLinePosition(FirstBaseline)))
        assertEquals("file baseline from the battery's top", 47, px(file.getBoundsInRoot().top - top + file.getAlignmentLinePosition(FirstBaseline)))
    }

    /** The line the card places: the card also measures each foot line once on its own, at the top, to see what fits. */
    private fun placed(tag: String): SemanticsNodeInteraction {
        val all = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
        val lowest = all.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.top }.index
        return all[lowest]
    }

    private companion object {
        const val TEXT = "text"
        const val ROW = "row"
        const val PLAIN = "plain"
        const val COLUMN = "column"
        const val COLUMNS = 7
    }
}
