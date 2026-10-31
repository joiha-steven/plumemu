package com.plumemu.usage

import android.content.Intent
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.telemetry.OpenFrom
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `app_open.from` of the intent that opened the main screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class OpenFromTest {
    private fun main(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)

    @Test
    fun homeIconUpdateAndOther() {
        assertEquals(OpenFrom.HomeKey, openFromOf(main(Intent.CATEGORY_HOME)))
        assertEquals(OpenFrom.Icon, openFromOf(main(Intent.CATEGORY_LAUNCHER)))
        assertEquals(OpenFrom.Update, openFromOf(main(Intent.CATEGORY_LAUNCHER).putExtra(EXTRA_OPENED_AFTER_UPDATE, true)))
        assertEquals(OpenFrom.Other, openFromOf(Intent(Intent.ACTION_VIEW)))
        assertEquals(OpenFrom.Other, openFromOf(null))
    }
}
