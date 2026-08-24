package com.kadhafi.aetherhop.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.pow

class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun scanPeers(): Flow<PeerNode> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val rssi = result.rssi
                val name = device.name ?: "Unknown Peer"
                val distance = calculateDistance(rssi, result.txPower)

                val node = PeerNode(
                    id = device.address,
                    name = name,
                    address = device.address,
                    rssi = rssi,
                    distanceMeters = distance
                )
                trySend(node)
            }
        }

        scanner.startScan(callback)
        awaitClose {
            scanner.stopScan(callback)
        }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val measuredPower = if (txPower != 127) txPower else -59
        return 10.0.pow((measuredPower - rssi) / (10 * 2.0))
    }
}
