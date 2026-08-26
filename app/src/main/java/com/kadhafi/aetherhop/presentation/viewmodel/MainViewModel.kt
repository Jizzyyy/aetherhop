package com.kadhafi.aetherhop.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = P2pRepositoryImpl(application.applicationContext)
    val messages: StateFlow<Map<String, List<ChatMessage>>> = repository.messages
    val connectionState: StateFlow<P2pConnectionState> = repository.connectionState

    private val _discoveredPeers = MutableStateFlow<List<PeerNode>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerNode>> = _discoveredPeers.asStateFlow()

    init {
        // Collect BLE scan flow and update discovered peers list
        viewModelScope.launch {
            repository.scanBlePeers().collect { peer ->
                _discoveredPeers.update { currentList ->
                    val index = currentList.indexOfFirst { it.id == peer.id }
                    if (index != -1) {
                        currentList.toMutableList().apply { set(index, peer) }
                    } else {
                        currentList + peer
                    }
                }
            }
        }
    }

    val myDeviceName = DeviceIdentity.getDeviceName(application.applicationContext)

    fun connectToPeer(peer: PeerNode) {
        repository.connectToPeer(peer)
    }

    fun sendMessage(targetAddress: String, text: String) {
        repository.sendChatMessage(targetAddress, text, myDeviceName)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopServices()
    }
}
