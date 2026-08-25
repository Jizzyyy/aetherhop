package com.kadhafi.aetherhop.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = P2pRepositoryImpl(application.applicationContext)
    val messages: StateFlow<List<ChatMessage>> = repository.messages
    val connectionState: StateFlow<P2pConnectionState> = repository.connectionState

    fun scanBlePeers(): Flow<PeerNode> = repository.scanBlePeers()
    fun scanWifiPeers() = repository.scanWifiPeers()

    fun sendMessage(targetAddress: String, text: String) {
        repository.sendChatMessage(targetAddress, text, "Me")
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopServices()
    }
}
