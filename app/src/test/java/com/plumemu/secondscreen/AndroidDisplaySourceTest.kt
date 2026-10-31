package com.plumemu.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android side on Robolectric: a device with one screen has no second screen, and a blocked hidden call reads as unknown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AndroidDisplaySourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val defaultDisplay: Display get() = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    @Test
    fun aDeviceWithOneScreenHasNoSecondScreenAndListensWithoutFailing() {
        val warnings = mutableListOf<String>()
        val source = AndroidDisplaySource(context, warn = { warnings += it })
        val displays = SecondScreenDisplays(source, info = {})
        displays.start()
        assertNull(displays.current.value)
        displays.stop()
        assertTrue(warnings.toString(), warnings.isEmpty())
    }

    @Test
    fun aRefusedListenerIsLoggedByClassAndMeansNoSecondScreen() {
        val warnings = mutableListOf<String>()
        val service = FailingService(failsToList = false, failsToRegister = true)
        val displays = SecondScreenDisplays(AndroidDisplaySource(service, warn = { warnings += it }), info = {})
        displays.start() // must not throw
        assertNull(displays.current.value)
        displays.stop()
        assertEquals("nothing to unregister", 0, service.unregistered)
        assertEquals(warnings.toString(), 1, warnings.size)
        assertTrue(warnings.single(), "SecurityException" in warnings.single())
        assertTrue("never the message: $warnings", warnings.none { "secret" in it })
    }

    @Test
    fun displaysThatCannotBeListedAreNoneAndLoggedByClass() {
        val warnings = mutableListOf<String>()
        val service = FailingService(failsToList = true, failsToRegister = false)
        val displays = SecondScreenDisplays(AndroidDisplaySource(service, warn = { warnings += it }), info = {})
        displays.start()
        assertNull(displays.current.value)
        displays.stop()
        assertEquals(1, service.unregistered)
        assertTrue(warnings.toString(), warnings.single().startsWith("Displays could not be listed (IllegalStateException)"))
    }

    @Test
    fun noDisplayServiceMeansNoSecondScreen() {
        val warnings = mutableListOf<String>()
        val displays = SecondScreenDisplays(AndroidDisplaySource(null as DisplayService?, warn = { warnings += it }), info = {})
        displays.start()
        assertNull(displays.current.value)
        displays.stop()
        assertTrue(warnings.toString(), warnings.isNotEmpty())
    }

    private class FailingService(
        private val failsToList: Boolean,
        private val failsToRegister: Boolean,
    ) : DisplayService {
        var unregistered = 0

        override fun presentationDisplays(): Array<Display> = if (failsToList) throw IllegalStateException("secret") else emptyArray()

        override fun registerListener(
            listener: DisplayManager.DisplayListener,
            handler: Handler,
        ) {
            if (failsToRegister) throw SecurityException("secret")
        }

        override fun unregisterListener(listener: DisplayManager.DisplayListener) {
            unregistered++
        }
    }

    @Test
    fun aBlockedLookUpReadsAsUnknownWithOneWarning() {
        val warnings = mutableListOf<String>()
        val types = HiddenDisplayType(warn = { warnings += it }, lookUp = { throw NoSuchMethodException("getType") })
        assertNull(types.of(defaultDisplay))
        assertNull(types.of(defaultDisplay))
        assertEquals(1, warnings.size)
    }

    @Test
    fun theHiddenTypeIsFoundByItsNameOnAndroidsOwnDisplayClass() {
        // Robolectric runs the real Display class, so a misspelt method name would warn here.
        val warnings = mutableListOf<String>()
        val type = HiddenDisplayType(warn = { warnings += it }).of(defaultDisplay)
        assertTrue("read as a number: $type, $warnings", type != null && warnings.isEmpty())
    }
}
