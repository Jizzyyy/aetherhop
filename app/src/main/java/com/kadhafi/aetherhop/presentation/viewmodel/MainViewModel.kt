package com.kadhafi.aetherhop.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.core.location.CompassSensorManager
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.core.util.UiText
import com.kadhafi.aetherhop.data.local.entity.ConversationEntity
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.model.SosPayload
import com.kadhafi.aetherhop.domain.repository.P2pRepository
import com.kadhafi.aetherhop.core.util.KeyExchangeManager
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerPairingPayload
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: P2pRepository = P2pRepositoryImpl(application.applicationContext)
    val messages: StateFlow<Map<String, List<ChatMessage>>> = repository.messages
    val connectionState: StateFlow<P2pConnectionState> = repository.connectionState
    val peerIdentities: StateFlow<Map<String, String>> = repository.peerIdentities
    val activeSosAlerts: StateFlow<List<SosPayload>> = repository.activeSosAlerts
    val conversations: Flow<List<ConversationEntity>> = repository.conversations

    private val _showConversations = MutableStateFlow(false)
    val showConversations: StateFlow<Boolean> = _showConversations.asStateFlow()

    private val compassSensorManager = CompassSensorManager(application.applicationContext)

    private val _discoveredPeers = MutableStateFlow<List<PeerNode>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerNode>> = _discoveredPeers.asStateFlow()

    private val _azimuthDegrees = MutableStateFlow(0f)
    val azimuthDegrees: StateFlow<Float> = _azimuthDegrees.asStateFlow()

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(repository.isBluetoothEnabled())
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _selectedPeer = MutableStateFlow<PeerNode?>(null)
    val selectedPeer: StateFlow<PeerNode?> = _selectedPeer.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun selectPeer(peer: PeerNode?) {
        _selectedPeer.value = peer
    }

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    fun setShowConversations(show: Boolean) {
        _showConversations.value = show
    }

    fun sendChannelBroadcast(channelId: String, text: String) {
        repository.sendChannelBroadcast(channelId, text)
    }

    init {
        // Observe Compass sensor azimuth orientation
        viewModelScope.launch {
            compassSensorManager.observeAzimuthDegrees().collect { azimuth ->
                _azimuthDegrees.value = azimuth
            }
        }

        // Observe Bluetooth state reactively
        viewModelScope.launch {
            repository.observeBluetoothState().distinctUntilChanged().collect { enabled ->
                _isBluetoothEnabled.value = enabled
            }
        }

        // Collect BLE scan flow and update discovered peers list
        viewModelScope.launch {
            repository.scanBlePeers().collect { peer ->
                _discoveredPeers.update { currentList ->
                    val index = currentList.indexOfFirst { it.id == peer.id }
                    if (index != -1) {
                        currentList.toMutableList().apply { set(index, peer.copy(lastSeenTimestamp = System.currentTimeMillis())) }
                    } else {
                        currentList + peer.copy(lastSeenTimestamp = System.currentTimeMillis())
                    }
                }
            }
        }

        // Periodic sweep to remove stale peers (last seen > 30 seconds)
        viewModelScope.launch {
            while (true) {
                delay(15000)
                val now = System.currentTimeMillis()
                _discoveredPeers.update { currentList ->
                    currentList.filter { now - it.lastSeenTimestamp <= 30000 }
                }
            }
        }

        // Collect WiFi Direct peer discovery flow and merge into discoveredPeers
        viewModelScope.launch {
            repository.wifiPeers.collect { devices ->
                _discoveredPeers.update { currentList ->
                    val newList = currentList.toMutableList()
                    devices.forEach { device ->
                        val name = device.deviceName.ifBlank { "WiFi Direct Peer" }
                        val id = device.deviceAddress
                        val index = newList.indexOfFirst { it.id == id || it.name == name }
                        if (index != -1) {
                            val existing = newList[index]
                            newList[index] = existing.copy(name = name, address = id)
                        } else {
                            newList.add(
                                PeerNode(
                                    id = id,
                                    name = name,
                                    address = id,
                                    rssi = -50,
                                    distanceMeters = 5.0
                                )
                            )
                        }
                    }
                    newList
                }
            }
        }
    }

    private val _myDeviceName = MutableStateFlow(DeviceIdentity.getDeviceName(application.applicationContext))
    val myDeviceName: StateFlow<String> = _myDeviceName.asStateFlow()

    val deviceId: String = repository.getDeviceId()

    val pairingPayloadJson: String
        get() {
            val payload = PeerPairingPayload(
                deviceId = deviceId,
                deviceName = _myDeviceName.value,
                publicKeyBase64 = "",
                checksumFingerprint = deviceId.take(16)
            )
            return kotlinx.serialization.json.Json.encodeToString(payload)
        }

    val fingerprintChecksum: String
        get() = deviceId.take(16)

    fun updateDeviceName(name: String) {
        if (name.isBlank()) return
        repository.setDeviceName(name)
        _myDeviceName.value = name.trim()
    }

    private val _uiEvents = MutableSharedFlow<UiText>()
    val uiEvents: SharedFlow<UiText> = _uiEvents.asSharedFlow()

    fun connectToPeer(peer: PeerNode) {
        if (connectionState.value is P2pConnectionState.Connecting) return
        val success = repository.connectToPeer(peer)
        if (!success) {
            viewModelScope.launch {
                _uiEvents.emit(UiText.StringResource(R.string.peer_not_found_error))
            }
        }
    }

    fun disconnectPeer() {
        repository.disconnectPeer()
    }

    fun sendMessage(targetAddress: String, text: String) {
        repository.sendChatMessage(targetAddress, text, _myDeviceName.value)
    }

    fun sendVoiceNote(targetAddress: String, audioBase64: String, durationMs: Long) {
        repository.sendVoiceNote(targetAddress, audioBase64, durationMs)
    }

    fun sendFile(targetAddress: String, uri: Uri, fileName: String) {
        repository.sendFileAttachment(targetAddress, uri, fileName)
    }

    fun broadcastSos(note: String) {
        repository.broadcastSos(note)
    }

    fun dismissSosAlert(senderId: String) {
        repository.dismissSosAlert(senderId)
    }

    fun retryMessage(messageId: String, targetAddress: String) {
        repository.retrySendMessage(messageId, targetAddress)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopServices()
    }
}
