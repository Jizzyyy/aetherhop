package com.kadhafi.aetherhop.data.nan

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class WifiAwareState {
    data object Unavailable : WifiAwareState()
    data object Available : WifiAwareState()
    data class Attached(val session: WifiAwareSession) : WifiAwareState()
    data class Error(val message: String) : WifiAwareState()
}

class AetherWifiAwareManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val awareManager: WifiAwareManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        appContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    } else null

    private val _awareState = MutableStateFlow<WifiAwareState>(WifiAwareState.Unavailable)
    val awareState: StateFlow<WifiAwareState> = _awareState.asStateFlow()

    private var currentSession: WifiAwareSession? = null

    fun isAwareSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) && awareManager?.isAvailable == true
        } else false
    }

    fun attachSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || awareManager == null || !isAwareSupported()) {
            _awareState.value = WifiAwareState.Unavailable
            return
        }

        _awareState.value = WifiAwareState.Available
        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                currentSession = session
                _awareState.value = WifiAwareState.Attached(session)
            }

            override fun onAttachFailed() {
                _awareState.value = WifiAwareState.Error("Wi-Fi Aware session attach failed")
            }
        }, null)
    }

    fun closeSession() {
        currentSession?.close()
        currentSession = null
        _awareState.value = if (isAwareSupported()) WifiAwareState.Available else WifiAwareState.Unavailable
    }
}
