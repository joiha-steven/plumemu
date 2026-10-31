package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
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

    private fun focusBoxArtStatus() {
        model.content = model.content.copy(account = AccountStatus.NotInThisBuild)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.ScraperStatus)
        reported.drain()
    }

    @Test
    fun aAndATapDoNothingAndMakeNoSoundOnTheBoxArtStatusRow() {
        focusBoxArtStatus()
        val at = model.optionIndex
        for (geometry in listOf(wide, narrow)) {
            press(Action.Enter, geometry)
            model.onTapOption(at, actions.shown, geometry)
            model.onTapValue(at, actions.shown, geometry)
            assertEquals(SettingsColumn.Options, model.focus.column)
            assertNull("no panel was entered", model.focus.page)
            assertEquals(emptyList<Any>(), reported.drain())
        }
        assertNull("no A hint", rowConfirmHint(SettingsOption.ScraperStatus))
    }

    @Test
    fun aAndATapDoNothingOnVersionAtEveryWidth() {
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
        focusBoxArtStatus()
        move(Direction.Right, wide)
        assertNull("wide shows the panel beside the row already", model.focus.page)
        move(Direction.Right, narrow)
        assertNotNull("narrow: Right is the way in", model.focus.page)
    }
}
