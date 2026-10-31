package com.plumemu.textsize

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.battery.BatteryLineSample
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.batteryLineCut
import com.plumemu.battery.PickingBatteryTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GameFacts
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.NameCertainty
import com.plumemu.game.NameFacts
import com.plumemu.game.NameOrigin
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
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
 * Info of a game measured before: the battery line is the ninth row, after Play time, in
 * the table's own roles (no label: the glyph names it); at the six sizes in every font, with the shortest to the longest
 * wording ([BATTERY_LINE_SAMPLES]), nothing is ellipsized (the battery row not even at Largest, where Info's other values
 * may be: [batteryLineCut]), clipped, too short or on top of another and every row, the battery row and
 * "Name from" included, can be read whole with Up and Down ([InfoReadThrough]). A game never
 * measured has the eight rows it had.
 */
abstract class GameInfoBatteryFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    @Before
    fun batteryIs60Percent() {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    private fun facts(
        pace: BatteryPace?,
        withName: Boolean = false,
    ): GameFacts =
        sampleFacts(title = "Super Metroid").copy(
            batteryPace = pace,
            fileName = "Super Metroid.sfc",
            name = if (withName) NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "Nintendo - Super Nintendo Entertainment System", hasFix = false) else null,
        )

    private fun problems(
        facts: GameFacts,
        sizes: List<Pair<PlumFont, TextSize>>,
        /** Where (`<size> <font>`) the row was measured to drop "on this charge" ([INFO_WORDS_GIVE_WAY]); anywhere else it is a problem. */
        wordsGiveWay: Set<String> = emptySet(),
    ): List<String> {
        val gaveWay = mutableSetOf<String>()
        var size by mutableStateOf(sizes.first().second)
        var font by mutableStateOf(sizes.first().first)
        val info = InfoReadThrough(OpenPanel(GameRef("1", facts.title), PanelScreen.Info(facts)))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { info.screen() } }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in sizes) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            val label = "${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allow = FitAllowance(setOf(GamePanelTags.ROWS, GamePanelTags.FACTS_SCROLL), ellipsisUnder = GamePanelTags.FACTS, isLargest = each == TextSize.Largest)
            // The table's other rows are held to Info's own measure (every size in Sans, Large and Largest in Serif and Mono:
            // GameInfoArcadeFitTest); the battery row, below, at every size in every font.
            if (eachFont == PlumFont.Sans || each == TextSize.Large || each == TextSize.Largest) textProblems(root, allow).mapTo(failures) { "$label: $it" }
            // The other values may end in "…" at Largest (Info's table); the battery row never: its numbers are the point.
            if (facts.batteryPace != null) {
                val text = compose.onAllNodesWithTag(PickingBatteryTags.TEXT, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
                if (text == null) failures += "$label: no battery text" else batteryLineCut(text)?.let { failures += "$label: $it" }
                if (text != null && "on this charge" !in text.config[SemanticsProperties.Text].joinToString("")) gaveWay += "${each.name} ${eachFont.name}"
            }
            info.unreadRows(compose).mapTo(failures) { "$label: $it" }
        }
        if (gaveWay != wordsGiveWay) failures += "the words gave way at $gaveWay, measured at $wordsGiveWay"
        return failures
    }

    private val everySize = PlumFont.entries.flatMap { font -> TextSize.entries.map { font to it } }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun battery(level: Int) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    private fun wordsGiveWay(sample: BatteryLineSample): Set<String> = INFO_WORDS_GIVE_WAY[widthDp to sample.label].orEmpty()

    private fun sampleFacts(
        sample: BatteryLineSample,
        withName: Boolean = false,
    ): GameFacts {
        battery(sample.level)
        return facts(sample.pace, withName)
    }

    private fun assertNone(failures: List<String>) = assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())

    @Test
    fun theShortBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[0]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[0])))

    @Test
    fun theCommonLongBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[1]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[1])))

    @Test
    fun theLongestBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[2]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[2])))

    @Test
    fun theUnderOnePercentBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[3]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[3])))

    @Test
    fun theLongestTwoDigitPaceBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[4]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[4])))

    @Test
    fun theLongestUnderOnePercentBatteryRowIsReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[5]), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[5])))

    @Test
    fun theLongestBatteryRowAndNameFromAreReadWholeAtEverySize() = assertNone(problems(sampleFacts(BATTERY_LINE_SAMPLES[2], withName = true), everySize, wordsGiveWay(BATTERY_LINE_SAMPLES[2])))

    @Test
    fun theRowSaysTheLineAfterPlayTime() {
        assertNone(problems(facts(BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)), listOf(PlumFont.Sans to TextSize.Medium)))
        compose.onNodeWithText("2 h on this charge · 30%\u00A0an\u00A0hour", useUnmergedTree = true).assertExists()
        val last = compose.onNodeWithTag(GamePanelTags.fact(8), useUnmergedTree = true).fetchSemanticsNode()
        val before = compose.onNodeWithTag(GamePanelTags.fact(7), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the battery row under Play time", last.boundsInRoot.top >= before.boundsInRoot.bottom - 1f)
    }

    @Test
    fun aGameNeverMeasuredHasTheEightRows() {
        assertNone(problems(facts(null), listOf(PlumFont.Sans to TextSize.Medium)))
        assertEquals(0, compose.onAllNodesWithTag(GamePanelTags.fact(8), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag(PickingBatteryTags.LINE, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}

/**
 * Measured at the default weight Medium: where Info's battery row (three lines at most) drops "on this charge" to keep its
 * numbers whole, by width and wording; everywhere else the whole sentence fits.
 */
private val INFO_WORDS_GIVE_WAY: Map<Pair<Int, String>, Set<String>> =
    mapOf(
        (768 to "longest") to setOf("Largest Mono"),
        (837 to "longest") to setOf("Largest Mono"),
        (768 to "longest two-digit pace") to setOf("Largest Mono"),
        (837 to "longest two-digit pace") to setOf("Largest Mono"),
        (768 to "longest under 1%") to setOf("Largest Serif", "Largest Mono"),
        (837 to "longest under 1%") to setOf("Largest Serif", "Largest Mono"),
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameInfoBatteryFit768Test : GameInfoBatteryFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class GameInfoBatteryFit837Test : GameInfoBatteryFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class GameInfoBatteryFit667Test : GameInfoBatteryFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameInfoBatteryFit576Test : GameInfoBatteryFitBase(576, 432)
