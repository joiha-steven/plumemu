package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Thor's bottom screen is denser than its top screen (3.92 inch at 1240x1080 against 6 inch at 1920x1080) while Android gives it
 * almost the same density, so a dp there is smaller to the eye. Every second-screen text style must be at least as tall in
 * millimetres as the matching main-screen style, at every Text size.
 */
class SecondScreenPhysicalTest {
    private val top = ThorPanels.top
    private val bottom = ThorPanels.bottom
    private val scale = SecondScreenPhysical.thor

    @Test
    fun aDpOnTheBottomScreenIsAboutAEighthSmallerThanOnTheTop() {
        println("PHYSICAL top ${top.ppi} ppi, ${top.inchesPerDp * 25.4} mm per dp; bottom ${bottom.ppi} ppi, ${bottom.inchesPerDp * 25.4} mm per dp; scale $scale")
        assertEquals(367.1, top.ppi, 0.2)
        assertEquals(419.5, bottom.ppi, 0.2)
        assertTrue("the bottom dp is smaller", bottom.inchesPerDp < top.inchesPerDp)
        assertEquals(1.137f, scale, 0.003f)
    }

    @Test
    fun theScaleNeverShrinksAndIsCapped() {
        assertEquals(1f, SecondScreenPhysical.scale(top, top), 0f)
        assertEquals("a screen with bigger dp stays as authored", 1f, SecondScreenPhysical.scale(top, bottom), 0f)
        val absurd = PanelFacts(1240, 1080, 2.3f, 0.5)
        assertEquals(SecondScreenPhysical.MAX_SCALE, SecondScreenPhysical.scale(absurd, top), 0f)
    }

    @Test
    fun onlyTheThorsTwoPanelsAreKnownBySize() {
        assertEquals(6.0, ThorPanels.diagonalOf(1920, 1080)!!, 0.0)
        assertEquals(3.92, ThorPanels.diagonalOf(1240, 1080)!!, 0.0)
        assertNull(ThorPanels.diagonalOf(1280, 720))
    }

    /** One second-screen text: its authored sp (at text size 1), its font's size factor, and the main-screen role it must not be smaller than. */
    private class Style(
        val name: String,
        val secondSp: Float,
        val main: TypeRole,
        val secondFont: PlumFont? = null,
    )

    private val styles =
        listOf(
            // The game card.
            Style("game title", SecondScreenGameCardDimens.titleSize.value, TypeRole.GameTitle),
            Style("system code (PS2)", SecondScreenGameCardDimens.systemSize.value, TypeRole.Tag),
            Style("tile label (PLAY TIME · LAST 8 WEEKS)", SecondScreenGameCardDimens.labelSize.value, TypeRole.Tag),
            Style("tile figure", SecondScreenGameCardDimens.bigSize.value, TypeRole.DetailsTitleWide),
            Style("tile unit", SecondScreenGameCardDimens.unitSize.value, TypeRole.InfoLineMeta),
            Style("tile small line", SecondScreenGameCardDimens.smallSize.value, TypeRole.InfoLineMeta),
            Style("bar label (this wk)", SecondScreenGameCardDimens.barsLabelSize.value, TypeRole.Tag),
            Style("description", SecondScreenGameCardDimens.descriptionSize.value, TypeRole.InfoLineMeta),
            Style("battery line", SecondScreenGameCardDimens.footSize.value, TypeRole.InfoLineMeta),
            Style("file name (mono)", SecondScreenGameCardDimens.fileSize.value, TypeRole.InfoLineMeta, PlumFont.Mono),
            // The system, Recent and Favorites cards.
            Style("entry title", EntryCardDimens.headerTitle.value, TypeRole.GameTitle),
            Style("entry count", EntryCardDimens.headerMeta.value, TypeRole.InfoLineMeta),
            Style("entry label", EntryCardDimens.label.value, TypeRole.Tag),
            Style("entry figure", EntryCardDimens.big.value, TypeRole.DetailsTitleWide),
            Style("entry unit", EntryCardDimens.unit.value, TypeRole.InfoLineMeta),
            Style("entry of", EntryCardDimens.of.value, TypeRole.InfoLineMeta),
            Style("entry small", EntryCardDimens.small.value, TypeRole.InfoLineMeta),
            Style("entry status", EntryCardDimens.status.value, TypeRole.InfoLineMeta),
            Style("entry foot", EntryCardDimens.aboutText.value, TypeRole.InfoLineMeta),
            Style("cover caption when", EntryCardDimens.captionWhen.value, TypeRole.InfoLineMeta),
            Style("cover caption system", EntryCardDimens.captionSystem.value, TypeRole.InfoLineMeta),
            Style("week day", EntryCardDimens.weekDay.value, TypeRole.Tag),
        ) +
            // The shared roles of the other cards: the smallest main-screen reading size is the game title's.
            TypeRole.entries.filter { it.name.startsWith("Second") }.map { Style(it.name, it.sizeSp, TypeRole.GameTitle) }

    @Test
    fun everySecondScreenStyleIsAtLeastAsBigAsItsMainScreenStyleAtEveryTextSize() {
        val problems = mutableListOf<String>()
        for (size in TextSize.entries) {
            for (pair in styles) {
                val mainFont = PlumFont.Jost
                val secondFont = pair.secondFont ?: mainFont
                // Text size and font factor apply on both screens; the second screen's window adds the scale on top.
                val secondSp = pair.secondSp * secondFont.sizeFactor * size.factor * scale
                val mainSp = pair.main.scaledSizeSp(mainFont, size)
                val secondMm = bottom.millimetres(secondSp)
                val mainMm = top.millimetres(mainSp)
                if (secondMm + 1e-9 < mainMm) problems += "$size ${pair.name}: ${"%.3f".format(secondMm)} mm < main ${pair.main} ${"%.3f".format(mainMm)} mm"
            }
        }
        assertTrue("${problems.size} second-screen styles smaller than the main screen's:\n" + problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theOwnersWorstCasesWereSmallerBeforeAndAreNotNow() {
        // 8 sp (the label, "this wk") and 10 sp at the bottom screen's dp against the tag's 11.33 on the top screen's.
        val tagMm = top.millimetres(TypeRole.Tag.sizeSp)
        assertTrue("the old label 8 sp was under the tag", bottom.millimetres(8f * scale) < tagMm)
        assertTrue("the new label is not", bottom.millimetres(SecondScreenGameCardDimens.labelSize.value * scale) >= tagMm)
        assertTrue("the new 'this wk' is not", bottom.millimetres(SecondScreenGameCardDimens.barsLabelSize.value * scale) >= tagMm)
        assertTrue("the new PS2 is not", bottom.millimetres(SecondScreenGameCardDimens.systemSize.value * scale) >= tagMm)
        assertTrue(
            "the new mono file name is not",
            bottom.millimetres(SecondScreenGameCardDimens.fileSize.value * PlumFont.Mono.sizeFactor * scale) >= tagMm,
        )
    }
}
