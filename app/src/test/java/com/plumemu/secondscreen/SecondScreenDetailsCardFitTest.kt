package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details card (layout C) measured, not looked at: inside its margins at every text size, the
 * title cut before its star, the description and fact lines cut only at their ends, a missing part
 * closing up, the text block on the bottom margin without art, and the picking card without details.
 */
abstract class SecondScreenDetailsCardFitBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(MediumText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val images: CoverImages = FakeCoverImages()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = images) } }
    }

    private fun show(
        content: SecondScreenContent,
        settings: PlumThemeSettings = MediumText.Default,
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

    /** Every text of the card, the cover and the played line inside the margins; nothing overlaps the next part down. */
    private fun assertInsideTheMargins(label: String) {
        val card = bounds(SecondScreenTags.CARD)
        val margin = px(dimens.margin)
        val parts = listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.FACT_LINE, SecondScreenTags.DESCRIPTION, SecondScreenTags.FILE_NAME, SecondScreenTags.FACTS, SecondScreenTags.COVER)
        for (tag in parts) {
            for (node in all(tag)) {
                val b = node.boundsInRoot
                assertTrue("$label: $tag left ${b.left}", b.left >= card.left + margin - 0.5f)
                assertTrue("$label: $tag top ${b.top}", b.top >= card.top + margin - 0.5f)
                assertTrue("$label: $tag right ${b.right}", b.right <= card.right - margin + 0.5f)
                assertTrue("$label: $tag bottom ${b.bottom}", b.bottom <= card.bottom - margin + 0.5f)
            }
        }
        val played = bounds(SecondScreenTags.FACTS)
        assertEquals("$label: the played line on the bottom margin", card.bottom - margin, played.bottom, 0.5f)
        all(SecondScreenTags.DESCRIPTION).singleOrNull()?.let { description ->
            assertTrue("$label: 16 between the description and the played line", description.boundsInRoot.bottom + px(SecondScreenDimens.descriptionGap) <= played.top + 0.5f)
        }
        all(SecondScreenTags.FILE_NAME).singleOrNull()?.let { file ->
            assertEquals("$label: the file line 4 above the played line", px(SecondScreenDimens.factLineGap), played.top - file.boundsInRoot.bottom, 0.5f)
            val above = all(SecondScreenTags.DESCRIPTION).singleOrNull() ?: all(SecondScreenTags.FACT_LINE).lastOrNull() ?: compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode()
            assertTrue("$label: 16 at least above the file line", above.boundsInRoot.bottom + px(SecondScreenDimens.descriptionGap) <= file.boundsInRoot.top + 0.5f)
            all(SecondScreenTags.COVER).singleOrNull()?.let { cover ->
                assertTrue("$label: 16 at least between the cover and the file line", cover.boundsInRoot.bottom + px(SecondScreenDimens.descriptionGap) <= file.boundsInRoot.top + 0.5f)
            }
        }
        all(SecondScreenTags.COVER).singleOrNull()?.let { cover ->
            assertTrue("$label: the cover inside its square", cover.boundsInRoot.width <= px(dimens.artSlot) + 0.5f && cover.boundsInRoot.height <= px(dimens.artSlot) + 0.5f)
        }
    }

    /**
     * The file line shows, or it is left out because it does not fit: either it gave way to the
     * description's last line (the description shows), or the height left under the parts above is
     * shorter than the file line with its gaps. Either way the played line keeps 16 above it.
     */
    private fun assertFileLineShownOrGaveWayToTheDescription(label: String) {
        if (all(SecondScreenTags.FILE_NAME).isNotEmpty()) return
        val played = bounds(SecondScreenTags.FACTS)
        val above = listOf(SecondScreenTags.DESCRIPTION, SecondScreenTags.FACT_LINE, SecondScreenTags.TITLE, SecondScreenTags.COVER).flatMap(::all).maxOf { it.boundsInRoot.bottom }
        assertTrue("$label: 16 above the played line without the file line", above + px(SecondScreenDimens.descriptionGap) <= played.top + 0.5f)
        if (all(SecondScreenTags.DESCRIPTION).isEmpty()) {
            // A fact line has the file line's role, so its height is the file line's.
            val fileLine = all(SecondScreenTags.FACT_LINE).first().boundsInRoot.height
            val needs = px(SecondScreenDimens.descriptionGap) + fileLine + px(SecondScreenDimens.factLineGap)
            assertTrue("$label: no description and no file line, though ${played.top - above} px were left for its $needs", played.top - above < needs)
        }
    }

    /** The title keeps its star and its lines; the description its lines; the played line is whole. */
    private fun assertNothingCutBeyondItsEllipsis(
        label: String,
        isFavorite: Boolean,
    ) {
        val title = compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode()
        val titleLayout = layoutOf(title)
        if (isFavorite) assertTrue("$label: the star is the last character: '${textOf(title)}'", textOf(title).endsWith(FAVORITE_STAR))
        assertTrue("$label: title ${titleLayout.lineCount} lines", titleLayout.lineCount <= dimens.detailsTitleMaxLines)
        assertFalse("$label: the title overflows its box", titleLayout.hasVisualOverflow)
        all(SecondScreenTags.DESCRIPTION).singleOrNull()?.let {
            val layout = layoutOf(it)
            assertTrue("$label: description ${layout.lineCount} lines", layout.lineCount <= dimens.descriptionMaxLines)
            // Its lines all show: a box shorter than them would clip the last one without its "…".
            val bottom = layout.getLineBottom(layout.lineCount - 1)
            assertTrue("$label: description lines end at $bottom px in a ${it.boundsInRoot.height} px box", bottom <= it.boundsInRoot.height + 1f)
        }
        // The file line: one line, a long name cut at its end ("…"), never clipped.
        all(SecondScreenTags.FILE_NAME).singleOrNull()?.let {
            val layout = layoutOf(it)
            assertEquals("$label: the file line on one line", 1, layout.lineCount)
            assertTrue("$label: the file line's text is whole in its box", layout.getLineBottom(0) <= it.boundsInRoot.height + 1f)
        }
        // The played line may be cut at its end ("…") since it names the emulator: one line.
        assertEquals("$label: the played line on one line", 1, layoutOf(compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()).lineCount)
    }

    private val cases
        get() =
            listOf(
                "portrait box" to PORTRAIT_GAME.copy(art = PORTRAIT_FRONT),
                "landscape box" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT),
                "long title, portrait box" to GAME_LONG_TITLE.withDetails(FULL_DETAILS, PORTRAIT_FRONT).copy(fileName = LONG_FILE_NAME),
                "long title, landscape box" to GAME_LONG_TITLE.withDetails(FULL_DETAILS, LANDSCAPE_FRONT).copy(fileName = LONG_FILE_NAME),
                "no art" to DETAILS_GAME,
                "long title, no art" to GAME_LONG_TITLE.withDetails(FULL_DETAILS).copy(fileName = LONG_FILE_NAME),
            )

    @Test
    fun everyPartFitsInsideTheMarginsAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((name, each) in cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = textSize))
                val label = "$size $textSize $name"
                assertInsideTheMargins(label)
                assertNothingCutBeyondItsEllipsis(label, each.isFavorite)
                assertTrue("$label: the description shows at least one line", all(SecondScreenTags.DESCRIPTION).isNotEmpty())
                assertFileLineShownOrGaveWayToTheDescription(label)
                // Measured: in Plum Sans it gives way only on Compact at Largest; on Roomy (the Thor) it always shows.
                if (size == SecondSize.Roomy || textSize != TextSize.Largest) assertEquals("$label: the file line shows", 1, all(SecondScreenTags.FILE_NAME).size)
            }
        }
    }

    @Test
    fun theLargestTextFitsInEveryFont() {
        for (font in PlumFont.entries) {
            for ((name, each) in cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = font))
                val label = "$size $font $name"
                assertInsideTheMargins(label)
                assertNothingCutBeyondItsEllipsis(label, each.isFavorite)
                assertFileLineShownOrGaveWayToTheDescription(label)
            }
        }
    }

    @Test
    fun atMediumTheShortLinesAreWholeAndTheLongDescriptionTakesItsLines() {
        // Beside a landscape box a long genre may be cut at its end; beside a portrait one and without art it fits.
        for ((name, each) in cases.filter { it.first == "portrait box" || it.first == "no art" }) {
            show(each)
            for (tag in listOf(SecondScreenTags.TOP, SecondScreenTags.FACT_LINE, SecondScreenTags.FILE_NAME, SecondScreenTags.FACTS)) {
                for (node in all(tag)) assertNoTextCut(node, "$size $name $tag")
            }
            val description = all(SecondScreenTags.DESCRIPTION).single()
            assertEquals("$size $name: a long description takes all its lines", dimens.descriptionMaxLines, layoutOf(description).lineCount)
        }
    }

    @Test
    fun eachMissingPartIsLeftOutAndTheOthersCloseUp() {
        for (picture in listOf(LANDSCAPE_FRONT, null)) {
            for ((name, details) in DETAILS_EACH_MISSING) {
                show(GAME_WITH_STAR.withDetails(details, picture))
                val label = "$size $name ${if (picture == null) "no art" else "art"}"
                val expectedLines = listOf(yearAndPublisherOf(details), genreAndRatingOf(details)).filter { it.isNotEmpty() }
                val lines = all(SecondScreenTags.FACT_LINE)
                assertEquals(label, expectedLines, lines.map(::textOf))
                assertEquals("$label: description", details.description.isNotEmpty(), all(SecondScreenTags.DESCRIPTION).isNotEmpty())
                val title = bounds(SecondScreenTags.TITLE)
                lines.firstOrNull()?.let { assertEquals("$label: first fact line 12 under the title", px(SecondScreenDimens.titleToFactLinesGap), it.boundsInRoot.top - title.bottom, 0.5f) }
                if (lines.size == 2) {
                    assertEquals("$label: 4 between the fact lines", px(SecondScreenDimens.factLineGap), lines[1].boundsInRoot.top - lines[0].boundsInRoot.bottom, 0.5f)
                }
                if (picture == null) {
                    // Without art the block closes up on the bottom margin: each part right on the next.
                    val above = lines.lastOrNull()?.boundsInRoot?.bottom ?: title.bottom
                    val next = all(SecondScreenTags.DESCRIPTION).singleOrNull()?.boundsInRoot ?: bounds(SecondScreenTags.FACTS)
                    assertEquals("$label: 16 above the next part", px(SecondScreenDimens.descriptionGap), next.top - above, 0.5f)
                }
                assertInsideTheMargins(label)
            }
            // No description but a file name: the file line takes the description's place, 16 under
            // what is above (at least, beside a cover: there the part fills the height and the file
            // line rests on the played line) and 4 over the played line.
            show(GAME_WITH_STAR.withDetails(FULL_DETAILS.copy(description = ""), picture).copy(fileName = DETAILS_FILE_NAME))
            val label = "$size no description, a file name, ${if (picture == null) "no art" else "art"}"
            assertTrue("$label: no description", all(SecondScreenTags.DESCRIPTION).isEmpty())
            val file = compose.onNodeWithTag(SecondScreenTags.FILE_NAME).fetchSemanticsNode().boundsInRoot
            assertEquals("$label: 4 over the played line", px(SecondScreenDimens.factLineGap), bounds(SecondScreenTags.FACTS).top - file.bottom, 0.5f)
            val above = listOf(SecondScreenTags.FACT_LINE, SecondScreenTags.TITLE, SecondScreenTags.COVER).flatMap(::all).maxOf { it.boundsInRoot.bottom }
            if (picture == null) {
                assertEquals("$label: 16 under the last fact line", px(SecondScreenDimens.descriptionGap), file.top - above, 0.5f)
            } else {
                assertTrue("$label: 16 at least under the cover and the text beside it", above + px(SecondScreenDimens.descriptionGap) <= file.top + 0.5f)
            }
            assertInsideTheMargins(label)
        }
    }

    /**
     * Where the file line gives way at Largest, font by font (measured, then pinned): a change to the
     * card's sizes that makes it give way somewhere new shows up here.
     */
    @Test
    fun atLargestTheFileLineGivesWayOnlyWhereMeasured() {
        val gaveWay = mutableListOf<String>()
        for (font in PlumFont.entries) {
            for ((name, each) in cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = font))
                if (all(SecondScreenTags.FILE_NAME).isEmpty()) gaveWay += "$font $name"
            }
        }
        assertEquals("$size: where the file line gives way at Largest", GAVE_WAY_AT_LARGEST.getValue(size), gaveWay)
    }

    private fun yearAndPublisherOf(details: com.plumemu.home.GameDetails) = listOfNotNull(details.year?.toString(), details.publisher.ifEmpty { null }).joinToString(" · ")

    /** The genre and the rating out of 10 (one decimal only when not whole); the players count is never shown. */
    private fun genreAndRatingOf(details: com.plumemu.home.GameDetails) =
        listOfNotNull(
            details.genre.ifEmpty { null },
            details.rating?.let { if (it % 2 == 0) "${it / 2}/10" else "${it / 2}.5/10" },
        ).joinToString(" · ")

    @Test
    fun withoutArtTheBlockKeepsThePickingCardsPlaceOnTheBottomMargin() {
        show(DETAILS_GAME)
        assertTrue("no cover", all(SecondScreenTags.COVER).isEmpty())
        val card = bounds(SecondScreenTags.CARD)
        assertEquals("the top line's dot on the left margin", px(dimens.margin), all(SecondScreenTags.TOP).single().boundsInRoot.left - px(SecondScreenDimens.topDot) - px(SecondScreenDimens.topDotGap), 0.5f)
        assertEquals("the title on the left margin", px(dimens.margin), bounds(SecondScreenTags.TITLE).left, 0.5f)
        assertEquals("the played line on the bottom margin", card.bottom - px(dimens.margin), bounds(SecondScreenTags.FACTS).bottom, 0.5f)
        // The description takes the full width: as wide as the card less both margins.
        assertEquals(card.width - 2 * px(dimens.margin), bounds(SecondScreenTags.DESCRIPTION).width, 1f)
    }

    @Test
    fun aGameWithNoDetailsAtAllKeepsThePickingCard() {
        for (each in listOf(GAME_WITH_STAR, GAME_WITH_STAR.withDetails(EMPTY_DETAILS), GAME_WITH_STAR.withDetails(null))) {
            show(each)
            assertTrue("$each: no details card", all(SecondScreenTags.DETAILS).isEmpty())
            assertEquals("$each: the picking block", 1, all(SecondScreenTags.BLOCK).size)
        }
    }

    @Test
    fun noPlayedLineUntilThePlayIsKnownAndNothingMovesWhenItArrives() {
        for (each in listOf(DETAILS_GAME.copy(art = LANDSCAPE_FRONT), DETAILS_GAME)) {
            show(each.copy(isPlayKnown = false))
            assertTrue("no played line before the answer", all(SecondScreenTags.FACTS).isEmpty())
            val before = bounds(SecondScreenTags.DESCRIPTION)
            show(each)
            assertEquals("Snes9x · Played yesterday · 3 h 20 min", textOf(compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()))
            assertEquals("the description stays where it was", before, bounds(SecondScreenTags.DESCRIPTION))
        }
        show(GAME_NEVER_PLAYED.withDetails(FULL_DETAILS).copy(isPlayKnown = true))
        assertEquals("Not chosen yet, asked on Play · Not played yet", textOf(compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()))
    }

    @Test
    fun aLongEmulatorNameIsCutAndThePlayFactsStayAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((emulator, words) in listOf(EMULATOR_NOT_CHOSEN to "Not chosen yet, asked on Play", EMULATOR_NONE to "None installed")) {
                for ((name, game) in listOf("art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT), "no art" to DETAILS_GAME, "picking" to GAME_WITH_STAR)) {
                    show(game.copy(emulator = emulator), PlumThemeSettings.Default.copy(textSize = textSize))
                    val label = "$size $textSize $name $words"
                    val line = compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()
                    assertNoTextCut(line, label)
                    val text = textOf(line)
                    assertTrue("$label: the play facts stay whole: '$text'", text.endsWith(" · Played yesterday · 3 h 20 min"))
                    assertTrue("$label: the emulator's words first, whole or cut: '$text'", text.startsWith(words) || text.substringBefore(" · ").endsWith("\u2026"))
                }
            }
        }
    }

    @Test
    fun aLongGenreIsCutAndTheRatingStaysAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT, details = LONG_GENRE_DETAILS), PlumThemeSettings.Default.copy(textSize = textSize))
            val label = "$size $textSize"
            val genre = all(SecondScreenTags.FACT_LINE).last()
            assertNoTextCut(genre, label)
            val text = textOf(genre)
            assertTrue("$label: the rating stays: '$text'", text.endsWith(" · 8.5/10"))
            assertTrue("$label: the genre is cut by measuring: '$text'", text.substringBefore(" · ").endsWith("\u2026"))
            assertInsideTheMargins(label)
        }
    }

    @Test
    fun thePlayedLineNamesTheEmulatorPlayStartsInTheInfoPanelsWords() {
        fun played() = textOf(compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode())
        val game = GAME_NEVER_PLAYED.withDetails(FULL_DETAILS, LANDSCAPE_FRONT).copy(isPlayKnown = true)
        show(game.copy(emulator = emulatorNamed("DuckStation")))
        assertEquals("DuckStation · Not played yet", played())
        show(game.copy(emulator = EmulatorFact(null)))
        assertEquals("None installed · Not played yet", played())
        show(game.copy(emulator = EmulatorFact(com.plumemu.game.EmulatorToStart("SwanStation", isInstalled = false))))
        assertEquals("SwanStation (not installed) · Not played yet", played())
        show(game.copy(emulator = EmulatorFact(com.plumemu.game.EmulatorToStart("", isInstalled = true, isNotChosenYet = true))))
        assertEquals("Not chosen yet, asked on Play · Not played yet", played())
        // No reader (the benchmark): no emulator part.
        show(game.copy(emulator = null))
        assertEquals("Not played yet", played())
        // A name too long for the line: one line, cut at its end, inside the margins.
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT, emulator = emulatorNamed("RetroArch (Snes9x 2010, the long core name)")), PlumThemeSettings.Default.copy(textSize = textSize))
            val line = compose.onNodeWithTag(SecondScreenTags.FACTS).fetchSemanticsNode()
            assertEquals("$size $textSize: one line", 1, layoutOf(line).lineCount)
            assertInsideTheMargins("$size $textSize long emulator")
        }
    }

    @Test
    fun theFileLineShowsTheLaunchedFileWithoutItsExtensionRightAboveThePlayedLine() {
        for ((name, game) in listOf("art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT), "no art" to DETAILS_GAME)) {
            show(game)
            val label = "$size $name"
            val file = compose.onNodeWithTag(SecondScreenTags.FILE_NAME).fetchSemanticsNode()
            assertEquals(label, "Super Metroid (Japan, USA) (En,Ja)", textOf(file))
            assertNoTextCut(file, label)
            assertEquals("$label: 4 above the played line", px(SecondScreenDimens.factLineGap), bounds(SecondScreenTags.FACTS).top - file.boundsInRoot.bottom, 0.5f)
            assertEquals("$label: on the left margin", px(dimens.margin), file.boundsInRoot.left, 0.5f)
            val description = bounds(SecondScreenTags.DESCRIPTION)
            if (name == "no art") {
                assertEquals("$label: 16 under the description", px(SecondScreenDimens.descriptionGap), file.boundsInRoot.top - description.bottom, 0.5f)
            } else {
                assertTrue("$label: 16 at least under the description", description.bottom + px(SecondScreenDimens.descriptionGap) <= file.boundsInRoot.top + 0.5f)
            }
            assertInsideTheMargins(label)
        }
    }

    @Test
    fun aLongFileNameIsOneLineCutAtItsEndAndTheOtherPartsStayAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((name, game) in listOf("art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT), "no art" to DETAILS_GAME)) {
                show(game.copy(fileName = LONG_FILE_NAME), PlumThemeSettings.Default.copy(textSize = textSize))
                val label = "$size $textSize $name long file name"
                assertFileLineShownOrGaveWayToTheDescription(label)
                all(SecondScreenTags.FILE_NAME).singleOrNull()?.let { file ->
                    val layout = layoutOf(file)
                    assertEquals("$label: one line", 1, layout.lineCount)
                    assertTrue("$label: cut at its end with \u2026", layout.isLineEllipsized(0))
                    assertEquals("$label: the file name without its extension", LONG_FILE_NAME.removeSuffix(".cue"), textOf(file))
                }
                assertEquals("$label: the top line", 1, all(SecondScreenTags.TOP).size)
                assertEquals("$label: the title", 1, all(SecondScreenTags.TITLE).size)
                assertEquals("$label: both fact lines", 2, all(SecondScreenTags.FACT_LINE).size)
                assertEquals("$label: the played line", 1, all(SecondScreenTags.FACTS).size)
                assertInsideTheMargins(label)
                assertNothingCutBeyondItsEllipsis(label, game.isFavorite)
            }
        }
    }

    @Test
    fun withoutAFileNameThereIsNoLineAndNoGap() {
        for ((name, game) in listOf("art" to DETAILS_GAME.copy(art = LANDSCAPE_FRONT), "no art" to DETAILS_GAME)) {
            for (missing in listOf(null, "", "  ")) {
                show(game.copy(fileName = missing))
                val label = "$size $name file name '$missing'"
                assertTrue("$label: no file line", all(SecondScreenTags.FILE_NAME).isEmpty())
                val played = bounds(SecondScreenTags.FACTS)
                val description = bounds(SecondScreenTags.DESCRIPTION)
                if (name == "no art") {
                    assertEquals("$label: the description 16 above the played line", px(SecondScreenDimens.descriptionGap), played.top - description.bottom, 0.5f)
                } else {
                    assertTrue("$label: 16 at least above the played line", description.bottom + px(SecondScreenDimens.descriptionGap) <= played.top + 0.5f)
                }
                assertInsideTheMargins(label)
            }
        }
    }

    @Test
    fun aFileNameAloneKeepsThePickingCard() {
        show(GAME_WITH_STAR.copy(fileName = DETAILS_FILE_NAME))
        assertTrue("no details card", all(SecondScreenTags.DETAILS).isEmpty())
        assertTrue("no file line on the picking card", all(SecondScreenTags.FILE_NAME).isEmpty())
        assertEquals(1, all(SecondScreenTags.BLOCK).size)
    }

    @Test
    fun theCardHasNoTextOutsideItsParts() {
        // Every text of the details card is one of its tagged parts: nothing else is drawn that the checks above miss.
        show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT))
        val tagged = listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.FACT_LINE, SecondScreenTags.DESCRIPTION, SecondScreenTags.FILE_NAME, SecondScreenTags.FACTS).sumOf { all(it).size }
        var texts = 0

        fun visit(node: SemanticsNode) {
            if (SemanticsProperties.Text in node.config) texts++
            node.children.forEach(::visit)
        }
        visit(compose.onRoot().fetchSemanticsNode())
        assertEquals(tagged, texts)
    }
}

private val CASES_BUT_NO_ART = listOf("portrait box", "landscape box", "long title, portrait box", "long title, landscape box", "long title, no art")

/**
 * Measured on the six fit cases (`<font> <case>`): on Roomy (the Thor) only Plum Serif
 * gives way (every case but "no art"); on Compact every font does in every case but "no art", and
 * Plum Serif in that one too. Since the type ramp (same day) Sans keeps it in two of those cases.
 */
private val GAVE_WAY_AT_LARGEST: Map<SecondSize, List<String>> =
    mapOf(
        SecondSize.Roomy to CASES_BUT_NO_ART.map { "Serif $it" },
        SecondSize.Compact to
            // Sans keeps its file line under a long title without art since the type ramp (Compact's
            // details title 22 → 20); beside a portrait box it kept it until Medium became the default weight
            // (the heavier text wraps one more line).
            (CASES_BUT_NO_ART - "long title, no art").map { "Sans $it" } +
            listOf("portrait box", "landscape box", "long title, portrait box", "long title, landscape box", "no art", "long title, no art").map { "Serif $it" } +
            CASES_BUT_NO_ART.map { "Mono $it" },
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenDetailsCardFitRoomyTest : SecondScreenDetailsCardFitBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenDetailsCardFitCompactTest : SecondScreenDetailsCardFitBase(SecondSize.Compact)
