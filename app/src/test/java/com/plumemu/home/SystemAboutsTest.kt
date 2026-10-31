package com.plumemu.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The about text of ~30 systems: every id is a shipped system, every string is there and short. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SystemAboutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyIdIsASystemTheShippedCatalogHas() {
        assertEquals(31, SystemAbouts.ids.size)
        for (id in SystemAbouts.ids) assertNotNull("$id is not in platforms.json", shippedCatalog.system(id))
        for (id in SystemAbouts.manyMakerIds) assertNotNull("$id is not in platforms.json", shippedCatalog.system(id))
    }

    @Test
    fun everyStringIsFilledInShortAndPlain() {
        for (id in SystemAbouts.ids) {
            val text = context.getString(SystemAbouts.of(id)!!)
            assertTrue("$id: empty", text.isNotBlank())
            assertTrue("$id: ${text.length} characters, at most 130", text.length <= 130)
            assertTrue("$id: ends in a full stop", text.endsWith("."))
            assertTrue("$id: ASCII only", text.all { it.code < 128 })
        }
    }

    @Test
    fun theBuiltInfoCarriesTheTextOfItsSystemAndNoneIsMissing() {
        for (id in SystemAbouts.ids) {
            val info = systemInfoOf(shippedSystem(id))
            assertEquals("$id", SystemAbouts.of(id), info!!.about)
        }
    }

    @Test
    fun theThreeArcadeSystemsHaveAFactsLineAndNoMaker() {
        for (id in listOf("arcade", "mame", "fbneo")) {
            val info = systemInfoOf(shippedSystem(id))!!
            assertEquals("$id", null, info.maker)
            assertEquals("$id", listOf("Arcade games from many makers"), info.factsIn())
        }
    }
}
