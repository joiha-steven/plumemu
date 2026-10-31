package com.plumemu.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

/*
 * Whether the device is connected through Wi-Fi, for the top bar's glyph (no polling).
 * A network callback for Wi-Fi networks only, registered while the lifecycle owner is started and
 * dropped when it stops. Needs only ACCESS_NETWORK_STATE; WifiManager is not used (and its
 * permission is not allowed): Wi-Fi switched on but joined to no network shows nothing.
 */

/** True while the device is connected to a Wi-Fi network; a state, so only the glyph redraws. */
@Composable
internal fun rememberWifiConnected(): State<Boolean> {
    val context = LocalContext.current
    val connected = remember { mutableStateOf(false) }
    LifecycleStartEffect(context) {
        val watch = WifiWatch(context.getSystemService(ConnectivityManager::class.java)) { connected.value = it }
        watch.start()
        onStopOrDispose {
            watch.stop()
            // Nothing is heard while stopped, so nothing stale is shown on the next start.
            connected.value = false
        }
    }
    return connected
}

/**
 * One registration of the Wi-Fi callback on [manager] (null: no glyph), reporting through
 * [onChange] whether any Wi-Fi network is connected. A failure to register is logged, never thrown.
 */
internal class WifiWatch(
    private val manager: ConnectivityManager?,
    private val onChange: (Boolean) -> Unit,
) {
    // Cleared by stop(): unregistering is asynchronous on older Androids, so a callback already
    // queued can still arrive afterwards and must not touch the state (a stale icon, or an old
    // "lost" overwriting a newer watch's "connected").
    @Volatile private var isActive = false
    private val callback = WifiCallback { if (isActive) onChange(it) }
    private var isRegistered = false

    fun start() {
        if (manager == null) {
            Log.w(WIFI_TAG, "No ConnectivityManager; the Wi-Fi glyph stays hidden")
            return
        }
        isActive = true
        // The capabilities are spelled out (the builder's defaults differ across versions) so the
        // seed below can take exactly the networks this request matches.
        val request =
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .apply { WATCHED_CAPABILITIES.forEach { addCapability(it) } }
                .build()
        try {
            manager.registerNetworkCallback(request, callback)
            isRegistered = true
            seedFromNetworksUp(manager)
        } catch (e: SecurityException) {
            Log.w(WIFI_TAG, "Not allowed to watch the network; the Wi-Fi glyph stays hidden", e)
            onChange(false)
        } catch (e: RuntimeException) {
            // Some builds throw when the system's callback limit is reached.
            Log.w(WIFI_TAG, "Could not watch the network; the Wi-Fi glyph stays hidden", e)
            onChange(false)
        }
    }

    /**
     * Reports a Wi-Fi network that is already up now, so the first frame is right; the callback's
     * own first call comes later over binder and would show the glyph a frame or two late.
     */
    @Suppress("DEPRECATION") // allNetworks is the only synchronous list; present on every API we support.
    private fun seedFromNetworksUp(manager: ConnectivityManager) {
        try {
            for (up in manager.allNetworks) {
                val capabilities = manager.getNetworkCapabilities(up) ?: continue
                if (isWatchedWifi(capabilities)) callback.seed(up)
            }
        } catch (e: RuntimeException) {
            Log.w(WIFI_TAG, "Could not read the networks that are up; the glyph waits for the callback", e)
        }
    }

    fun stop() {
        isActive = false
        if (!isRegistered) return
        isRegistered = false
        try {
            manager?.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.w(WIFI_TAG, "The network callback was already gone", e)
        }
    }
}

/**
 * Whether the callback's request would match [capabilities]: Wi-Fi with [WATCHED_CAPABILITIES],
 * which the request asks for explicitly. The seed must take no more than that, or a network
 * the callback never hears (a VPN over Wi-Fi carries the Wi-Fi transport too) would never be lost.
 */
private fun isWatchedWifi(capabilities: NetworkCapabilities): Boolean =
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        WATCHED_CAPABILITIES.all { capabilities.hasCapability(it) }

private val WATCHED_CAPABILITIES =
    listOf(
        NetworkCapabilities.NET_CAPABILITY_INTERNET,
        NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED,
        NetworkCapabilities.NET_CAPABILITY_TRUSTED,
        NetworkCapabilities.NET_CAPABILITY_NOT_VPN,
    )

/**
 * Keeps the Wi-Fi networks that are up: available adds one, lost (or capabilities without Wi-Fi)
 * removes it, and [onChange] hears whether any is left. Callbacks come on a system thread, the
 * seed on the main thread; [onChange] is called under the lock so two threads never report out of
 * order.
 */
internal class WifiCallback(
    private val onChange: (Boolean) -> Unit,
) : ConnectivityManager.NetworkCallback() {
    private val up = mutableSetOf<Network>()

    // Networks the callback has seen lost: a seed read just before the loss must not bring one back.
    private val lost = mutableSetOf<Network>()

    /** A network already up when watching starts, unless the callback has since heard it lost. */
    fun seed(network: Network) =
        synchronized(up) {
            if (network !in lost) {
                up += network
                onChange(up.isNotEmpty())
            }
        }

    override fun onAvailable(network: Network) = set(network, true)

    override fun onLost(network: Network) = set(network, false)

    override fun onCapabilitiesChanged(
        network: Network,
        capabilities: NetworkCapabilities,
    ) = set(network, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))

    private fun set(
        network: Network,
        isWifi: Boolean,
    ) = synchronized(up) {
        if (isWifi) {
            up += network
            lost -= network
        } else {
            up -= network
            lost += network
        }
        onChange(up.isNotEmpty())
    }
}

private const val WIFI_TAG = "WifiStatus"
