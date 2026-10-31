package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.toRgb
import com.plumemu.settings.toThemeSettings
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hue, Saturation and Lightness sliders under the "+" palette: the D-pad
 * order and steps, only readable values for Text and Accent, the skip over refused background
 * tones, B and A, the sounds, and taps and drags on a track.
 */
internal class SettingsColorSlidersTest : SettingsModelHarness() {
    private val reported = Reported().also { it.listenTo(model.feel) }

    private fun heard(direction: Direction) = press(Action.Move(direction)).let { actions.settings; reported.drain() }

    private fun background(): Rgb = actions.settings.toThemeSettings().backgroundColor

    private fun openBackground() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
    }

    private fun openText() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.TextCustom, model.focus.page)
    }

    private fun openAccent() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.AccentCustom, model.focus.page)
    }

    private val cells: Int get() = ColorPages.palette(model.focus.page!!, background()).size

    private fun onSlider(slider: ColorSlider) {
        // Down from wherever the cursor is, through the grid, to the slider.
        var guard = 0
        while (model.focus.panelIndex != cells + slider.ordinal && guard++ < 20) move(Direction.Down)
        assertEquals(cells + slider.ordinal, model.focus.panelIndex)
    }

    private fun palettePage() = model.focus.page!!

    @Test
    fun downFromTheGridReachesHueSaturationLightnessThenColorCodeAndUpGoesBack() {
        openBackground()
        onSlider(ColorSlider.Hue)
        move(Direction.Down)
        assertEquals("Saturation", cells + 1, model.focus.panelIndex)
        move(Direction.Down)
        assertEquals("Lightness", cells + 2, model.focus.panelIndex)
        move(Direction.Down)
        assertEquals("Color code", cells + 3, model.focus.panelIndex)
        assertTrue(model.isHexRow(palettePage(), model.focus.panelIndex))
        move(Direction.Down)
        assertEquals("Down at the end stays", cells + 3, model.focus.panelIndex)
        repeat(3) { move(Direction.Up) }
        assertEquals("Hue", cells, model.focus.panelIndex)
        move(Direction.Up)
        assertEquals("the last row of the grid", (cells - 1) / ColorPages.PALETTE_COLUMNS, model.focus.panelIndex / ColorPages.PALETTE_COLUMNS)
    }

    @Test
    fun rightAndLeftStepHueBySixDegreesAndTheOthersByFourPercent() {
        openBackground()
        for ((slider, step) in listOf(ColorSlider.Hue to 6, ColorSlider.Saturation to 4, ColorSlider.Lightness to 4)) {
            onSlider(slider)
            val start = model.sliderHsl[slider]
            move(Direction.Right)
            assertEquals("$slider right", Math.floorDiv(start, step) * step + step, model.sliderHsl[slider])
            val after = model.sliderHsl[slider]
            move(Direction.Right)
            assertEquals("$slider right again", after + step, model.sliderHsl[slider])
            move(Direction.Left)
            assertEquals("$slider left", after, model.sliderHsl[slider])
            assertEquals("the colour written is the thumb's", Rgb(model.sliderHsl.rgb().value), (appearance.background as ColorSetting.Custom).let { Rgb(it.rgb) })
            assertEquals("still on the page", PanelPage.BackgroundCustom, model.focus.page)
        }
    }

    @Test
    fun heldRightKeepsSteppingEachRepeatOneStep() {
        openBackground()
        onSlider(ColorSlider.Hue)
        val start = model.sliderHsl.hue
        repeat(10) { move(Direction.Right) }
        assertEquals(Math.floorDiv(start, 6) * 6 + 60, model.sliderHsl.hue)
    }

    @Test
    fun leftAtTheStartOfASliderDoesNothingAndDoesNotLeaveThePage() {
        openBackground()
        onSlider(ColorSlider.Hue)
        repeat(70) { move(Direction.Left) }
        assertEquals(0, model.sliderHsl.hue)
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        reported.drain()
        assertEquals("a press at the end reports nothing", emptyList<Outcome>(), heard(Direction.Left))
    }

    @Test
    fun leftStillLeavesFromTheGridsLeftmostTile() {
        openBackground()
        repeat(10) { move(Direction.Left) }
        assertNotEquals(PanelPage.BackgroundCustom, model.focus.page)
    }

    @Test
    fun aStepMovesAndMovingOntoASliderMoves() {
        openBackground()
        reported.drain()
        onSlider(ColorSlider.Hue)
        assertTrue("onto Hue", Outcome.Moved in reported.drain())
        assertEquals("a step", listOf(Outcome.Moved), heard(Direction.Right))
    }

    @Test
    fun textThumbNeverStopsOnAColourThatDoesNotReadAndLightnessStopsAtTheEdgeOfWhatReads() {
        openText()
        onSlider(ColorSlider.Lightness)
        val bg = background()
        var steps = 0
        while (steps < 40) {
            val before = model.sliderHsl.lightness
            move(Direction.Left)
            if (model.sliderHsl.lightness == before) break
            steps++
            actions.settings
            assertTrue("text ${appearance.textColor.toRgb().toHex()} reads at ${model.sliderHsl}", Readability.passes(appearance.textColor.toRgb(), bg, ColorRole.Text))
        }
        assertTrue("it stopped before black: ${model.sliderHsl}", model.sliderHsl.lightness > 0)
        reported.drain()
        assertEquals("the end reports nothing", emptyList<Outcome>(), heard(Direction.Left))
        val stop = model.sliderHsl
        repeat(30) { move(Direction.Right) }
        assertTrue(model.sliderHsl.lightness >= stop.lightness)
        assertTrue(Readability.passes(model.sliderHsl.rgb(), bg, ColorRole.Text))
    }

    @Test
    fun accentThumbNeverStopsBelowThreeToOne() {
        openAccent()
        onSlider(ColorSlider.Lightness)
        val bg = background()
        repeat(40) {
            move(Direction.Left)
            actions.settings
            assertTrue(Readability.passes(model.sliderHsl.rgb(), bg, ColorRole.Accent))
        }
        assertTrue(model.sliderHsl.lightness > 0)
        repeat(40) {
            move(Direction.Right)
            assertTrue(Readability.passes(model.sliderHsl.rgb(), bg, ColorRole.Accent))
        }
    }

    @Test
    fun backgroundLightnessSkipsTheMidTonesNoTextReadsOn() {
        openBackground()
        onSlider(ColorSlider.Lightness)
        val stops = mutableListOf(model.sliderHsl.lightness)
        repeat(30) {
            move(Direction.Right)
            if (model.sliderHsl.lightness != stops.last()) stops += model.sliderHsl.lightness
            assertTrue("${model.sliderHsl} may carry text", Readability.backgroundAllowsText(model.sliderHsl.rgb()))
        }
        assertTrue("a jump over the refused part: $stops", stops.zipWithNext().any { (a, b) -> b - a > 4 })
        assertTrue("it reached the pale end", stops.last() > 80)
    }

    @Test
    fun bPutsBackTheColourBeforeThePage() {
        openBackground()
        onSlider(ColorSlider.Lightness)
        repeat(6) { move(Direction.Right) }
        actions.settings
        assertNotEquals(ColorSetting.Preset("Graphite"), appearance.background)
        press(Action.Back)
        assertEquals(ColorSetting.Preset("Graphite"), appearance.background)
    }

    @Test
    fun aChoosesTheColourShown() {
        openBackground()
        onSlider(ColorSlider.Saturation)
        repeat(3) { move(Direction.Right) }
        val shown = actions.settings.appearance.background
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(shown, actions.settings.appearance.background)
    }

    @Test
    fun aTapOnATrackSetsTheValueThereAndFocusesTheRow() {
        openBackground()
        val hue = cells
        model.onTouchSlider(hue, 0.5f, actions.settings, wide)
        assertEquals(hue, model.focus.panelIndex)
        assertEquals(180, model.sliderHsl.hue)
        model.onTouchSliderEnd()
        actions.settings
        assertEquals(ColorSetting.Custom(model.sliderHsl.rgb().value), appearance.background)
        model.onTouchSlider(hue, 0.25f, actions.settings, wide)
        assertEquals("a drag moves it", 90, model.sliderHsl.hue)
        model.onTouchSlider(hue, 1f, actions.settings, wide)
        assertEquals(360, model.sliderHsl.hue)
    }

    @Test
    fun aTapOnAnUnreadableStretchLandsOnTheNearestValueThatReads() {
        openText()
        val lightness = cells + 2
        model.onTouchSlider(lightness, 0f, actions.settings, wide)
        actions.settings
        assertTrue(model.sliderHsl.lightness > 0)
        assertTrue(Readability.passes(appearance.textColor.toRgb(), background(), ColorRole.Text))
        reported.drain()
    }

    @Test
    fun aTapSoundsAMoveAndAnUnchangedValueIsSilent() {
        openBackground()
        reported.drain()
        model.onTouchSlider(cells, 0.5f, actions.settings, wide)
        assertTrue(Outcome.Moved in reported.drain())
        model.onTouchSlider(cells, 0.5f, actions.settings, wide)
        assertEquals(emptyList<Outcome>(), reported.drain())
    }

    @Test
    fun leavingTheSlidersForTheHexRowKeepsTheColourTunedAndBPutsTheOpeningOneBack() {
        // Reaching the hex row does not put the opening colour back; only B does.
        openBackground()
        onSlider(ColorSlider.Hue)
        repeat(5) { move(Direction.Right) }
        val tuned = background()
        assertNotEquals(ColorSetting.Preset("Graphite"), appearance.background)
        repeat(3) { move(Direction.Down) }
        assertEquals(tuned, background())
        assertEquals(tuned, model.sliderHsl.rgb())
        press(Action.Back)
        press(Action.Back)
        assertEquals(ColorSetting.Preset("Graphite"), appearance.background)
    }

    @Test
    fun aTypedCodeIsWhatTheSlidersShowAndStepFrom() {
        openBackground()
        toHexRow()
        model.onHexTextChange("1A3A5A")
        model.onHexEntered(model.hexText)
        val typed = Hsl.of(Rgb(0x1A3A5A))
        assertEquals(typed, model.sliderHsl)
        move(Direction.Up) // Lightness
        move(Direction.Right)
        val stepped = model.sliderHsl
        assertEquals(typed.hue, stepped.hue)
        assertEquals(typed.saturation, stepped.saturation)
        // A step lands on the next multiple of the step from the typed value, not from the old colour.
        assertEquals((typed.lightness / ColorSlider.Lightness.step + 1) * ColorSlider.Lightness.step, stepped.lightness)
    }

    @Test
    fun theSlidersShowTheColourOnOpeningAndFollowTheGridCursor() {
        openBackground()
        assertEquals("on opening: the current colour", Hsl.of(background()), model.sliderHsl)
        move(Direction.Right)
        move(Direction.Up)
        assertTrue(model.focus.panelIndex < cells)
        val swatch = ColorPages.palette(palettePage(), background())[model.focus.panelIndex]
        assertEquals(Hsl.of(swatch), model.sliderHsl)
        assertEquals(swatch, background())
    }

    @Test
    fun aFingerDragsAtFineResolutionNotInDpadSteps() {
        openBackground()
        val hue = cells
        model.onTouchSlider(hue, 0.5f, actions.settings, wide)
        assertEquals(180, model.sliderHsl.hue)
        model.onTouchSlider(hue, 0.5f + 1f / 360f, actions.settings, wide)
        assertEquals("one hue track unit of 1/360 moves hue by 1, not 6", 181, model.sliderHsl.hue)
        model.onTouchSlider(cells + 1, 0.37f, actions.settings, wide)
        assertEquals("saturation by 1 %", 37, model.sliderHsl.saturation)
    }

    @Test
    fun theGamepadStepIsStillSixAndFour() {
        assertEquals(6, ColorSlider.Hue.step)
        assertEquals(4, ColorSlider.Saturation.step)
        assertEquals(4, ColorSlider.Lightness.step)
    }

    @Test
    fun theThumbFollowsTheFingerAcrossAnUnreadableStretchWhileThePreviewAlwaysReads() {
        openText()
        val lightness = cells + 2
        val bg = background()
        for (i in 0..100) {
            val fraction = i / 100f
            model.onTouchSlider(lightness, fraction, actions.settings, wide)
            assertEquals("thumb at the finger", ColorSlider.Lightness to fraction, model.sliderFinger)
            assertTrue("preview at $fraction reads", Readability.passes(model.sliderHsl.rgb(), bg, ColorRole.Text))
            assertTrue("shown text at $fraction reads", Readability.passes(actions.colorPreview.appearance!!.textColor.toRgb(), bg, ColorRole.Text))
        }
    }

    @Test
    fun releaseSettlesTheThumbOnTheValueInEffect() {
        openText()
        val lightness = cells + 2
        model.onTouchSlider(lightness, 0f, actions.settings, wide)
        assertEquals(0f, model.sliderFinger?.second)
        assertTrue("moved off black to a value that reads", model.sliderHsl.lightness > 0)
        model.onTouchSliderEnd()
        assertEquals("no finger: the thumb is drawn at the value", null, model.sliderFinger)
        assertTrue(Readability.passes(model.sliderHsl.rgb(), background(), ColorRole.Text))
    }

    private val preview get() = actions.colorPreview

    @Test
    fun aDragOfManyMovesWritesTheStoreOnceOnReleaseWithTheFinalValue() {
        openBackground()
        val hue = cells
        val snapshot = actions.settings
        val before = actions.writes
        for (i in 0..360) model.onTouchSlider(hue, i / 360f, snapshot, wide)
        assertEquals("no write while the finger moves", before, actions.writes)
        model.onTouchSliderEnd()
        assertEquals("one write when it lifts", before + 1, actions.writes)
        actions.settings
        assertEquals(360, model.sliderHsl.hue)
        assertEquals(ColorSetting.Custom(model.sliderHsl.rgb().value), appearance.background)
    }

    @Test
    fun theThemeShowsTheDraggedColourBeforeReleaseAndTheStoreStillHoldsTheOldOne() {
        openBackground()
        val hue = cells
        val stored = actions.settings.appearance.background
        model.onTouchSlider(hue, 0.25f, actions.settings, wide)
        val dragged = ColorSetting.Custom(model.sliderHsl.rgb().value)
        assertEquals(dragged, preview.appearance!!.background)
        assertEquals("the store is untouched", stored, actions.settings.appearance.background)
        assertEquals("the screen's settings take the overlay", dragged, preview.over(actions.settings).appearance.background)
        model.onTouchSliderEnd()
        assertEquals("kept until the store shows it", dragged, preview.appearance!!.background)
        assertTrue(preview.isReleased)
        preview.settleAgainst(actions.settings.appearance)
        assertEquals("settled once the store holds the same colours", null, preview.appearance)
    }

    @Test
    fun theOverlayCarriesOnlyTheThreeColoursSoOtherAppearanceChangesShowAtOnce() {
        openBackground()
        model.onTouchSlider(cells, 0.25f, actions.settings, wide)
        model.onTouchSliderEnd()
        val stored = actions.settings
        val changed = stored.withAppearance { it.copy(font = com.plumemu.coredata.settings.FontSetting.entries.last()) }
        assertEquals("a font changed meanwhile shows", changed.appearance.font, preview.over(changed).appearance.font)
        assertEquals("the dragged colour still shows", preview.appearance!!.background, preview.over(changed).appearance.background)
        preview.settleAgainst(preview.appearance!!.copy(font = changed.appearance.font))
        assertEquals("the same three colours settle it whatever else differs", null, preview.appearance)
    }

    @Test
    fun aReleasedPreviewThatTheStoreNeverMatchesIsGivenUpOn() {
        openBackground()
        model.onTouchSlider(cells, 0.25f, actions.settings, wide)
        model.onTouchSliderEnd()
        preview.settleAgainst(actions.settings.appearance.copy(accent = ColorSetting.Custom(0x123456)))
        assertTrue("a different store does not settle it", preview.isActive)
        preview.clear()
        assertEquals(null, preview.appearance)
        assertEquals(false, preview.isReleased)
    }

    @Test
    fun bMidDragPutsBackTheColourBeforeThePageAndTheLaterLiftWritesNothingMore() {
        openBackground()
        val opening = actions.settings.appearance.background
        model.onTouchSlider(cells, 0.6f, actions.settings, wide)
        assertNotEquals(opening, preview.appearance!!.background)
        press(Action.Back)
        press(Action.Back)
        assertEquals("the screen shows the opening colour again", opening, preview.appearance?.background)
        val writes = actions.writes
        model.onTouchSliderEnd()
        assertEquals("the lift after B writes nothing more", writes, actions.writes)
        assertEquals(opening, actions.settings.appearance.background)
    }

    @Test
    fun aCancelMidDragKeepsTheLastShownColourAndWritesOnce() {
        openAccent()
        val before = actions.writes
        for (i in 0..50) model.onTouchSlider(cells, i / 100f, actions.settings, wide)
        val shown = preview.appearance!!.accent
        model.onTouchSliderEnd() // a cancelled gesture reports the same end
        assertEquals(before + 1, actions.writes)
        assertEquals(shown, actions.settings.appearance.accent)
    }

    @Test
    fun theScreenGoingWithAFingerDownLeavesTheStoreAsLeavingThePageDoes() {
        openAccent()
        val opening = actions.settings.appearance.accent
        model.onTouchSlider(cells, 0.4f, actions.settings, wide)
        model.onDispose()
        assertEquals("not chosen: the opening colour is back", opening, actions.settings.appearance.accent)
        assertEquals("and the screen shows it too", opening, preview.appearance?.accent ?: opening)
        assertTrue("never an overlay left waiting for a lift that happened", preview.isReleased || !preview.isActive)
        preview.settleAgainst(actions.settings.appearance)
        assertEquals("and it settles once the store has it", null, preview.appearance)
    }

    @Test
    fun aStepAfterTheLiftKeepsTheOverlayReleasedSoItCanSettle() {
        openBackground()
        model.onTouchSlider(cells, 0.3f, actions.settings, wide)
        model.onTouchSliderEnd()
        move(Direction.Right)
        assertTrue(preview.isActive)
        assertTrue("a write with no finger down is released", preview.isReleased)
        preview.settleAgainst(actions.settings.appearance)
        assertEquals(null, preview.appearance)
    }

    @Test
    fun bMidDragLeavesNoOverlayWaitingForTheLift() {
        openBackground()
        model.onTouchSlider(cells, 0.6f, actions.settings, wide)
        press(Action.Back)
        press(Action.Back)
        assertTrue(preview.isReleased || !preview.isActive)
        preview.settleAgainst(actions.settings.appearance)
        assertEquals(null, preview.appearance)
    }

    @Test
    fun theScreenGoingWithAFingerDownWritesOnlyThePutBackWhenNotChosen() {
        openAccent()
        val before = actions.writes
        model.onTouchSlider(cells, 0.4f, actions.settings, wide)
        model.onDispose()
        assertEquals("only the put-back", before + 1, actions.writes)
    }

    @Test
    fun theScreenGoingWithAFingerDownAfterAChoiceWritesTheShownColour() {
        openAccent()
        model.onTouchSlider(cells, 0.4f, actions.settings, wide)
        val shown = preview.appearance!!.accent
        press(Action.Enter)
        model.onDispose()
        assertEquals(shown, actions.settings.appearance.accent)
    }

    @Test
    fun aStepDuringADragEndsWithTheStoreEqualToWhatIsShown() {
        openBackground()
        val hue = cells
        model.onTouchSlider(hue, 0.2f, actions.shown, wide)
        move(Direction.Right)
        assertEquals("the screen follows the step", ColorSetting.Custom(model.sliderHsl.rgb().value), preview.appearance!!.background)
        model.onTouchSliderEnd()
        actions.settings
        assertEquals(ColorSetting.Custom(model.sliderHsl.rgb().value), appearance.background)
        assertEquals(preview.appearance!!.background, appearance.background)
    }

    @Test
    fun aTapOnATrackWritesOnceWhenTheFingerLifts() {
        openBackground()
        val before = actions.writes
        model.onTouchSlider(cells, 0.5f, actions.settings, wide)
        assertEquals(before, actions.writes)
        model.onTouchSliderEnd()
        assertEquals(before + 1, actions.writes)
    }

    @Test
    fun theTrackDrawsTheRunsOfWholeValuesAFingerCanLandOn() {
        openText()
        val reach = ColorPages.sliderReach(palettePage(), background())
        val others = model.sliderHsl.with(ColorSlider.Lightness, 0)
        val runs = reach.drawnStretches(others, ColorSlider.Lightness)
        assertTrue("some values read", runs.isNotEmpty())
        for (value in 0..ColorSlider.Lightness.max) {
            val drawn = runs.any { value in it }
            assertEquals("lightness $value drawn only if it can be stopped on", reach.reaches(others, ColorSlider.Lightness, value), drawn)
        }
        // A finger released anywhere settles inside a drawn run.
        for (i in 0..100) {
            val landed = reach.nearest(others, ColorSlider.Lightness, i / 100f)!!.lightness
            assertTrue("lightness $landed is drawn", runs.any { landed in it })
        }
    }

    @Test
    fun aDragThenAStepThenADragLeavesTheStoreOnTheSliderValue() {
        openBackground()
        val hue = cells
        model.onTouchSlider(hue, 0.2f, actions.shown, wide)
        move(Direction.Right)
        model.onTouchSlider(hue, 0.8f, actions.shown, wide)
        model.onTouchSliderEnd()
        actions.settings
        assertEquals(ColorSetting.Custom(model.sliderHsl.rgb().value), appearance.background)
    }

    @Test
    fun aDragOnAReopenedPageBeforeTheQueueRunsLeavesTheStoreOnTheSliderValue() {
        openAccent()
        val hue = cells
        model.onTouchSlider(hue, 0.3f, actions.shown, wide)
        press(Action.Back)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.AccentCustom, model.focus.page)
        model.onTouchSlider(cells, 0.7f, actions.shown, wide)
        model.onTouchSliderEnd()
        actions.settings
        assertEquals(ColorSetting.Custom(model.sliderHsl.rgb().value), appearance.accent)
    }

    @Test
    fun aFastDragSoundsOncePerDpadStepOfTravelAtMost() {
        openBackground()
        reported.drain()
        val hue = cells
        for (i in 0..360) model.onTouchSlider(hue, i / 360f, actions.settings, wide)
        val sounds = reported.drain().count { it == Outcome.Moved }
        assertTrue("$sounds sounds for 360 degrees of hue, at most one per 6 degrees", sounds in 1..61)
        model.onTouchSliderEnd()
        reported.drain()
        model.onTouchSlider(hue, 0.5f, actions.settings, wide)
        assertTrue("a new touch sounds again", Outcome.Moved in reported.drain())
    }
}
