package com.plumemu.home

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.InputRouter
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.screen.NoActions
import com.plumemu.settings.screen.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * "Show battery" off means no Wi-Fi glyph and no network callback, on the main screen and in
 * Settings; switching it off while the screen is up drops the callback, on again brings it back
 *. A Wi-Fi network is up throughout, so the glyph shows whenever it is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class WifiBarsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** One ordinary Wi-Fi network up: Robolectric's own default lacks the capabilities the request asks for. */
    @Before
    fun wifiUp() {
        val shadow = shadowOf(RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java))
        val network = ShadowNetwork.newInstance(1)
        shadow.clearAllNetworks()
        shadow.addNetwork(network, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED))
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadow.setNetworkCapabilities(network, caps)
    }

    private fun callbacks() =shadowOf(RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)).networkCallbacks.size

    private fun glyphs() = compose.onAllNodesWithTag(TopBarTags.WIFI, useUnmergedTree = true)

    @Test
    fun mainScreenFollowsShowBattery() {
        var showBattery by mutableStateOf(false)
        compose.setContent {
            PlumTheme {
                val scope = rememberCoroutineScope()
                HomeScreen(FakeHomeContent.board(), remember { InputRouter(scope, onDebug = {}) }, HomeCallbacks(), bars = HomeBars(showBattery = showBattery))
            }
        }
        compose.waitForIdle()
        assertEquals("battery off: nothing listens", 0, callbacks())
        glyphs().assertCountEquals(0)
        showBattery = true
        compose.waitForIdle()
        assertEquals("battery on: listening", 1, callbacks())
        glyphs().assertCountEquals(1)
        showBattery = false
        compose.waitForIdle()
        assertEquals("battery switched off while up: unregistered", 0, callbacks())
        glyphs().assertCountEquals(0)
    }

    @Test
    fun settingsFollowsShowBattery() {
        var showBattery by mutableStateOf(false)
        compose.setContent {
            PlumTheme {
                val scope = rememberCoroutineScope()
                val settings = PlumSettings().let { it.copy(homeScreen = it.homeScreen.copy(showBattery = showBattery)) }
                SettingsScreen(settings, remember { InputRouter(scope, onDebug = {}) }, NoActions, versionName = "test")
            }
        }
        compose.waitForIdle()
        assertEquals("battery off: nothing listens", 0, callbacks())
        glyphs().assertCountEquals(0)
        showBattery = true
        compose.waitForIdle()
        assertEquals(1, callbacks())
        glyphs().assertCountEquals(1)
        showBattery = false
        compose.waitForIdle()
        assertEquals("switched off while up: unregistered", 0, callbacks())
        glyphs().assertCountEquals(0)
    }
}
