package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Information-only rows (Version, Privacy, "Box art: not available in this build"):
 * no A hint, and A and a tap do nothing and make no sound. On a narrow screen Right ("› More") still opens
 * the explanation, so it stays the one way in.
 */
internal class SettingsInfoRowsTest : SettingsModelHarness() {
    private val reported = Reported().also { it.listenTo(model.feel) }

    private fun focusPrivacy() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Privacy)
        reported.drain()
    }

    @Test
    fun aBuildWithoutScreenScraperHasNoStatusRowAndBoxArtStillOpens() {
        model.content = model.content.copy(account = AccountStatus.NotInThisBuild)
        assertEquals(listOf(SettingsOption.BoxArtSource), accountRowsIn(model.content))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.BoxArtSource)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
    }

    @Test
    fun aAndATapDoNothingAndMakeNoSoundOnAReadOnlyRow() {
        focusPrivacy()
        val at = model.optionIndex
        for (geometry in listOf(wide, narrow)) {
            press(Action.Enter, geometry)
            assertEquals("A, $geometry", emptyList<Any>(), reported.drain())
            model.onTapOption(at, actions.shown, geometry)
            assertEquals("tap, $geometry", emptyList<Any>(), reported.drain())
            model.onTapValue(at, actions.shown, geometry)
            assertEquals(SettingsColumn.Options, model.focus.column)
            assertNull("no panel was entered", model.focus.page)
            assertEquals("tap on the value, $geometry", emptyList<Any>(), reported.drain())
        }
        assertNull("no A hint", rowConfirmHint(SettingsOption.Privacy))
    }

    @Test
    fun withNoManifestTheUpdateEntryDoesNotMoveTheFocusEvenWhenAnUpdateWasRecorded() {
        val found = com.plumemu.update.UpdateManifest(41, "0.0.3", "https://example.com/p.apk", 1, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
        model.content = model.content.copy(hasUpdates = false, update = com.plumemu.update.UpdateView(found = found))
        val before = model.focus
        assertFalse(model.openUpdate(actions.shown, wide))
        assertEquals(before, model.focus)
    }

    @Test
    fun aOnVersionOpensItsPanelAndSoDoesATapOnItsValue() {
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Version)
        press(Action.Enter, wide)
        assertEquals(PanelPage.Update, model.focus.page)
        press(Action.Back, wide)
        assertNull(model.focus.page)
        val at = model.optionIndex
        model.onTapValue(at, actions.shown, wide)
        model.onTapValue(at, actions.shown, wide)
        assertEquals(PanelPage.Update, model.focus.page)
    }

    @Test
    fun aAndATapDoNothingOnVersionAtEveryWidthInABuildWithNoUpdateManifest() {
        model.content = model.content.copy(hasUpdates = false)
        openSection(SettingsSection.About)
        focusOption(SettingsOption.Version)
        val at = model.optionIndex
        // The first tap only records the row as the section's remembered one (the walk down to it did not); then it is settled.
        model.onTapValue(at, actions.shown, wide)
        reported.drain()
        for (geometry in listOf(wide, narrow)) {
            press(Action.Enter, geometry)
            assertEquals("A on Version, narrow=${geometry.isNarrow}", emptyList<Any>(), reported.drain())
            model.onTapValue(at, actions.shown, geometry)
            assertEquals(SettingsColumn.Options, model.focus.column)
            assertEquals("tap on Version, narrow=${geometry.isNarrow}", emptyList<Any>(), reported.drain())
        }
    }

    @Test
    fun rightStillOpensTheExplanationOnNarrowAndNotOnWide() {
        focusPrivacy()
        move(Direction.Right, wide)
        assertNull("wide shows the panel beside the row already", model.focus.page)
        move(Direction.Right, narrow)
        assertNotNull("narrow: Right is the way in", model.focus.page)
    }
}
