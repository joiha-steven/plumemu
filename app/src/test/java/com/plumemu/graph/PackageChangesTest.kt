package com.plumemu.graph

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainActivity
import com.plumemu.PlumApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** App installs and removals reach the startup work: heard while started, asked about after a stop. */
@RunWith(RobolectricTestRunner::class)
class PackageChangesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var changes = 0

    /** Android 8+'s counter, played by the test: [counter] moves when a package changes. */
    private val fakeSequence = object : PackageSequence {
        var counter = 5

        override fun current(context: Context): Int = counter

        override fun changedSince(context: Context, sequence: Int): Int? = counter.takeIf { it != sequence }
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action, Uri.parse("package:org.ppsspp.ppsspp")))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun receivers() =
        shadowOf(context as android.app.Application).registeredReceivers.filter { it.intentFilter.hasDataScheme("package") }

    @Test
    @Config(sdk = [34])
    fun everyKindOfPackageChangeIsHeardWhileStarted() {
        val packages = PackageChanges(context, fakeSequence) { changes++ }
        packages.onStart()
        assertEquals("nothing reported on a first start", 0, changes)
        for (action in PackageChanges.ACTIONS) broadcast(action)
        assertEquals(PackageChanges.ACTIONS.size, changes)

        val receiver = receivers().single()
        assertTrue("exported, like the other system broadcasts", receiver.flags and Context.RECEIVER_EXPORTED != 0)
        packages.onStop()
        assertEquals("no receiver while stopped", 0, receivers().size)
        broadcast(Intent.ACTION_PACKAGE_ADDED)
        assertEquals("not heard while stopped", PackageChanges.ACTIONS.size, changes)
    }

    @Test
    @Config(sdk = [34])
    fun android8AsksWhatChangedWhileStopped() {
        val packages = PackageChanges(context, fakeSequence) { changes++ }
        packages.onStart()
        packages.onStop()
        packages.onStart()
        assertEquals("nothing changed since the stop", 0, changes)

        packages.onStop()
        fakeSequence.counter = 6 // an emulator was installed while Plumemu was stopped
        packages.onStart()
        assertEquals("the change is found at the next start", 1, changes)
        assertEquals("registered once", 1, receivers().size)
        packages.onStop()
    }

    @Test
    @Config(sdk = [34])
    fun theRealCounterIsReadOnAndroid8() {
        // Robolectric answers getChangedPackages with no change; the call itself must work.
        assertEquals(0, AndroidPackageSequence.current(context))
    }

    @Test
    @Config(sdk = [23])
    fun beforeAndroid8AnyStopCountsAsAPossibleChange() {
        val packages = PackageChanges(context) { changes++ }
        packages.onStart()
        packages.onStop()
        packages.onStart()
        assertEquals("Android 6 cannot ask what changed while stopped", 1, changes)
        packages.onStop()
    }

    @Test
    @Config(sdk = [23])
    fun aNewActivityAfterTheOldOneWasDestroyedStillSeesTheChange() {
        val startup = (context as PlumApplication).graph.startup
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        first.pause().stop().destroy() // swiped from Recents while the process lives
        val before = startup.packageChangeCount

        val second = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertEquals("the stop of the first Activity is remembered by the process", before + 1, startup.packageChangeCount)
        second.pause().stop().destroy()
    }
}
