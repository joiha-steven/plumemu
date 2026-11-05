package com.plumemu.setup

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Whether the active network, of any kind (Wi-Fi, Ethernet, mobile), can reach the internet. One read, no
 * callback: Ready asks once on entering. A manager that cannot say is "online": the line then makes no
 * false claim about the network.
 */
internal fun hasInternet(manager: ConnectivityManager?): Boolean {
    if (manager == null) return true
    return try {
        val network = manager.activeNetwork ?: return false
        manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: SecurityException) {
        Log.w("SetupOnline", "Not allowed to read the network (${e.javaClass.simpleName})")
        true
    } catch (e: RuntimeException) {
        Log.w("SetupOnline", "Could not read the network (${e.javaClass.simpleName})")
        true
    }
}
