package com.riftcompanion.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility for detecting metered (cellular) network connections.
 * Used to warn the user before data-consuming sync operations.
 */
object NetworkUtils {

    /**
     * Returns true if the current active network is metered (cellular or similar).
     * On Android 7+ this uses NetworkCapabilities.NET_CAPABILITY_NOT_METERED.
     */
    fun isMeteredConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return true // no network = treat as metered to be safe
        val caps = cm.getNetworkCapabilities(network) ?: return true
        // If the network lacks NET_CAPABILITY_NOT_METERED, it's metered
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Returns true if the device has any active network connection.
     */
    fun hasNetworkConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
