package com.plumemu.graph

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A card put in while Plumemu is started reaches the change check; nothing listens while it is stopped. */
@RunWith(RobolectricTestRunner::class)
class StorageMountsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var mounts = 0

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action, Uri.parse("file:///storage/1234-ABCD")))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun receivers() =
        shadowOf(context as android.app.Application).registeredReceivers.filter { it.intentFilter.hasDataScheme("file") }

    @Test
    @Config(sdk = [34])
    fun aMountIsHeardOnlyWhileStarted() {
        val storage = StorageMounts(context) { mounts++ }
        broadcast(Intent.ACTION_MEDIA_MOUNTED)
        assertEquals("nothing listens before the start", 0, mounts)

        storage.onStart()
        storage.onStart()
        broadcast(Intent.ACTION_MEDIA_MOUNTED)
        assertEquals("one receiver, however many starts", 1, mounts)
        broadcast(Intent.ACTION_MEDIA_UNMOUNTED)
        assertEquals("a removal asks nothing", 1, mounts)
        assertTrue("exported, like the other system broadcasts", receivers().single().flags and Context.RECEIVER_EXPORTED != 0)

        storage.onStop()
        assertEquals("no receiver while stopped", 0, receivers().size)
        broadcast(Intent.ACTION_MEDIA_MOUNTED)
        assertEquals(1, mounts)
    }

    @Test
    @Config(sdk = [23])
    fun theLowestAndroidHearsItToo() {
        val storage = StorageMounts(context) { mounts++ }
        storage.onStart()
        broadcast(Intent.ACTION_MEDIA_MOUNTED)
        storage.onStop()
        assertEquals(1, mounts)
    }
}
