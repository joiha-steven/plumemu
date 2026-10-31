package com.plumemu.settings.screen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The update panel's "Released …" is written as the check row writes a date, never as the manifest's raw ISO day. */
class SettingsUpdateDateTest {
    private val before = Locale.getDefault()

    @Before fun english() = Locale.setDefault(Locale.US)

    @After fun restore() = Locale.setDefault(before)

    @Test
    fun theReleaseDayIsADeviceDate() {
        assertEquals("Oct 23, 2026", releaseDateText("2026-10-23"))
        assertEquals("a time after the day is left out", "Oct 23, 2026", releaseDateText("2026-10-23T08:00:00Z"))
    }

    @Test
    fun aDayInAnotherLanguageFollowsTheDevice() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("23.10.2026", releaseDateText("2026-10-23"))
    }

    @Test
    fun textThatIsNotADayIsShownAsGiven() {
        assertEquals("soon", releaseDateText("soon"))
        assertEquals("2026-13-45", releaseDateText("2026-13-45"))
    }
}
