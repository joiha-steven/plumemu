package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.settings.colorSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Setup's look as stored: a set's own colours, or on an OLED screen true Black under the default set's accent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphSetupLookTest {
    @get:org.junit.Rule val timeout = graphTestTimeout()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val setup = GraphSetupSource(graph, context, Dispatchers.IO)

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun stored() = runBlocking { withTimeout(WAIT_MS) { graph.settingsStore.settings.first() } }.appearance

    @Test
    fun theDefaultSetIsStoredWithItsInkPage() {
        runBlocking { withTimeout(WAIT_MS) { setup.applyLook(ColorSet.Plumemu, textSize = null, blackPage = false) } }
        assertEquals(ColorSet.Plumemu, stored().colorSet())
    }

    @Test
    fun aBlackPageKeepsTheDefaultSetsAccentOnTrueBlack() {
        runBlocking { withTimeout(WAIT_MS) { setup.applyLook(ColorSet.Plumemu, textSize = null, blackPage = true) } }
        val appearance = stored()
        assertEquals(ColorSetting.Preset("Black"), appearance.background)
        assertEquals(ColorSet.Plumemu.accentColor.value, (appearance.accent as ColorSetting.Custom).rgb and 0xFFFFFF)
    }
}
