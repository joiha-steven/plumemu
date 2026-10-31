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
 * value role: the text-only column ([hasBoxArt] false) and the cover column (wide at 768 and the Thor's
 * size, the narrow stop at 576). At every text size in every font, with the shortest to the longest
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
        val tableCut = mutableSetOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val label = "${each.name} ${eachFont.name} ${sample?.label}"
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val details = findTagged(root, HomeTags.DETAILS) ?: run { failures += "$label: no details column"; null } ?: continue
            val line = findTagged(details, PickingBatteryTags.LINE)
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
            if (sample == BATTERY_LINE_SAMPLES[0] && each == TextSize.Medium && eachFont == PlumFont.Sans) {
                assertEquals("2 h on this charge · 30%\u00A0an\u00A0hour", text.config[SemanticsProperties.Text].joinToString(""))
            }
            notFullyShown(line, root)?.let { failures += "$label: the line $it" }
            if (line.boundsInRoot.bottom > details.boundsInRoot.bottom + 1f) failures += "$label: the line runs below the column"
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
class HomeBatteryLineFitCover768Test : HomeBatteryLineFitBase("cover 768", hasBoxArt = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeBatteryLineFitCoverThorTest : HomeBatteryLineFitBase("cover Thor", hasBoxArt = true)

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
        ("cover 768" to "short") to setOf("Larger Sans", "Large Serif"),
        ("cover Thor" to "short") to setOf("Largest Sans", "Larger Serif", "Huge Mono"),
        ("cover Thor" to "longest under 1%") to setOf("Huge Sans", "Larger Mono"),
        ("cover 576" to "longest under 1%") to setOf("Largest Mono"),
    )

/** The cover column on 768 x 432 from Larger up: the height the cover and the table leave holds no line. */
private val COVER_768_NO_ROOM = setOf("Larger Sans", "Huge Sans", "Largest Sans", "Large Serif", "Larger Serif", "Huge Serif", "Largest Serif", "Larger Mono", "Huge Mono", "Largest Mono")

/** The cover column on the Thor (837 x 471) at its largest sizes. */
private val COVER_THOR_NO_ROOM = setOf("Largest Sans", "Larger Serif", "Huge Serif", "Largest Serif", "Huge Mono", "Largest Mono")

/**
 * Measured: where not even the short form fits the height the wide cover column has left under
 * the table, so the line is left out rather than cut or shown in part, by place and wording. Only some of
 * these are where the table itself has no room ([HOME_TABLE_CUT]); in the rest the table is whole and the
 * line still has no room (the cover keeps its height: only the title block makes it give way). The text-only
 * column and the narrow stop always show it.
 */
private val HOME_LINE_LEFT_OUT: Map<Pair<String, String>, Set<String>> =
    mapOf(
        ("cover 768" to "short") to COVER_768_NO_ROOM - setOf("Larger Sans", "Large Serif"),
        ("cover 768" to "common long") to COVER_768_NO_ROOM,
        ("cover 768" to "longest") to COVER_768_NO_ROOM,
        ("cover 768" to "under 1%") to COVER_768_NO_ROOM,
        ("cover 768" to "longest two-digit pace") to COVER_768_NO_ROOM,
        ("cover 768" to "longest under 1%") to COVER_768_NO_ROOM,
        ("cover Thor" to "short") to setOf("Huge Serif", "Largest Serif", "Largest Mono"),
        ("cover Thor" to "common long") to COVER_THOR_NO_ROOM,
        ("cover Thor" to "longest") to COVER_THOR_NO_ROOM,
        ("cover Thor" to "under 1%") to COVER_THOR_NO_ROOM,
        ("cover Thor" to "longest two-digit pace") to COVER_THOR_NO_ROOM,
        ("cover Thor" to "longest under 1%") to COVER_THOR_NO_ROOM,
    )

/**
 * Seen while measuring the line, not changed (the table's): where the facts table's own
 * rows are cut by the column, by place ("Size Font"): on 768 and the Thor the emulator's second line by
 * the height left, at the narrow stop at Largest Mono the values by the width. The table is measured
 * before the line, so the line does not cause it. 768 Larger Serif joined when Medium weight became the default.
 */
private val HOME_TABLE_CUT: Map<String, Set<String>> =
    mapOf(
        "cover 768" to setOf("Largest Sans", "Larger Serif", "Huge Serif", "Largest Serif", "Largest Mono"),
        "cover Thor" to setOf("Largest Serif"),
        "cover 576" to setOf("Largest Mono"),
    )
