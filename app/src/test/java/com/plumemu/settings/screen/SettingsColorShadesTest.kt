package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.settings.toThemeSettings
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Color strip and the Shade row of a colour page: the walk, the preview of the same shade
 * place on the next stop, the ends, taps, and a finger's colour shown through the overlay.
 */
internal class SettingsColorShadesTest : SettingsModelHarness() {
    init {
        startOnRed() // the accent presets are walked from Red
    }

    private val reported = Reported().also { it.listenTo(model.feel) }

    private val preview get() = actions.colorPreview

    private fun background(): Rgb = actions.settings.toThemeSettings().backgroundColor

    private fun open(option: SettingsOption) {
        openSection(SettingsSection.Appearance)
        focusOption(option)
        press(Action.Enter)
    }

    private fun shades() = model.colorShades(colourPage)

    /** The shade the page wrote: the one of the strip's stop at [place] of its row. */
    private fun shadeAt(place: Int) = ColorSetting.Custom(shades()[place].value)

    @Test
    fun everyShadeOfEveryStopPassesTheCheckOfItsSetting() {
        for ((option, role) in listOf(SettingsOption.Background to ShadeRole.Background, SettingsOption.TextColor to ShadeRole.Text, SettingsOption.Accent to ShadeRole.Accent)) {
            val bg = ColorPages.backgroundOf(actions.shown)
            for (stop in 0 until ColorShades.stopCount(role)) {
                for (shade in ColorShades.shades(role, stop, bg)) {
                    val reads =
                        when (role) {
                            ShadeRole.Background -> Readability.backgroundAllowsText(shade)
                            ShadeRole.Text -> Readability.passes(shade, bg, ColorRole.Text)
                            ShadeRole.Accent -> Readability.passes(shade, bg, ColorRole.Accent)
                        }
                    assertTrue("$option stop $stop: ${shade.toHex()} reads on ${bg.toHex()}", reads)
                }
            }
        }
    }

    @Test
    fun leftAndRightOnColorPreviewTheSameShadePlaceOnTheNextStopAndStopAtTheEnds() {
        open(SettingsOption.Accent)
        toColor()
        val stops = ColorShades.stopCount(ShadeRole.Accent)
        repeat(stops) { move(Direction.Left) }
        assertEquals("Left stops at the first stop", 0, model.colorStop)
        assertEquals(ColorPart.Color, model.colourPart)
        move(Direction.Right)
        assertEquals(1, model.colorStop)
        val first = shades().size / 2
        assertEquals("the middle shade of the row at first", shadeAt(first), appearance.accent)
        move(Direction.Right)
        assertEquals("the same place on the next stop", shadeAt(first), appearance.accent)
        repeat(stops) { move(Direction.Right) }
        assertEquals("Right stops at the last stop", stops - 1, model.colorStop)
        val last = appearance.accent
        move(Direction.Right)
        assertEquals("nothing moves past the end", last, appearance.accent)
    }

    @Test
    fun backgroundAndTextStripsStartWithAGreyStop() {
        assertEquals(ColorShades.HUE_COUNT + 1, ColorShades.stopCount(ShadeRole.Background))
        assertEquals(ColorShades.HUE_COUNT + 1, ColorShades.stopCount(ShadeRole.Text))
        assertEquals(ColorShades.HUE_COUNT, ColorShades.stopCount(ShadeRole.Accent))
        open(SettingsOption.Background)
        toColor()
        repeat(ColorShades.stopCount(ShadeRole.Background)) { move(Direction.Left) }
        assertTrue("the first stop is grey", ColorShades.isGrey(ShadeRole.Background, model.colorStop))
        val rgb = (appearance.background as ColorSetting.Custom).rgb
        assertTrue("and writes a grey", Rgb(rgb).let { it.red == it.green && it.green == it.blue })
    }

    @Test
    fun theShadePlaceIsKeptAcrossStopsAndShadesWalkAndPreview() {
        open(SettingsOption.Accent)
        toShade()
        val count = shades().size
        repeat(count) { move(Direction.Right) }
        assertEquals("Right stops at the last shade", model.shadeIndex(colourPage, count - 1), model.focus.panelIndex)
        assertEquals(shadeAt(count - 1), appearance.accent)
        repeat(count) { move(Direction.Left) }
        assertEquals("Left stops at the first shade", model.shadeIndex(colourPage, 0), model.focus.panelIndex)
        assertEquals(shadeAt(0), appearance.accent)
        move(Direction.Right)
        move(Direction.Up)
        move(Direction.Right)
        assertEquals("Color kept the place (the second shade)", shadeAt(1), appearance.accent)
        move(Direction.Down)
        assertEquals("Down comes back to that shade", model.shadeIndex(colourPage, 1), model.focus.panelIndex)
        move(Direction.Down)
        assertEquals(ColorPart.Hex, model.colourPart)
        assertEquals("the Color code row keeps the colour", shadeAt(1), appearance.accent)
        move(Direction.Up)
        assertEquals("Up from the Color code row comes back to the shade it came from", model.shadeIndex(colourPage, 1), model.focus.panelIndex)
    }

    @Test
    fun aHeldUpFromTheColorCodeRowStopsAtColor() {
        open(SettingsOption.Accent)
        toHexRow()
        repeat(6) { press(Action.Move(Direction.Up, isRepeat = true)) }
        assertEquals(ColorPart.Color, model.colourPart)
    }

    @Test
    fun aHeldDownFromThePresetsDoesNotCrossIntoColorAndAHeldLeftOnColorStaysThere() {
        open(SettingsOption.Accent)
        repeat(8) { press(Action.Move(Direction.Down, isRepeat = true)) }
        assertEquals(ColorPart.Preset, model.colourPart)
        toColor()
        move(Direction.Left) // a press of its own, not a repeat of the one that came down
        repeat(40) { press(Action.Move(Direction.Left, isRepeat = true)) }
        assertEquals(ColorPart.Color, model.colourPart)
        assertEquals("a held Left walks the strip to its start and stays on Color", 0, model.colorStop)
    }

    @Test
    fun aStepOnColorSoundsAMoveAndNothingAtTheEnd() {
        open(SettingsOption.Accent)
        toColor()
        reported.drain()
        move(Direction.Right)
        assertTrue(Outcome.Moved in reported.drain())
        repeat(ColorShades.stopCount(ShadeRole.Accent)) { move(Direction.Right) }
        reported.drain()
        move(Direction.Right)
        assertFalse(Outcome.Moved in reported.drain())
    }

    @Test
    fun anOwnAccentOpensOnItsNearestShadeAndTextLikeIt() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(accent = ColorSetting.Custom(0x336699)))
        val writes = actions.writes
        open(SettingsOption.Accent)
        val (stop, shade) = ColorShades.nearestStopAndShade(ShadeRole.Accent, Rgb(0x336699), ColorPages.backgroundOf(actions.shown))
        assertEquals(ColorPart.Shade, model.colourPart)
        assertEquals(stop, model.colorStop)
        assertEquals(model.shadeIndex(colourPage, shade), model.focus.panelIndex)
        assertEquals("nothing was written", writes, actions.writes)
        assertEquals(ColorSetting.Custom(0x336699), appearance.accent)
    }

    @Test
    fun aTapOnAShadeFocusesAndPreviewsItAndASecondTapKeepsIt() {
        open(SettingsOption.Background)
        toColor()
        val second = model.shadeIndex(colourPage, 1)
        model.onTapPanelItem(second, actions.shown, wide)
        assertEquals(second, model.focus.panelIndex)
        val shade = shadeAt(1)
        assertEquals(shade, appearance.background)
        assertEquals("still on the page", PanelPage.BackgroundPresets, model.focus.page)
        model.onTapPanelItem(second, actions.shown, wide)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
        assertEquals(shade, appearance.background)
    }

    @Test
    fun aTapOnColorSetsTheStopUnderTheFingerAndFocusesTheRowWithoutChoosing() {
        open(SettingsOption.Accent)
        reported.drain()
        model.onTouchStop(5, actions.shown, wide)
        assertEquals(ColorPart.Color, model.colourPart)
        assertEquals(5, model.colorStop)
        assertEquals(PanelPage.AccentPresets, model.focus.page)
        assertEquals(shadeAt(shades().size / 2), appearance.accent)
        assertTrue(Outcome.Moved in reported.drain())
        model.onTouchStop(5, actions.shown, wide)
        assertEquals("the same stop again changes nothing and is silent", emptyList<Outcome>(), reported.drain())
        model.onTapPanelItem(model.colorIndex(colourPage), actions.shown, wide)
        assertEquals("a tap on the row's label does not choose", PanelPage.AccentPresets, model.focus.page)
        press(Action.Back)
        assertOnColorsRow(COLORS_ACCENT_ITEM)
        assertEquals("B puts back what was in effect when the cursor came to Color", ColorSetting.Preset("Red"), appearance.accent)
    }

    @Test
    fun accentBySystemIsStillReachableAndDownFromItGoesToColor() {
        open(SettingsOption.Accent)
        // Down a row, then Right along it (the By system tile is the last preset): bounded, so a layout that never gets there fails instead of looping.
        repeat(20) {
            if (model.focus.panelIndex == ColorPages.bySystemIndex) return@repeat
            val before = model.focus.panelIndex
            move(Direction.Right)
            if (model.focus.panelIndex == before) move(Direction.Down)
        }
        assertEquals(ColorPages.bySystemIndex, model.focus.panelIndex)
        assertTrue(appearance.accent is ColorSetting.BySystem)
        move(Direction.Down)
        assertEquals(ColorPart.Color, model.colourPart)
        press(Action.Back)
        assertTrue("B puts By system back", appearance.accent is ColorSetting.BySystem)
    }

    // A finger's colour is shown through the overlay and stored once on lift (the strip's drag is wired to touch later; the model side is here).

    @Test
    fun aDragOfManyMovesWritesTheStoreOnceOnLiftWithTheFinalColour() {
        open(SettingsOption.Background)
        val snapshot = actions.settings
        val before = actions.writes
        for (stop in 0 until ColorShades.stopCount(ShadeRole.Background)) model.onTouchStop(stop, snapshot, wide, drag = true)
        assertEquals("no write while the finger moves", before, actions.writes)
        model.onTouchStopEnd()
        assertEquals("one write when it lifts", before + 1, actions.writes)
        actions.settings
        assertEquals(ColorSetting.Custom(shades()[shades().size / 2].value), appearance.background)
    }

    @Test
    fun theThemeShowsTheDraggedColourBeforeLiftAndTheStoreStillHoldsTheOldOne() {
        open(SettingsOption.Background)
        val stored = actions.settings.appearance.background
        model.onTouchStop(7, actions.settings, wide, drag = true)
        val dragged = ColorSetting.Custom(shades()[shades().size / 2].value)
        assertEquals(dragged, preview.appearance!!.background)
        assertEquals("the store is untouched", stored, actions.settings.appearance.background)
        assertEquals("the screen's settings take the overlay", dragged, preview.over(actions.settings).appearance.background)
        model.onTouchStopEnd()
        assertEquals("kept until the store shows it", dragged, preview.appearance!!.background)
        assertTrue(preview.isReleased)
        preview.settleAgainst(actions.settings.appearance)
        assertEquals("settled once the store holds the same colours", null, preview.appearance)
    }

    @Test
    fun theOverlayCarriesOnlyTheThreeColoursSoOtherAppearanceChangesShowAtOnce() {
        open(SettingsOption.Background)
        model.onTouchStop(7, actions.settings, wide, drag = true)
        model.onTouchStopEnd()
        val changed = actions.settings.withAppearance { it.copy(textSize = com.plumemu.coredata.settings.TextSizeSetting.Largest) }
        assertEquals("a text size changed meanwhile shows", changed.appearance.textSize, preview.over(changed).appearance.textSize)
        assertEquals("the dragged colour still shows", preview.appearance!!.background, preview.over(changed).appearance.background)
        preview.settleAgainst(preview.appearance!!.copy(textSize = changed.appearance.textSize))
        assertEquals("the same three colours settle it whatever else differs", null, preview.appearance)
    }

    @Test
    fun aReleasedPreviewThatTheStoreNeverMatchesIsGivenUpOn() {
        open(SettingsOption.Background)
        model.onTouchStop(7, actions.settings, wide, drag = true)
        model.onTouchStopEnd()
        preview.settleAgainst(actions.settings.appearance.copy(accent = ColorSetting.Custom(0x123456)))
        assertTrue("a different store does not settle it", preview.isActive)
        preview.clear()
        assertEquals(null, preview.appearance)
        assertFalse(preview.isReleased)
    }

    @Test
    fun bMidDragPutsBackTheColourBeforeTheStripAndTheLaterLiftWritesNothingMore() {
        open(SettingsOption.Background)
        toColor()
        val opening = appearance.background
        model.onTouchStop(model.colorStop + 6, actions.settings, wide, drag = true)
        assertNotEquals(opening, preview.appearance!!.background)
        press(Action.Back)
        assertEquals("the screen shows the opening colour again", opening, preview.appearance?.background)
        val writes = actions.writes
        model.onTouchStopEnd()
        assertEquals("the lift after B writes nothing more", writes, actions.writes)
        assertEquals(opening, actions.settings.appearance.background)
    }

    @Test
    fun theScreenGoingWithAFingerDownPutsBackWhenNotChosen() {
        open(SettingsOption.Accent)
        toColor()
        val opening = appearance.accent
        val before = actions.writes
        model.onTouchStop(model.colorStop + 4, actions.settings, wide, drag = true)
        model.onDispose()
        assertEquals("not chosen: the colour in effect when the cursor came down is back", opening, actions.settings.appearance.accent)
        assertEquals("only the put-back is written", before + 1, actions.writes)
        assertTrue("never an overlay left waiting for a lift that happened", preview.isReleased || !preview.isActive)
    }

    @Test
    fun theScreenGoingWithAFingerDownAfterAChoiceWritesTheShownColour() {
        open(SettingsOption.Accent)
        toColor()
        model.onTouchStop(model.colorStop + 4, actions.settings, wide, drag = true)
        val shown = preview.appearance!!.accent
        press(Action.Enter)
        model.onDispose()
        assertEquals(shown, actions.settings.appearance.accent)
    }

    @Test
    fun aStepDuringADragEndsWithTheStoreEqualToWhatIsShown() {
        open(SettingsOption.Background)
        toColor()
        model.onTouchStop(model.colorStop + 3, actions.shown, wide, drag = true)
        move(Direction.Right)
        assertEquals("the screen follows the step", ColorSetting.Custom(shades()[shades().size / 2].value), preview.appearance!!.background)
        model.onTouchStopEnd()
        actions.settings
        assertEquals(preview.appearance!!.background, appearance.background)
    }

    @Test
    fun theTextShadesReadOnTheBackgroundShownWhenThePageOpenedWhileTheStoreAnswersLate() {
        val paper = com.plumemu.coreui.theme.BackgroundPreset.Paper.color
        open(SettingsOption.Background)
        model.onTapPanelItem(com.plumemu.coreui.theme.BackgroundPreset.entries.indexOf(com.plumemu.coreui.theme.BackgroundPreset.Paper), actions.shown, wide) // queued: actions.shown is still Graphite
        assertEquals("the store has not answered yet", ColorSetting.Preset("Graphite"), actions.shown.appearance.background)
        press(Action.Back)
        focusOption(SettingsOption.TextColor)
        press(Action.Enter)
        assertEquals(PanelPage.TextPresets, model.focus.page)
        assertEquals(ColorShades.shades(ShadeRole.Text, model.colorStop, paper), model.colorShades(PanelPage.TextPresets))
    }

    @Test
    fun leavingTheCompositionOnAShadePutsBackTheColourInEffectWhenTheCursorCameDown() {
        open(SettingsOption.Background)
        toColor()
        val reached = appearance.background
        move(Direction.Down) // the first shade previews
        assertNotEquals(reached, appearance.background)
        model.onDispose()
        assertEquals(reached, appearance.background)
    }
}
