package com.plumemu.launcher.play

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStorePackageTest {
    private val store = SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext())

    @Test
    fun thePackageRoundTrips() {
        val note = PendingSession(1, 1_000, 2_000, 3, packageName = "com.emu")
        store.write(note)
        assertEquals(note, store.read())
    }

    @Test
    fun anOlderRecordHasNoPackage() {
        store.write(PendingSession(1, 1_000, 2_000, 3))
        assertNull(store.read()?.packageName)
    }
}
