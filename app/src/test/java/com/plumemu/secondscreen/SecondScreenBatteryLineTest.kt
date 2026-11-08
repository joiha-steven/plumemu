package com.plumemu.secondscreen

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.batteryLineCut
import com.plumemu.battery.PickingBatteryTags
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The battery line on the game card, measured: in the card's foot (above the file line, or on the bottom margin without
 * one), with the battery glyph first, inside the margins at all six text sizes; none for a game never measured
 * or before the play facts are known; the time only, as the board says it (no pace), and none while charging; dropped whole
 * (never cut) where the height runs out.
 */
abstract class SecondScreenBatteryLineBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private var showBattery by mutableStateOf(true)
    private val badges = CacheOnlyBadges()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        battery(level = 60, isCharging = false)
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides badges) { SecondScreenCard(content, showBattery = showBattery) } } }
    }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun battery(
        level: Int,
        isCharging: Boolean,
    ) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, if (isCharging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING)
                .putExtra(BatteryManager.EXTRA_PLUGGED, if (isCharging) BatteryManager.BATTERY_PLUGGED_USB else 0),
        )
    }

    private fun show(
        content: SecondScreenContent,
        settings: PlumThemeSettings = PlumThemeSettings.Default,
    ) {
        this.settings = settings
        this.content = content
        compose.waitForIdle()
    }

    private fun px(dp: Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun all(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun textOf(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("")

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private val thirty = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)

    private val cards
        get() =
            listOf(
                "board" to BOARD_GAME.copy(batteryPace = thirty),
                "no RA" to BOARD_GAME_NO_RA.copy(batteryPace = thirty),
                "no description" to BOARD_GAME.copy(details = null, batteryPace = thirty),
                "long title" to BOARD_GAME.copy(title = LONG_TITLE, batteryPace = thirty),
                "long file" to BOARD_GAME.copy(fileName = LONG_FILE_NAME, batteryPace = thirty),
                "no file" to BOARD_GAME.copy(fileName = null, batteryPace = thirty),
            )

    /** How many lines the text node is high: its box over one line of its own layout (the layout the semantics hand out is made without the screen's width, so only the box is trusted). */
    private fun linesOf(node: SemanticsNode): Int {
        val layout = layoutOf(node)
        return Math.round(node.boundsInRoot.height / (layout.getLineBottom(0) - layout.getLineTop(0)))
    }

    /** Whether the line's text does not show all its words ([batteryLineCut]). */
    private fun isCut(node: SemanticsNode): Boolean = batteryLineCut(node) != null

    /**
     * The line is in the card's foot, inside its margins: the file line the foot gap under it, or, with no file line, on the bottom margin;
     * the glyph first and centred on the text's first line; one or two lines; under the tiles.
     */
    private fun assertLineInTheFoot(label: String) {
        val card = bounds(GameCardTags.CARD)
        val line = bounds(PickingBatteryTags.LINE)
        assertTrue("$label: left ${line.left}", line.left >= card.left - 0.5f)
        assertTrue("$label: right ${line.right}", line.right <= card.right + 0.5f)
        assertTrue("$label: under the tiles", line.top >= bounds(GameCardTags.TILES).bottom - 0.5f)
        val file = all(GameCardTags.FILE).singleOrNull()
        if (file == null) {
            assertEquals("$label: the line on the bottom margin", card.bottom, line.bottom, 1f)
        } else {
            assertEquals("$label: the foot gap above the file line", px(SecondScreenGameCardDimens.footGap), file.boundsInRoot.top - line.bottom, 1f)
        }
        val glyph = bounds(PickingBatteryTags.GLYPH)
        val text = bounds(PickingBatteryTags.TEXT)
        assertTrue("$label: the glyph before the text", glyph.right <= text.left)
        assertEquals("$label: the glyph on the left margin", card.left, glyph.left, 0.5f)
        val layout = layoutOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode())
        assertEquals("$label: the glyph centred on the text's first line", text.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2, (glyph.top + glyph.bottom) / 2, 1f)
        assertTrue("$label: one or two lines", linesOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()) in 1..2)
    }

    @Test
    fun theLineReadsTheTimeOnlyAndTheGlyphComesFirst() {
        for ((name, game) in cards) {
            show(game)
            assertEquals("$size $name", "2 h on this charge", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
            assertEquals("$size $name: one line at Medium", 1, linesOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
            assertLineInTheFoot("$size $name")
            assertFalse("$size $name: cut", isCut(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        }
    }

    /**
     * At every text size, with every wording from the shortest to the longest
     * ([BATTERY_LINE_SAMPLES]), the line shows all its words: one line, or wrapped to two between words.
     * A cut is found at the last line it is allowed too ([batteryLineCut]), not only on one line. Where
     * the whole sentence does not fit, the words "on this charge" give way ([WORDS_GIVE_WAY]); where not even
     * that fits, the line is left out rather than cut ([LINE_LEFT_OUT]).
     */
    @Test
    fun theLineFitsAtEveryTextSizeInEveryFont() {
        val cut = mutableListOf<String>()
        val short = mutableListOf<String>()
        val leftOut = mutableListOf<String>()
        for (font in listOf(PlumFont.Jost)) {
            for (textSize in TextSize.entries) {
                for (sample in BATTERY_LINE_SAMPLES) {
                    battery(level = sample.level, isCharging = false)
                    for ((name, game) in cards) {
                        show(game.copy(batteryPace = sample.pace), PlumThemeSettings.Default.copy(textSize = textSize))
                        val label = "$font $textSize $name, ${sample.label}"
                        if (all(PickingBatteryTags.LINE).isEmpty()) {
                            leftOut += label
                            continue
                        }
                        assertLineInTheFoot("$size $label")
                        val text = compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()
                        batteryLineCut(text)?.let { cut += "$label: $it" }
                        if ("on this charge" !in textOf(text)) short += label
                        assertTrue("$size $label: the title is still there", all(GameCardTags.TITLE).isNotEmpty())
                    }
                }
            }
        }
        assertEquals("$size: where the line is cut", emptyList<String>(), cut)
        assertEquals("$size: where the words give way", WORDS_GIVE_WAY.getValue(size), short)
        assertEquals("$size: where the line is left out", LINE_LEFT_OUT.getValue(size), leftOut)
    }

    /** Settings › Show battery off: no line on the cards and no battery receiver for them (as in game); on again, both back. */
    @Test
    fun showBatteryOffShowsNoLineAndListensToNothing() {
        show(BOARD_GAME.copy(batteryPace = thirty))
        assertEquals("on: the line", 1, all(PickingBatteryTags.LINE).size)
        assertEquals("on: one receiver for the window", 1, batteryReceivers())
        showBattery = false
        compose.waitForIdle()
        assertTrue("off: no line", all(PickingBatteryTags.LINE).isEmpty())
        assertEquals("off: no battery receiver", 0, batteryReceivers())
        show(BOARD_GAME_NO_RA.copy(batteryPace = thirty))
        assertTrue("off: none on the other card either", all(PickingBatteryTags.LINE).isEmpty())
        showBattery = true
        compose.waitForIdle()
        assertEquals("on again: the line", 1, all(PickingBatteryTags.LINE).size)
        assertEquals(1, batteryReceivers())
    }

    private fun batteryReceivers() = shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED) }

    @Test
    fun chargingShowsNoLineSinceThereIsNoTimeToSay() {
        battery(level = 60, isCharging = true)
        show(BOARD_GAME.copy(batteryPace = thirty))
        assertTrue("charging: no line", all(PickingBatteryTags.LINE).isEmpty())
        assertTrue("charging: no glyph", all(PickingBatteryTags.GLYPH).isEmpty())
    }

    @Test
    fun theTimeIsTheChargeOverThePaceAndAboutOnlyWhereRoundingHidesMinutes() {
        val expected =
            listOf(
                2.0 to "About 5\u00A0min", 5.0 to "5\u00A0min", 45.0 to "45\u00A0min", 47.0 to "About 45\u00A0min", 60.0 to "1 h", 62.0 to "About 1 h",
                75.0 to "1\u00A0h 15\u00A0min", 100.0 to "About 1\u00A0h 45\u00A0min",
            )
        for ((minutes, words) in expected) {
            show(BOARD_GAME.copy(batteryPace = BatteryPace(minutes, 60.0)))
            assertEquals("$size $minutes min", "$words on this charge", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        }
    }

    @Test
    fun aPaceUnderOnePercentTellsTheTimeOfTheRealPace() {
        show(BOARD_GAME.copy(batteryPace = BatteryPace(100 / 0.32 * 60, 0.32)))
        assertEquals("312\u00A0h 30\u00A0min on this charge", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
    }

    @Test
    fun withoutAKnownPaceTheTimeShowsAloneAndChargingShowsNothing() {
        // A device seen only under 20% since it started: the full charge, so the percent, is not known yet.
        show(BOARD_GAME.copy(batteryPace = BatteryPace(120.0, null)))
        assertEquals("2 h on this charge", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        battery(level = 60, isCharging = true)
        show(SecondScreenContent.Calm)
        show(BOARD_GAME.copy(batteryPace = BatteryPace(120.0, null)))
        assertTrue("charging, nothing known to say", all(PickingBatteryTags.LINE).isEmpty())
    }

    @Test
    fun noLineForAGameNeverMeasuredOrBeforeThePlayFactsAreKnown() {
        for ((name, game) in cards) {
            show(game.copy(batteryPace = null))
            assertTrue("$size $name: no line without a usual pace", all(PickingBatteryTags.LINE).isEmpty())
            show(game.copy(isPlayKnown = false))
            assertTrue("$size $name: no line before the play facts", all(PickingBatteryTags.LINE).isEmpty())
        }
    }

    @Test
    fun theLineSitsAboveTheFileLineWhichKeepsTheBottomMargin() {
        show(BOARD_GAME.copy(batteryPace = thirty))
        val file = bounds(GameCardTags.FILE)
        assertEquals(bounds(GameCardTags.CARD).bottom, file.bottom, 1f)
        assertEquals(px(SecondScreenGameCardDimens.footGap), file.top - bounds(PickingBatteryTags.LINE).bottom, 1f)
    }

    @Test
    fun noOtherCardCarriesTheLine() {
        for ((name, other) in ALL_SAMPLES.filter { it.second !is SecondScreenContent.Game }) {
            show(other)
            assertTrue("$size $name", all(PickingBatteryTags.LINE).isEmpty())
        }
    }
}

/**
 * Measured (`<font> <size> <card>, <wording>`): where the sentence does not fit the card's width and the time drops "on this
 * charge". None: the card is wide enough for the whole sentence at every size, so the words never give way here.
 */
private val WORDS_GIVE_WAY: Map<SecondSize, List<String>> = mapOf(SecondSize.Roomy to emptyList(), SecondSize.Compact to emptyList())

/**
 * Measured, as [WORDS_GIVE_WAY]: where the card's height runs out and the foot's battery line is dropped whole (the file line
 * goes first), never cut. None in Jost (it was Plum Serif at Huge and Largest on Compact, before the Font setting went).
 */
private val LINE_LEFT_OUT: Map<SecondSize, List<String>> = mapOf(SecondSize.Roomy to emptyList(), SecondSize.Compact to emptyList())

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineRoomyTest : SecondScreenBatteryLineBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineCompactTest : SecondScreenBatteryLineBase(SecondSize.Compact)
