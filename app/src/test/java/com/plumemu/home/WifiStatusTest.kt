package com.plumemu.home

import android.content.ComponentName
import android.net.ConnectivityManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.robolectric.Robolectric
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.robolectric.shadows.ShadowNetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import com.plumemu.coreui.testing.ReferenceShots

/** The Wi-Fi glyph's state: Wi-Fi networks only, listened to between start and stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class WifiStatusTest {
    private val network = ShadowNetwork.newInstance(1)
    private val other = ShadowNetwork.newInstance(2)

    private fun capabilities(isWifi: Boolean): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(if (isWifi) NetworkCapabilities.TRANSPORT_WIFI else NetworkCapabilities.TRANSPORT_CELLULAR)
        // What a NetworkRequest.Builder asks for by default, as an ordinary network has.
        for (capability in listOf(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED, NetworkCapabilities.NET_CAPABILITY_TRUSTED, NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            shadowOf(caps).addCapability(capability)
        }
        return caps
    }

    /** A VPN over Wi-Fi: since Android 9 it carries its underlying network's transport too. */
    private fun vpnOverWifi(): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        // A new NetworkCapabilities starts with NOT_VPN; a VPN's own capabilities never have it.
        shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        return caps
    }

    private fun callbackReports(): Pair<WifiCallback, MutableList<Boolean>> {
        val heard = mutableListOf<Boolean>()
        return WifiCallback { heard += it } to heard
    }

    @Test
    fun availableMeansConnected() {
        val (callback, heard) = callbackReports()
        callback.onAvailable(network)
        assertEquals(listOf(true), heard)
    }

    @Test
    fun lostMeansNotConnected() {
        val (callback, heard) = callbackReports()
        callback.onAvailable(network)
        callback.onLost(network)
        assertEquals(listOf(true, false), heard)
    }

    @Test
    fun aNetworkThatIsNotWifiIsNotConnected() {
        val (callback, heard) = callbackReports()
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, capabilities(isWifi = false))
        callback.onCapabilitiesChanged(other, capabilities(isWifi = false))
        assertEquals("the one that stopped being Wi-Fi leaves, the other never joins", listOf(true, false, false), heard)
        callback.onCapabilitiesChanged(network, capabilities(isWifi = true))
        assertEquals(true, heard.last())
    }

    @Test
    fun staysConnectedWhileAnotherWifiNetworkIsUp() {
        val (callback, heard) = callbackReports()
        callback.onAvailable(network)
        callback.onAvailable(other)
        callback.onLost(network)
        assertTrue(heard.last())
        callback.onLost(other)
        assertFalse(heard.last())
    }

    @Test
    fun registersOnStartAndUnregistersOnStop() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(manager)
        val heard = mutableListOf<Boolean>()
        val watch = WifiWatch(manager) { heard += it }
        assertEquals("nothing listens before start", 0, shadow.networkCallbacks.size)
        watch.start()
        assertEquals(1, shadow.networkCallbacks.size)
        watch.stop()
        assertEquals("nothing listens after stop", 0, shadow.networkCallbacks.size)
        watch.stop()
        assertEquals("a second stop is harmless", 0, shadow.networkCallbacks.size)
    }

    @Test
    fun theScreenListensOnlyWhileStarted() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { rememberWifiConnected() }
        shadowOf(Looper.getMainLooper()).idle()
        val shadow = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        assertEquals("listening while started", 1, shadow.networkCallbacks.size)
        controller.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("nothing listens while stopped", 0, shadow.networkCallbacks.size)
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("back when started again", 1, shadow.networkCallbacks.size)
        controller.pause().stop().destroy()
    }

    /** On API 23 unregistering is asynchronous: a callback already queued still arrives after stop. */
    @Test
    fun aCallbackThatArrivesAfterStopIsIgnored() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        // Robolectric starts with a Wi-Fi network up; this test is about what comes after stop.
        shadowOf(manager).clearAllNetworks()
        val heard = mutableListOf<Boolean>()
        val watch = WifiWatch(manager) { heard += it }
        watch.start()
        val registered = shadowOf(manager).networkCallbacks.single()
        watch.stop()
        registered.onAvailable(network)
        registered.onLost(network)
        assertTrue("nothing is reported after stop: $heard", heard.isEmpty())
    }

    /** The first frame already shows the glyph when Wi-Fi is up: no waiting for the callback's binder trip. */
    @Test
    fun aWifiNetworkAlreadyUpIsReportedAtStart() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(manager)
        shadow.clearAllNetworks()
        shadow.addNetwork(network, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED))
        shadow.setNetworkCapabilities(network, capabilities(isWifi = true))
        val heard = mutableListOf<Boolean>()
        WifiWatch(manager) { heard += it }.start()
        assertEquals(listOf(true), heard)
    }

    /**
     * The seed takes only what the callback watches: a VPN over Wi-Fi is never heard by the callback,
     * so seeding it would leave the glyph on after Wi-Fi is lost.
     */
    @Test
    fun aVpnOverWifiIsNotSeededSoLosingWifiHidesTheGlyph() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(manager)
        shadow.clearAllNetworks()
        val wifiInfo = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED)
        shadow.addNetwork(network, wifiInfo)
        shadow.setNetworkCapabilities(network, capabilities(isWifi = true))
        shadow.addNetwork(other, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_VPN, 0, true, NetworkInfo.State.CONNECTED))
        shadow.setNetworkCapabilities(other, vpnOverWifi())
        val heard = mutableListOf<Boolean>()
        WifiWatch(manager) { heard += it }.start()
        assertTrue(heard.last())
        shadow.networkCallbacks.single().onLost(network)
        assertFalse("only the VPN is left, which is not Wi-Fi: $heard", heard.last())
    }

    /** A loss heard on the system thread between reading a network and seeding it is not undone by the seed. */
    @Test
    fun aNetworkLostBeforeItsSeedLandsStaysLost() {
        val (callback, heard) = callbackReports()
        callback.onLost(network)
        callback.seed(network)
        assertFalse("the seed came too late for a network already lost: $heard", heard.lastOrNull() ?: false)
        callback.onAvailable(network)
        assertTrue("a network that comes back is heard again", heard.last())
    }

    @Test
    fun aMobileNetworkAlreadyUpIsNotReportedAsWifi() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(manager)
        shadow.clearAllNetworks()
        shadow.addNetwork(other, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_MOBILE, 0, true, NetworkInfo.State.CONNECTED))
        shadow.setNetworkCapabilities(other, capabilities(isWifi = false))
        val heard = mutableListOf<Boolean>()
        WifiWatch(manager) { heard += it }.start()
        assertTrue("no Wi-Fi network is up: $heard", heard.none { it })
    }

    @Test
    fun noConnectivityManagerIsNotACrash() {
        val heard = mutableListOf<Boolean>()
        val watch = WifiWatch(null) { heard += it }
        watch.start()
        watch.stop()
        assertTrue(heard.isEmpty())
    }

    @Test
    @Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], shadows = [RefusingConnectivityShadow::class])
    fun aRefusedRegistrationShowsNoGlyphAndDoesNotCrash() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val heard = mutableListOf<Boolean>()
        val watch = WifiWatch(manager) { heard += it }
        watch.start()
        watch.stop()
        assertEquals(listOf(false), heard)
    }
}

/** A system that refuses to let the app watch the network (a missing permission). */
@Implements(ConnectivityManager::class)
class RefusingConnectivityShadow : ShadowConnectivityManager() {
    @Implementation
    override fun registerNetworkCallback(
        request: android.net.NetworkRequest,
        callback: ConnectivityManager.NetworkCallback,
    ): Unit = throw SecurityException("no permission")
}
