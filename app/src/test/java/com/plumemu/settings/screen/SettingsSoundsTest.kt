package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Settings reports for each press and tap, at the point where its focus or a setting changes. */
internal class SettingsSoundsTest : SettingsModelHarness() {
    private val reported = Reported().also { it.listenTo(model.feel) }

    // The fake store applies a write when it is read, as DataStore does a moment later.
    private fun heard(action: Action) = press(action).let { actions.settings; reported.drain() }

    private fun heard(direction: Direction) = heard(Action.Move(direction))

    private fun tapped(run: () -> Unit) = run().let { reported.drain() }

    @Test
    fun aWrongHexCodeTriedWithPlumemusDoneConfirmsAsUseThisColourDoes() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        repeat(6) { move(Direction.Right) }
        move(Direction.Down)
        press(Action.Enter) // "+": the accent palette, on a swatch
        toHexRow()
        press(Action.Enter) // Plumemu's tray up on the hex field
        press(Action.Typed(TypedKey.Text("zz")))
        reported.drain()
        assertEquals("START with a wrong code", listOf(Outcome.Activated), heard(Action.Menu))
        assertTrue("refused, still typing", model.hexError && model.isEditingHex)
        assertEquals("the tray's Done with a wrong code", listOf(Outcome.Activated), tapped { model.onTrayKey(KeyboardEvent.Action) })
        press(Action.Typed(TypedKey.Backspace))
        press(Action.Typed(TypedKey.Backspace))
        assertEquals("START on an empty field", listOf(Outcome.Activated), heard(Action.Menu))
        // "Use this colour" with the same code (tray down, Down, A): the same confirm.
        press(Action.Typed(TypedKey.Text("zz")))
        press(Action.Back)
        move(Direction.Down)
        reported.drain()
        assertEquals("A on Use this colour", listOf(Outcome.Activated), heard(Action.Enter))
        assertTrue(model.hexError)
    }

    @Test
    fun movesPlayOnlyWhenTheCursorMoved() {
        assertEquals("already on the first section", emptyList<Outcome>(), heard(Direction.Up))
        assertEquals(listOf(Outcome.Moved), heard(Direction.Down))
        assertEquals(listOf(Outcome.Moved), heard(Action.NextSystem))
    }

    @Test
    fun enterOpensWithConfirmArrowRightWithAMoveAndBackGoesBack() {
        assertEquals(listOf(Outcome.Activated), heard(Action.Enter))
        assertEquals(listOf(Outcome.WentBack), heard(Action.Back))
        assertEquals(listOf(Outcome.Moved), heard(Direction.Right))
        assertEquals(listOf(Outcome.WentBack), heard(Action.Back))
        assertEquals(listOf(Outcome.WentBack), heard(Action.Back))
        assertEquals("from the sections: closes Settings", 1, actions.closed)
    }

    @Test
    fun applyingALanguageConfirmsByPressAndByTap() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Language)
        press(Action.Enter)
        reported.drain()
        assertEquals("the first tap only moves the cursor", listOf(Outcome.Moved), tapped { model.onTapPanelItem(1, actions.settings, wide) })
        assertEquals("the second tap applies it", listOf(Outcome.Activated), tapped { model.onTapPanelItem(1, actions.settings, wide) })
        press(Action.Enter)
        reported.drain()
        assertEquals("A on a language", listOf(Outcome.Activated), heard(Action.Enter))
    }

    @Test
    fun tapsThatOpenConfirmAndTapsThatOnlyFocusMove() {
        assertEquals(listOf(Outcome.Moved), tapped { model.onTapSection(SettingsSection.Advanced) })
        assertEquals("the second tap opens it", listOf(Outcome.Activated), tapped { model.onTapSection(SettingsSection.Advanced) })
        val animations = SettingsSection.Advanced.options.indexOf(SettingsOption.Animations)
        assertEquals("a switch flips on the first tap", listOf(Outcome.Activated), tapped { model.onTapOption(animations, actions.settings, wide) })
        val reset = SettingsSection.Advanced.options.indexOf(SettingsOption.ResetAll)
        assertEquals("first tap focuses", listOf(Outcome.Moved), tapped { model.onTapOption(reset, actions.settings, wide) })
        assertEquals("second tap opens the confirm", listOf(Outcome.Activated), tapped { model.onTapOption(reset, actions.settings, wide) })
    }

    @Test
    fun aTapThatOnlyFocusesOrStepsBackMovesAndNeverConfirms() {
        tapped { model.onTapSection(SettingsSection.Appearance) }
        val textColor = SettingsSection.Appearance.options.indexOf(SettingsOption.TextColor)
        assertEquals("first tap on a choice row from the sections", listOf(Outcome.Moved), tapped { model.onTapOption(textColor, actions.settings, wide) })
        assertEquals("a tap on a section from the options steps back", listOf(Outcome.Moved), tapped { model.onTapSection(SettingsSection.Appearance) })
    }

    @Test
    fun aLicenceTextThatCannotScrollIsSilent() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Licenses)
        press(Action.Enter) // the list
        press(Action.Enter) // one licence's text
        assertEquals(PanelPage.LicenseText, model.focus.page)
        reported.drain()
        model.licenseCanScroll = { false }
        assertEquals(emptyList<Outcome>(), heard(Direction.Down))
        assertEquals(emptyList<Outcome>(), heard(Direction.Up))
        model.licenseCanScroll = { true }
        assertEquals(listOf(Outcome.Moved), heard(Direction.Down))
    }

    @Test
    fun theSoundsSwitchIsSilentItselfBothWaysButItsNeighboursClick() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Sounds)
        reported.drain()
        assertEquals("by A", emptyList<Outcome>(), heard(Action.Enter))
        val sounds = SettingsSection.Advanced.options.indexOf(SettingsOption.Sounds)
        val reset = SettingsSection.Advanced.options.indexOf(SettingsOption.ResetAll)
        assertEquals("a tap that only focuses another row", listOf(Outcome.Moved), tapped { model.onTapOption(reset, actions.settings, wide) })
        assertEquals("first tap focuses", listOf(Outcome.Moved), tapped { model.onTapOption(sounds, actions.settings, wide) })
        assertEquals("second tap steps the level, silent", emptyList<Outcome>(), tapped { model.onTapOption(sounds, actions.settings, wide) })
        heard(Direction.Up) // from Sounds to Animations
        assertEquals(listOf(Outcome.Activated), heard(Action.Enter))
    }

    @Test
    fun aOnTheTextPanelOfAGreyBackgroundMakesNoSound() {
        // A background an older version stored that no text colour reads on: the panel offers nothing.
        actions.settings = actions.settings.copy(appearance = actions.settings.appearance.copy(background = ColorSetting.Custom(0x808080)))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.TextColor)
        heard(Action.Enter)
        reported.drain()
        assertEquals("A with nothing to pick", emptyList<Outcome>(), heard(Action.Enter))
        assertEquals("and the panel stays", PanelPage.TextPresets, model.focus.page)
    }

    @Test
    fun aChoiceAtItsEndSaysNothingAndWrapsWithAConfirm() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Font)
        reported.drain()
        assertEquals("first value, Left", emptyList<Outcome>(), heard(Direction.Left))
        assertEquals(listOf(Outcome.Moved), heard(Direction.Right))
        assertEquals(listOf(Outcome.Moved), heard(Direction.Right))
        assertEquals("last value, Right", emptyList<Outcome>(), heard(Direction.Right))
        assertEquals("A wraps round", listOf(Outcome.Activated), heard(Action.Enter))
    }

    @Test
    fun detectingWhileItRunsIsSilent() {
        openSection(SettingsSection.Emulators)
        reported.drain()
        assertEquals(listOf(Outcome.Activated), heard(Action.Enter))
        assertEquals("the first run is still going", emptyList<Outcome>(), heard(Action.Enter))
    }
}
