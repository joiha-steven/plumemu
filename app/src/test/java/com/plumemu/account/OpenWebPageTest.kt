package com.plumemu.account

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The browser opener behind RetroAchievements' "Open the key page". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class OpenWebPageTest {
    @Test
    fun startsAViewIntentOnTheKeyPageInANewTask() {
        val started = mutableListOf<Intent>()
        assertTrue(openWebPage(RA_KEY_PAGE_URL) { started += it })
        val intent = started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://retroachievements.org/settings", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun reportsNoBrowserAndARefusalWithoutThrowing() {
        assertFalse(openWebPage(RA_KEY_PAGE_URL) { throw ActivityNotFoundException() })
        assertFalse(openWebPage(RA_KEY_PAGE_URL) { throw SecurityException() })
    }

    @Test
    fun throughTheRealContextTheActivityIsStarted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(openWebPage(RA_KEY_PAGE_URL) { app.startActivity(it) })
        assertEquals(RA_KEY_PAGE_URL, shadowOf(app).nextStartedActivity.dataString)
    }
}
