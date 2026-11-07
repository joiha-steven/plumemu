package com.plumemu.game

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Get it": the emulator's own page in a browser, and a false answer when nothing opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadPageTest {
    private val page = "https://github.com/azahar-emu/azahar/releases"

    @Test
    fun thePageOpensOnceInItsOwnTask() {
        val tried = mutableListOf<Intent>()
        assertTrue(openDownloadPage(page) { tried += it })
        val intent = tried.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(page, intent.data.toString())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun withNothingToOpenItTheAnswerIsFalse() {
        var tries = 0
        assertFalse(
            openDownloadPage(page) {
                tries++
                throw ActivityNotFoundException(page)
            },
        )
        assertEquals("no second address to fall back on", 1, tries)
    }
}
