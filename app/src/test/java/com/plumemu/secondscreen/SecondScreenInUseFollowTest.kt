package com.plumemu.secondscreen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.settings.AppearanceCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `startAndFollowInUse` feeds the flow, and the cache of the next start, as a display is found or lost while running. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenInUseFollowTest {
    private class Source(var displays: List<DisplayFacts> = emptyList()) : DisplaySource {
        private var listener: (() -> Unit)? = null

        fun change(next: List<DisplayFacts>) {
            displays = next
            listener?.invoke()
        }

        override fun presentationDisplays() = displays

        override fun register(onChange: () -> Unit): Boolean {
            listener = onChange
            return true
        }

        override fun unregister() {
            listener = null
        }
    }

    private val builtIn = DisplayFacts(4, "Screen-2", true, TYPE_INTERNAL)

    @Test
    fun aDisplayFoundOrLostWhileRunningMovesTheFlowAndTheCache() = runTest(UnconfinedTestDispatcher()) {
        val cache = AppearanceCache(ApplicationProvider.getApplicationContext<Context>())
        val source = Source()
        val displays = SecondScreenDisplays(source, info = {})
        val into = MutableStateFlow(false)
        displays.startAndFollowInUse(backgroundScope, MutableStateFlow<PlumSettings?>(PlumSettings.Defaults), into, cache::writeSecondScreenInUse)
        assertEquals("no display: not in use", false, into.value)
        source.change(listOf(builtIn))
        assertEquals("found", true, into.value)
        assertEquals("kept for the next start", true, cache.wasSecondScreenInUse())
        source.change(emptyList())
        assertEquals("lost", false, into.value)
        assertEquals(false, cache.wasSecondScreenInUse())
    }

    @Test
    fun aCachedInUseIsCorrectedWhenTheDisplayIsMissingAtTheNextStart() = runTest(UnconfinedTestDispatcher()) {
        val cache = AppearanceCache(ApplicationProvider.getApplicationContext<Context>())
        cache.writeSecondScreenInUse(true)
        val into = MutableStateFlow(cache.wasSecondScreenInUse())
        SecondScreenDisplays(Source(), info = {}).startAndFollowInUse(backgroundScope, MutableStateFlow<PlumSettings?>(PlumSettings.Defaults), into, cache::writeSecondScreenInUse)
        assertEquals(false, into.value)
        assertEquals(false, cache.wasSecondScreenInUse())
    }
}
