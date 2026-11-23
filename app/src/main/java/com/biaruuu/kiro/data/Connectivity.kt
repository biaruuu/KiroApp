package com.biaruuu.kiro.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow

/** Whether the phone has an internet connection right now (drives the offline banner). */
object Connectivity {
    val online = MutableStateFlow(true)
    private var started = false

    fun isOnline(c: Context): Boolean {
        val cm = c.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Synchronized
    fun start(c: Context) {
        val app = c.applicationContext
        online.value = isOnline(app)
        if (started) return
        started = true
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online.value = true
                    PartySocket.networkBack()
                }
                override fun onLost(network: Network) { online.value = isOnline(app) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        } catch (_: Exception) {
            started = false
        }
    }
}
