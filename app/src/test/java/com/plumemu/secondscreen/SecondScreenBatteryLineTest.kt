package com.plumemu.secondscreen

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.CoverImages
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
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
 * The picking line on the second screen's cards, measured: one line right under the
 * played line, in its fact role, with the battery glyph first, inside the margins at all six text
 * sizes; none for a game never measured or before the play facts are known; the pace only while
 * charging.
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
    private val images: CoverImages = FakeCoverImages()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        battery(level = 60, isCharging = false)
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = images, showBattery = showBattery) } }
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

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

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
                "details, art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT, batteryPace = thirty),
                "details, portrait box" to PORTRAIT_GAME.copy(art = PORTRAIT_FRONT, batteryPace = thirty),
                "details, no art" to DETAILS_GAME.copy(batteryPace = thirty),
                "details, long title" to GAME_LONG_TITLE.withDetails(FULL_DETAILS, LANDSCAPE_FRONT).copy(fileName = LONG_FILE_NAME, batteryPace = thirty),
                "picking card" to GAME_WITH_STAR.copy(batteryPace = thirty),
                "picking card, long title" to GAME_LONG_TITLE.copy(batteryPace = thirty),
            )

    /**
     * How many lines the text node is high (its box over the played line's height, one line in the same role; the text layout the semantics
     * hand out is made without the screen's width, so only the box is trusted).
     */
    private fun linesOf(node: SemanticsNode): Int = Math.round(node.boundsInRoot.height / bounds(SecondScreenTags.FACTS).height)

    /** Whether the line's text does not show all its words ([batteryLineCut]). */
    private fun isCut(node: SemanticsNode): Boolean = batteryLineCut(node) != null

    /** The line is one line right under the played line, 4 below it, inside the margins; the played line is whole. */
    private fun assertLineBelowThePlayedLine(label: String) {
        val card = bounds(SecondScreenTags.CARD)
        val margin = px(dimens.margin)
        val line = bounds(PickingBatteryTags.LINE)
        val played = bounds(SecondScreenTags.FACTS)
        assertEquals("$label: the line on the bottom margin", card.bottom - margin, line.bottom, 0.5f)
        assertEquals("$label: ${SecondScreenDimens.factLineGap} under the played line", px(SecondScreenDimens.factLineGap), line.top - played.bottom, 0.5f)
        assertTrue("$label: left ${line.left}", line.left >= card.left + margin - 0.5f)
        assertTrue("$label: right ${line.right}", line.right <= card.right - margin + 0.5f)
        val glyph = bounds(PickingBatteryTags.GLYPH)
        val text = bounds(PickingBatteryTags.TEXT)
        assertTrue("$label: the glyph before the text", glyph.right <= text.left)
        assertEquals("$label: the glyph on the left margin", px(dimens.margin), glyph.left, 0.5f)
        val layout = layoutOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode())
        assertEquals("$label: the glyph centred on the text's first line", text.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2, (glyph.top + glyph.bottom) / 2, 1f)
        assertTrue("$label: one or two lines", linesOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()) in 1..2)
        assertEquals("$label: the played line on one line", 1, layoutOf(compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()).lineCount)
    }

    @Test
    fun theLineReadsTimeAndPaceAndTheGlyphComesFirst() {
        for ((name, game) in cards) {
            show(game)
            assertEquals("$size $name", "2 h on this charge · 30%\u00A0an\u00A0hour", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
            assertEquals("$size $name: one line at Medium", 1, linesOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
            assertLineBelowThePlayedLine("$size $name")
            assertFalse("$size $name: cut", isCut(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        }
    }

    /**
     * At every text size in every font, with every wording from the shortest to the longest
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
        for (font in PlumFont.entries) {
            for (textSize in TextSize.entries) {
                for (sample in BATTERY_LINE_SAMPLES) {
                    battery(level = sample.level, isCharging = false)
                    for ((name, game) in cards) {
                        show((game as SecondScreenContent.Game).copy(batteryPace = sample.pace), PlumThemeSettings.Default.copy(textSize = textSize, font = font))
                        val label = "$font $textSize $name, ${sample.label}"
                        if (all(PickingBatteryTags.LINE).isEmpty()) {
                            leftOut += label
                            continue
                        }
                        assertLineBelowThePlayedLine("$size $label")
                        val text = compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()
                        batteryLineCut(text)?.let { cut += "$label: $it" }
                        if ("on this charge" !in textOf(text)) short += label
                        assertTrue("$size $label: the title is still there", all(SecondScreenTags.TITLE).isNotEmpty())
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
        show(DETAILS_GAME.copy(batteryPace = thirty))
        assertEquals("on: the line", 1, all(PickingBatteryTags.LINE).size)
        assertEquals("on: one receiver for the window", 1, batteryReceivers())
        showBattery = false
        compose.waitForIdle()
        assertTrue("off: no line", all(PickingBatteryTags.LINE).isEmpty())
        assertEquals("off: no battery receiver", 0, batteryReceivers())
        show(GAME_WITH_STAR.copy(batteryPace = thirty))
        assertTrue("off: none on the picking card either", all(PickingBatteryTags.LINE).isEmpty())
        showBattery = true
        compose.waitForIdle()
        assertEquals("on again: the line", 1, all(PickingBatteryTags.LINE).size)
        assertEquals(1, batteryReceivers())
    }

    private fun batteryReceivers() = shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED) }

    @Test
    fun chargingTellsThePaceOnly() {
        battery(level = 60, isCharging = true)
        show(DETAILS_GAME.copy(batteryPace = thirty))
        assertEquals("30%\u00A0an\u00A0hour", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        assertEquals("the glyph stays", 1, all(PickingBatteryTags.GLYPH).size)
        assertLineBelowThePlayedLine("$size charging")
    }

    @Test
    fun theTimeIsTheChargeOverThePaceAndAboutOnlyWhereRoundingHidesMinutes() {
        val expected =
            listOf(
                2.0 to "About 5 min", 5.0 to "5 min", 45.0 to "45 min", 47.0 to "About 45 min", 60.0 to "1 h", 62.0 to "About 1 h",
                75.0 to "1 h 15 min", 100.0 to "About 1 h 45 min",
            )
        for ((minutes, words) in expected) {
            show(DETAILS_GAME.copy(batteryPace = BatteryPace(minutes, 60.0)))
            assertEquals("$size $minutes min", "$words on this charge · 60%\u00A0an\u00A0hour", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        }
    }

    @Test
    fun aPaceUnderOnePercentIsSaidSoWithTheTimeOfTheRealPace() {
        show(DETAILS_GAME.copy(batteryPace = BatteryPace(100 / 0.32 * 60, 0.32)))
        assertEquals("312 h 30 min on this charge · under\u00A01%\u00A0an\u00A0hour", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        battery(level = 100, isCharging = true)
        show(SecondScreenContent.Calm)
        show(DETAILS_GAME.copy(batteryPace = BatteryPace(100 / 0.32 * 60, 0.32)))
        assertEquals("Under\u00A01%\u00A0an\u00A0hour", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
    }

    @Test
    fun withoutAKnownPaceTheTimeShowsAloneAndChargingShowsNothing() {
        // A device seen only under 20% since it started: the full charge, so the percent, is not known yet.
        show(DETAILS_GAME.copy(batteryPace = BatteryPace(120.0, null)))
        assertEquals("2 h on this charge", textOf(compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()))
        battery(level = 60, isCharging = true)
        show(SecondScreenContent.Calm)
        show(DETAILS_GAME.copy(batteryPace = BatteryPace(120.0, null)))
        assertTrue("charging, nothing known to say", all(PickingBatteryTags.LINE).isEmpty())
    }

    /**
     * Beside a cover at Largest in Plum Serif the card has room for one line only (measured: 72 px of
     * the 144 two lines need, on both sizes): the time drops "on this charge", never a number, and the
     * line is whole.
     */
    @Test
    fun withRoomForOneLineTheWordsGoFirstNotTheNumbers() {
        val largestSerif = PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = PlumFont.Serif)
        battery(level = 83, isCharging = false)
        show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT, batteryPace = BATTERY_LINE_SAMPLES[1].pace), largestSerif)
        val text = compose.onNodeWithTag(PickingBatteryTags.TEXT).fetchSemanticsNode()
        assertEquals("$size", "About 2 h 45 min · 30%\u00A0an\u00A0hour", textOf(text))
        assertEquals("$size: whole", null, batteryLineCut(text))
        assertLineBelowThePlayedLine("$size Largest Serif, art")
    }

    @Test
    fun noLineForAGameNeverMeasuredOrBeforeThePlayFactsAreKnown() {
        for ((name, game) in cards) {
            show(game.let { (it as SecondScreenContent.Game).copy(batteryPace = null) })
            assertTrue("$size $name: no line without a usual pace", all(PickingBatteryTags.LINE).isEmpty())
            show((game as SecondScreenContent.Game).copy(isPlayKnown = false))
            assertTrue("$size $name: no line before the play facts", all(PickingBatteryTags.LINE).isEmpty())
        }
    }

    @Test
    fun theLineIsBeforeTheFileLineNeverBetweenTheCardAndItsMargin() {
        // The played line keeps its place above the line; the file line stays above the played line.
        show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT, batteryPace = thirty))
        val file = bounds(SecondScreenTags.FILE_NAME)
        val played = bounds(SecondScreenTags.FACTS)
        assertEquals(px(SecondScreenDimens.factLineGap), played.top - file.bottom, 0.5f)
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
 * Measured (`<font> <size> <card>, <wording>`): where the card has room for one line only and
 * the time drops "on this charge". A change that moves it somewhere new shows up here.
 */
private val WORDS_GIVE_WAY: Map<SecondSize, List<String>> =
    mapOf(
        SecondSize.Roomy to
            listOf(
                "Serif Largest details, art, common long",
                "Serif Largest details, long title, common long",
                "Serif Largest details, art, longest",
                "Serif Largest details, long title, longest",
                "Serif Largest details, art, under 1%",
                "Serif Largest details, long title, under 1%",
                "Serif Largest details, art, longest two-digit pace",
                "Serif Largest details, long title, longest two-digit pace",
            ),
        SecondSize.Compact to
            listOf(
                "Serif Largest details, art, short",
                "Serif Largest details, portrait box, short",
                "Serif Largest details, long title, short",
                "Serif Largest details, art, common long",
                "Serif Largest details, portrait box, common long",
                "Serif Largest details, long title, common long",
                "Serif Largest details, art, longest",
                "Serif Largest details, portrait box, longest",
                "Serif Largest details, long title, longest",
                "Serif Largest details, art, under 1%",
                "Serif Largest details, portrait box, under 1%",
                "Serif Largest details, long title, under 1%",
            ),
    )

/**
 * Measured, as [WORDS_GIVE_WAY]: where even the short form needs a second line the card does not
 * have (the description, under the cover, has none left to give: the line is measured before it), so the
 * line is left out rather than cut. Only the two longest wordings, beside a cover at Largest in Plum Serif.
 */
private val LINE_LEFT_OUT: Map<SecondSize, List<String>> =
    mapOf(
        SecondSize.Roomy to
            listOf(
                "Serif Largest details, art, longest under 1%",
                "Serif Largest details, long title, longest under 1%",
            ),
        SecondSize.Compact to
            listOf(
                "Serif Largest details, art, longest two-digit pace",
                "Serif Largest details, portrait box, longest two-digit pace",
                "Serif Largest details, long title, longest two-digit pace",
                "Serif Largest details, art, longest under 1%",
                "Serif Largest details, portrait box, longest under 1%",
                "Serif Largest details, long title, longest under 1%",
            ),
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineRoomyTest : SecondScreenBatteryLineBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineCompactTest : SecondScreenBatteryLineBase(SecondSize.Compact)
