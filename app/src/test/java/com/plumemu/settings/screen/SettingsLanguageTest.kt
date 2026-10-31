package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.language.AppLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Advanced › Language: a list opened with A, never stepped. */
internal class SettingsLanguageTest : SettingsModelHarness() {
    private fun onLanguageRow() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Language)
    }

    private fun picked() = actions.settings.appearance.language

    @Test
    fun languageLivesInAdvancedAfterVibrationAndIsNoLongerInAbout() {
        val advanced = SettingsSection.Advanced.options
        assertEquals(advanced.indexOf(SettingsOption.Vibration) + 1, advanced.indexOf(SettingsOption.Language))
        assertEquals(false, SettingsSection.About.options.contains(SettingsOption.Language))
        assertEquals("a row that opens a list, not a ‹ › choice", OptionKind.Opens, SettingsOption.Language.kind)
    }

    @Test
    fun leftAndRightOnTheRowNeverChangeTheLanguage() {
        onLanguageRow()
        move(Direction.Right)
        assertNull("Right only opens the list", picked())
        press(Action.Back)
        move(Direction.Left)
        assertNull("Left only goes back to the sections", picked())
    }

    @Test
    fun aOpensTheListOnTheCurrentLanguageAndUpDownOnlyMove() {
        actions.settings = actions.settings.copy(appearance = actions.settings.appearance.copy(language = AppLanguages.all[2].tag))
        onLanguageRow()
        press(Action.Enter)
        assertEquals(PanelPage.Language, model.focus.page)
        assertEquals("on the current language (System default is item 0)", 3, model.focus.panelIndex)
        move(Direction.Down)
        move(Direction.Up)
        move(Direction.Up)
        assertEquals("moving changes nothing", AppLanguages.all[2].tag, picked())
        assertEquals(2, model.focus.panelIndex)
        repeat(AppLanguages.all.size + 3) { move(Direction.Down) }
        assertEquals("the list ends at the last language", AppLanguages.all.size, model.focus.panelIndex)
        assertEquals(AppLanguages.all[2].tag, picked())
    }

    @Test
    fun aAppliesTheItemUnderTheCursorAndClosesThePanel() {
        onLanguageRow()
        press(Action.Enter)
        assertEquals("System default first", 0, model.focus.panelIndex)
        move(Direction.Down)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(AppLanguages.all[1].tag, picked())
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.Enter)
        repeat(AppLanguages.all.size) { move(Direction.Up) }
        press(Action.Enter)
        assertNull("System default again", picked())
    }

    @Test
    fun bClosesThePanelWithNoChange() {
        onLanguageRow()
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Back)
        assertNull(picked())
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Language, model.focusedOption)
    }

    @Test
    fun aStoredTagNoLongerShippedReadsAsSystemDefault() {
        assertEquals(0, languagePanelIndex("xx"))
        assertEquals(0, languagePanelIndex(null))
        assertEquals(1 + AppLanguages.all.size - 1, languagePanelIndex(AppLanguages.all.last().tag))
        assertNull(languagePanelTag(0))
        assertEquals(AppLanguages.all.first().tag, languagePanelTag(1))
    }
}
