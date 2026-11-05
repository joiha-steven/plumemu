package com.plumemu.textsize

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.battery.BatteryLineSample
import com.plumemu.battery.batteryLineCut
import com.plumemu.battery.PickingBatteryTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The picking line in the top screen's details column, under the facts table in the table's
 * value role: the text-only column ([hasBoxArt] false) and the narrow stop at 576. The wide cover column (768, the Thor)
 * has no line: the time on a charge is the BATTERY cell of the details block's second row (`inBlock`): present with a measured
 * game and absent without, never cut, inside the column, the column whole. At every text size in every font, with the shortest to the longest
 * wording ([BATTERY_LINE_SAMPLES]), nothing is ellipsized, cut ([batteryLineCut]), clipped or on top of
 * another, and the line lies inside the column, below the emulator row. Where the column runs out of
 * height the line is never cut or partly shown: the words "on this charge" give way first
 * ([HOME_WORDS_GIVE_WAY]), then it is left out ([HOME_LINE_LEFT_OUT]), where the table has no room either
 * and also, in the wide cover column at the large sizes, under a whole table. A game never measured has
 * no line, and nothing else of the column differs.
 */
abstract class HomeBatteryLineFitBase(
    /** The place's name in [HOME_WORDS_GIVE_WAY] and [HOME_LINE_LEFT_OUT]. */
    private val place: String,
    private val hasBoxArt: Boolean,
    /** The wide cover column: the time on a charge is the block's BATTERY cell, there is no line under it. */
    private val inBlock: Boolean = false,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Largest)
    private var font by mutableStateOf(PlumFont.Sans)

    @Before
    fun batteryIs60Percent() = battery(60)

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun battery(level: Int) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    private fun check(sample: BatteryLineSample?) {
        val pace = sample?.pace
        sample?.let { battery(it.level) }
        val facts = FakePlayFacts(pace = pace)
        val content = LibraryLikeContent.of(hasBoxArt = hasBoxArt)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                CompositionLocalProvider(LocalHomePlayFacts provides facts) {
                    // The narrow screens read the details at their third stop (the peek before it shows the cover alone).
                    val toDetailsStop = if (hasBoxArt && PlumTheme.layout.widthClass == WidthClass.Narrow) listOf(Action.Move(Direction.Right)) else emptyList()
                    HomeAt(content, LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID) + toDetailsStop)
                }
            }
        }
        val combinations = PlumFont.entries.flatMap { eachFont -> TextSize.entries.map { eachFont to it } }
        val failures = mutableListOf<String>()
        val gaveWay = mutableSetOf<String>()
        val leftOut = mutableSetOf<String>()
        val cellLeftOut = mutableSetOf<String>()
        val tableCut = mutableSetOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val label = "${each.name} ${eachFont.name} ${sample?.label}"
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val details = findTagged(root, HomeTags.DETAILS) ?: run { failures += "$label: no details column"; null } ?: continue
            val line = findTagged(details, PickingBatteryTags.LINE)
            if (inBlock) {
                if (line != null) failures += "$label: the old line is still under the block"
                val cell = findTagged(details, HomeTags.DETAILS_BATTERY)
                if (pace == null) {
                    if (cell != null) failures += "$label: a battery cell for a game never measured"
                } else if (cell == null) {
                    failures += "$label: no battery cell"
                } else {
                    // A cell the box has no room for is measured but not placed: its place is what counts, not that the node exists.
                    val box = findTagged(details, HomeTags.DETAILS_FACTS)
                    if (box == null) {
                        failures += "$label: no facts box"
                        continue
                    }
                    if (notShownInBox(cell, box) != null) {
                        cellLeftOut += "${each.name} ${eachFont.name}"
                        continue
                    }
                    val shown = findAllTexts(cell)
                    if (!shown.replace("\u00A0", " ").let { it.contains(" h") || it.contains(" min") }) failures += "$label: the cell has no time: '$shown'"
                    if (cell.boundsInRoot.left < details.boundsInRoot.left - 1f || cell.boundsInRoot.right > details.boundsInRoot.right + 1f) failures += "$label: the cell leaves the column"
                    if (sample == BATTERY_LINE_SAMPLES[0] && each == TextSize.Small && eachFont == PlumFont.Sans && "2 h" !in shown) failures += "$label: the cell does not say 2 h: '$shown'"
                }
                // The emulator's name ends in an ellipsis before its cell leaves (the box's floors); on the Thor at Largest in Mono the box has no room for the whole name.
                val longName = if (place == "cover Thor" && each == TextSize.Largest && eachFont == PlumFont.Mono) setOf("RetroArch: Snes9x") else emptySet()
                textProblems(details, FitAllowance(ellipsisTexts = longName)).mapTo(failures) { "$label: $it" }
                notFullyShown(details, root)?.let { failures += "$label: the column $it" }
                continue
            }
            if (pace == null) {
                if (line != null) failures += "$label: a line for a game never measured"
                continue
            }
            val table = findTagged(details, HomeTags.DETAILS_FACTS) ?: run { failures += "$label: no facts table"; null } ?: continue
            val at = "${each.name} ${eachFont.name}"
            // The table's own rows cut by the column's height or width: the table's, before the line (it is measured first).
            val tableProblems = textProblems(table, FitAllowance())
            if (tableProblems.isNotEmpty() || notFullyShown(table, root) != null) tableCut += at
            textProblems(details, FitAllowance()).filterNot { it in tableProblems }.mapTo(failures) { "$label: $it" }
            notFullyShown(details, root)?.let { failures += "$label: the column $it" }
            if (line == null) {
                // Not even the short form fits the height left: left out, never cut ([HOME_LINE_LEFT_OUT]).
                leftOut += at
                continue
            }
            if (line.boundsInRoot.top < table.boundsInRoot.bottom) failures += "$label: the line is not under the table"
            if (line.boundsInRoot.left < table.boundsInRoot.left - 1f || line.boundsInRoot.right > details.boundsInRoot.right + 1f) failures += "$label: the line leaves the column"
            val text = findTagged(line, PickingBatteryTags.TEXT)
            if (text == null) { failures += "$label: no text"; continue }
            batteryLineCut(text)?.let { failures += "$label: $it" }
            if ("on this charge" !in text.config[SemanticsProperties.Text].joinToString("")) gaveWay += at
            if (sample == BATTERY_LINE_SAMPLES[0] && each == TextSize.Small && eachFont == PlumFont.Sans) {
                assertEquals("2 h on this charge · 30%\u00A0an\u00A0hour", text.config[SemanticsProperties.Text].joinToString(""))
            }
            notFullyShown(line, root)?.let { failures += "$label: the line $it" }
            if (line.boundsInRoot.bottom > details.boundsInRoot.bottom + 1f) failures += "$label: the line runs below the column"
        }
        if (inBlock && pace != null) {
            val measuredCellLeftOut = HOME_BATTERY_CELL_LEFT_OUT[place].orEmpty()
            if (cellLeftOut != measuredCellLeftOut) failures += "the battery cell was left out at $cellLeftOut, measured at $measuredCellLeftOut"
            // The drop policy runs only when the box really cannot hold the cells: never at Small or Medium.
            if (cellLeftOut.any { it.startsWith("Small") || it.startsWith("Medium") }) failures += "the battery cell was left out at Small or Medium: $cellLeftOut"
        }
        if (sample != null) {
            val measuredGiveWay = HOME_WORDS_GIVE_WAY[place to sample.label].orEmpty()
            if (gaveWay != measuredGiveWay) failures += "the words gave way at $gaveWay, measured at $measuredGiveWay"
            val measuredLeftOut = HOME_LINE_LEFT_OUT[place to sample.label].orEmpty()
            if (leftOut != measuredLeftOut) failures += "the line was left out at $leftOut, measured at $measuredLeftOut"
            val measuredTableCut = HOME_TABLE_CUT[place].orEmpty()
            if (tableCut != measuredTableCut) failures += "the table's own rows were cut at $tableCut, measured at $measuredTableCut"
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun findAllTexts(node: androidx.compose.ui.semantics.SemanticsNode): String =
        node.children.joinToString("") { (if (SemanticsProperties.Text in it.config) it.config[SemanticsProperties.Text].joinToString("") else "") + findAllTexts(it) }

    @Test
    fun theShortLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[0])

    @Test
    fun theCommonLongLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[1])

    @Test
    fun theLongestLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[2])

    @Test
    fun theUnderOnePercentLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[3])

    @Test
    fun theLongestTwoDigitPaceLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[4])

    @Test
    fun theLongestUnderOnePercentLineFitsUnderTheTable() = check(BATTERY_LINE_SAMPLES[5])

    @Test
    fun aGameNeverMeasuredHasNoLine() = check(null)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeBatteryLineFit768Test : HomeBatteryLineFitBase("text 768", hasBoxArt = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeBatteryLineFitThorTest : HomeBatteryLineFitBase("text Thor", hasBoxArt = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeBatteryLineFitCover768Test : HomeBatteryLineFitBase("cover 768", hasBoxArt = true, inBlock = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeBatteryLineFitCoverThorTest : HomeBatteryLineFitBase("cover Thor", hasBoxArt = true, inBlock = true)

/** The narrow stop: the cover beside the title, the table under the title's fact lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeBatteryLineFitCover576Test : HomeBatteryLineFitBase("cover 576", hasBoxArt = true)

/**
 * Measured at the default weight Medium: where the top screen's line drops "on this charge" to stay whole, by place and
 * wording ("Size Font"); everywhere else the whole sentence fits. The text-only column keeps every word.
 */
private val HOME_WORDS_GIVE_WAY: Map<Pair<String, String>, Set<String>> =
    mapOf(
        // The 40 dp footer zone gave the column 4 dp more: Largest Serif keeps the whole sentence now.
        ("cover 576" to "longest under 1%") to setOf("Largest Sans", "Huge Mono", "Largest Mono"),
        ("cover 576" to "longest") to setOf("Largest Mono"),
        ("cover 576" to "longest two-digit pace") to setOf("Largest Mono"),
        // At 576 x 432 the narrow stop has no height left for three lines of the short wording at Largest Serif.
        ("cover 576" to "short") to setOf("Largest Serif"),
    )

/**
 * Where the line is left out because not even its short form fits the height left, by place and wording. None now: the wide cover
 * column has no line (its battery is a cell of the details block, checked in [HomeBatteryLineFitBase] with `inBlock`), and the
 * text-only column and the narrow stop always show the line.
 */
private val HOME_LINE_LEFT_OUT: Map<Pair<String, String>, Set<String>> =
    listOf("common long", "longest", "under 1%", "longest two-digit pace", "longest under 1%").associate { ("cover 576" to it) to setOf("Largest Serif") }

/**
 * Where the battery cell is left out of the facts box because three rows cannot hold the cells (by place, "Size Font"). The cells are placed
 * for the widest time the cell can say, so this is the same for every time: a time that changes while a game rests moves nothing.
 */
private val HOME_BATTERY_CELL_LEFT_OUT: Map<String, Set<String>> =
    mapOf(
        "cover 768" to setOf("Larger Jost", "Huge Jost", "Largest Jost", "Larger Serif", "Huge Serif", "Largest Serif", "Largest Mono"),
        "cover Thor" to setOf("Largest Jost", "Huge Serif", "Largest Serif"),
    )

/**
 * Where the facts table's own rows are cut by the column, by place ("Size Font"): on 768 and the Thor (cover columns) the cover gives
 * way for the whole box and every value is one line with an ellipsis, so none; the text-only column and the 576 narrow stop hold a
 * fixed room and cut at the sizes listed. The 640x480 narrow stop has no emulator and battery cells at all. The table is measured
 * before the line, so the line does not cause it.
 */
private val HOME_TABLE_CUT: Map<String, Set<String>> =
    mapOf(
        "cover 768" to emptySet(),
        "cover Thor" to emptySet(),
        // The text-only column and the narrow stop hold their table's room: each value is one line (an ellipsis ends one that is too long; the full
        // text is in Info), so nothing moves when the values arrive. That is cut at the largest sizes.
        "text 768" to setOf("Largest Jost", "Larger Sans", "Huge Sans", "Largest Sans", "Large Serif", "Larger Serif", "Huge Serif", "Largest Serif", "Large Mono", "Larger Mono", "Huge Mono", "Largest Mono"),
        "text Thor" to setOf("Largest Jost", "Larger Sans", "Huge Sans", "Largest Sans", "Large Serif", "Larger Serif", "Huge Serif", "Largest Serif", "Large Mono", "Larger Mono", "Huge Mono", "Largest Mono"),
        "cover 576" to setOf("Larger Jost", "Huge Jost", "Largest Jost", "Large Sans", "Larger Sans", "Huge Sans", "Largest Sans", "Medium Serif", "Large Serif", "Larger Serif", "Huge Serif", "Largest Serif", "Medium Mono", "Large Mono", "Larger Mono", "Huge Mono", "Largest Mono"),
    )
