package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.SecondScreenText
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.GameDetails
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.After
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
 * What one card change costs the main thread: the description lays out only what can show (and the lines shown are the whole
 * text's), a tile label lays out once, the battery and file lines are composed once, a title offered again measures nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardCostTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(SecondScreenText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val badges = CacheOnlyBadges()

    private fun host(size: SecondSize) {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides badges) { SecondScreenCard(content) } } }
    }

    @Before
    fun startCounting() {
        CardLayoutCount.counts.clear()
        CardLayoutCount.counting = true
        forgetFittedTitles()
    }

    @After
    fun stopCounting() {
        CardLayoutCount.counting = false
    }

    private fun show(
        game: SecondScreenContent,
        textSize: TextSize = TextSize.Medium,
    ) {
        settings = PlumThemeSettings.Default.copy(textSize = textSize)
        content = game
        compose.waitForIdle()
    }

    private fun withDescription(text: String) = BOARD_GAME.copy(details = GameDetails(year = null, publisher = "", genre = "", players = "", description = text))

    private fun descriptionLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(GameCardTags.DESCRIPTION).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** The lines [shown] has that the whole [full] text has: the same text, start and end, down to the last line the room shows. */
    private fun assertSameShownLines(label: String, full: String) {
        val layout = descriptionLayout()
        val input = layout.layoutInput
        val whole = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(AnnotatedString(full), input.style, constraints = input.constraints)
        val box = compose.onNodeWithTag(GameCardTags.DESCRIPTION_BOX).fetchSemanticsNode().boundsInRoot.height
        val room = box - SecondScreenGameCardDimens.descriptionInkTop.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val line = whole.getLineBottom(0) - whole.getLineTop(0)
        val shown = minOf((room / line + 0.01f).toInt(), whole.lineCount)
        assertTrue("$label: the room shows $shown lines", shown >= 1)
        assertTrue("$label: laid out text is a start of the description", full.startsWith(input.text.text))
        for (i in 0 until shown) {
            assertEquals("$label: line $i", whole.getLineStart(i) to whole.getLineEnd(i), layout.getLineStart(i) to layout.getLineEnd(i))
        }
        assertEquals("$label: the fade", whole.size.height > box, layout.size.height > box)
    }

    @Test
    fun theShownLinesAreTheWholeDescriptionsLines() {
        for (size in SecondSize.entries) {
            host(size)
            for (textSize in listOf(TextSize.Small, TextSize.Largest)) {
                for (font in listOf(PlumFont.Jost)) {
                    val long = BOARD_DESCRIPTION.repeat(6).trim()
                    show(withDescription(long), textSize)
                    val whole = descriptionLayout().let { shownLayout ->
                        val input = shownLayout.layoutInput
                        TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(AnnotatedString(long), input.style, constraints = input.constraints)
                    }
                    val box = compose.onNodeWithTag(GameCardTags.DESCRIPTION_BOX).fetchSemanticsNode().boundsInRoot.height
                    val line = whole.getLineBottom(0) - whole.getLineTop(0)
                    val lines = ((box - SecondScreenGameCardDimens.descriptionInkTop.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density) / line + 0.01f).toInt()
                    println("card cost: $size $textSize box $box line $line lines $lines")
                    val cases =
                        mapOf(
                            "short" to "Samus Aran brings the last Metroid to a research station.",
                            "one line fewer" to long.substring(0, whole.getLineEnd(maxOf(lines - 2, 0))).trim(),
                            "exact fit" to long.substring(0, whole.getLineEnd(lines - 1)).trim(),
                            "one line past" to long.substring(0, whole.getLineEnd(lines)).trim(),
                            "two lines past" to long.substring(0, whole.getLineEnd(lines + 1)).trim(),
                            "long" to long,
                            "no spaces" to "Ab".repeat(700),
                        )
                    for ((name, text) in cases) {
                        show(withDescription(text), textSize)
                        assertSameShownLines("$size $textSize $font $name", text)
                    }
                }
            }
        }
    }

    @Test
    fun theDescriptionLaysOutOnlyWhatCanShow() {
        host(SecondSize.Roomy)
        val long = BOARD_DESCRIPTION.repeat(6).trim()
        CardLayoutCount.counts.clear()
        show(withDescription(long))
        val laidOut = CardLayoutCount.counts["description"] ?: 0
        println("card cost: description laid out $laidOut chars in ${CardLayoutCount.counts["descriptionLayouts"]} layouts of ${long.length} characters (before: ${long.length} in 1)")
        val layout = descriptionLayout()
        val box = compose.onNodeWithTag(GameCardTags.DESCRIPTION_BOX).fetchSemanticsNode().boundsInRoot.height
        val shownLines = (box / (layout.getLineBottom(0) - layout.getLineTop(0))).toInt()
        println("card cost: $shownLines lines show, ${layout.lineCount} laid out")
        assertTrue("laid out ${layout.lineCount} lines for $shownLines shown", layout.lineCount <= shownLines + 5)
        assertTrue("laid out $laidOut of ${long.length} characters", laidOut in 1..long.length / 2)
        assertEquals("one layout is enough", 1, CardLayoutCount.counts["descriptionLayouts"])
    }

    @Test
    fun aCardChangeComposesAndLaysOutEachPartOnce() {
        host(SecondSize.Roomy)
        CardLayoutCount.counts.clear()
        show(BOARD_GAME.copy(batteryPace = com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)))
        val counts = CardLayoutCount.counts.toSortedMap()
        println("card cost: $counts (before: battery 2, file 2)")
        assertEquals("battery composed once", 1, counts["battery"])
        assertEquals("file composed once", 1, counts["file"])
        assertEquals("one layout per tile label (${counts["tileLabel"]})", 2, counts["tileLabel"])
        assertEquals("a title measures once", 1, counts["titleMeasure"])
        // The same card offered again, and the one walked back to, measure no title.
        CardLayoutCount.counts.clear()
        show(BOARD_GAME.copy(gameId = 2L, batteryPace = com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)))
        assertEquals("a title seen again measures nothing", null, CardLayoutCount.counts["titleMeasure"])
    }

    /** A foot part that does not fit is measured once, then left out: no node of it sits unplaced in the tree, and a kept one is composed once. */
    @Test
    fun aFootPartThatDoesNotFitLeavesNoNodeInTheTree() {
        host(SecondSize.Compact)
        val cards = listOf("board" to BOARD_GAME, "no RA" to BOARD_GAME_NO_RA, "no description" to BOARD_GAME.copy(details = null))
        for (textSize in TextSize.entries) {
            for (sample in BATTERY_LINE_SAMPLES) {
                for ((name, card) in cards) {
                    CardLayoutCount.counts.clear()
                    show(card.copy(batteryPace = sample.pace), textSize)
                    assertTrue("$textSize ${sample.label} $name: battery composed ${CardLayoutCount.counts["battery"]} times", (CardLayoutCount.counts["battery"] ?: 1) == 1)
                    for (tag in listOf(GameCardTags.FILE, GameCardTags.BATTERY)) {
                        for (node in compose.onAllNodesWithTag(tag).fetchSemanticsNodes()) {
                            assertTrue("$textSize ${sample.label} $name $tag: a node in the tree sits unplaced at ${node.boundsInRoot.top}", node.boundsInRoot.top > 0f)
                        }
                    }
                }
            }
        }
    }
}
