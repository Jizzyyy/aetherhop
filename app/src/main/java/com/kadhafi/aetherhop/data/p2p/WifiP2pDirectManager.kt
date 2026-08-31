package com.kadhafi.aetherhop.data.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Build
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

class WifiP2pDirectManager(context: Context) {
    private val appContext = context.applicationContext
    private val p2pManager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _connectionState = MutableStateFlow<P2pConnectionState>(P2pConnectionState.Idle)
    val connectionState: StateFlow<P2pConnectionState> = _connectionState.asStateFlow()

    init {
        channel = p2pManager?.initialize(appContext, appContext.mainLooper, null)
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(): Flow<List<WifiP2pDevice>> = callbackFlow {
        val activeChannel = channel
        if (p2pManager == null || activeChannel == null) {
            close()
            return@callbackFlow
        }

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            _connectionState.value = P2pConnectionState.Error("Wi-Fi Direct Disabled")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        p2pManager.requestPeers(activeChannel) { peerList ->
                            trySend(peerList.deviceList.toList())
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            p2pManager.requestConnectionInfo(activeChannel) { info ->
                                if (info.groupFormed) {
                                    val ownerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                                    _connectionState.value = P2pConnectionState.Connected(
                                        groupOwnerAddress = ownerAddress,
                                        isGroupOwner = info.isGroupOwner,
                                        deviceName = "WiFi Direct Peer"
                                    )
                                }
                            }
                        } else {
                            _connectionState.value = P2pConnectionState.Idle
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        // Internal device state changed, handled gracefully
                    }
                }
            }
        }

        appContext.registerReceiver(receiver, intentFilter)
        _connectionState.value = P2pConnectionState.Discovering

        p2pManager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                _connectionState.value = P2pConnectionState.Error("WiFi Direct Discovery Failed: $reason")
            }
        })

        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
                p2pManager.stopPeerDiscovery(activeChannel, null)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (p2pManager == null || channel == null) {
            onError("WiFi Direct unavailable")
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        _connectionState.value = P2pConnectionState.Connecting(device.deviceName ?: "Device")

        p2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                _connectionState.value = P2pConnectionState.Error("Connection Failed: $reason")
                onError("Failed with code: $reason")
            }
        })
    }

    fun disconnect() {
        p2pManager?.removeGroup(channel, null)
        _connectionState.value = P2pConnectionState.Idle
    }
}
