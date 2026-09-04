package com.kadhafi.aetherhop.data.nan

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class AwareDataPathState {
    data object Idle : AwareDataPathState()
    data class Connected(val network: Network) : AwareDataPathState()
    data class Failed(val reason: String) : AwareDataPathState()
}

class WifiAwareDataLinkManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @RequiresApi(Build.VERSION_CODES.Q)
    fun requestDataPath(discoverySession: DiscoverySession, peerHandle: PeerHandle): Flow<AwareDataPathState> = callbackFlow {
        if (connectivityManager == null) {
            trySend(AwareDataPathState.Failed("ConnectivityManager unavailable"))
            close()
            return@callbackFlow
        }

        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle)
            .setPskPassphrase("AetherHopMeshPass2026")
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(AwareDataPathState.Connected(network))
            }

            override fun onUnavailable() {
                trySend(AwareDataPathState.Failed("Wi-Fi Aware Network unavailable"))
            }

            override fun onLost(network: Network) {
                trySend(AwareDataPathState.Idle)
            }
        }

        connectivityManager.requestNetwork(networkRequest, networkCallback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }
}
