package com.example.agriscout.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}

/**
 * Small connectivity helper used by SyncRepository and the ViewModel.
 * WorkManager still owns CONNECTED constraints; this avoids noisy FAILED marks
 * when the device is clearly offline during a manual or in-flight sync.
 */
class NetworkConnectivityMonitor(
    context: Context
) : ConnectivityChecker {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Registers a callback for connectivity changes.
     * @return unregister function
     */
    fun registerConnectivityListener(onChanged: (Boolean) -> Unit): () -> Unit {
        val manager = connectivityManager ?: return {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onChanged(true)
            }

            override fun onLost(network: Network) {
                onChanged(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                onChanged(online)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        return runCatching {
            manager.registerNetworkCallback(request, callback)
            onChanged(isOnline())
            val unregister: () -> Unit = { runCatching { manager.unregisterNetworkCallback(callback) } }
            unregister
        }.getOrElse { {} }
    }
}
