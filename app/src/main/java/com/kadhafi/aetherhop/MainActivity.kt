package com.kadhafi.aetherhop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.kadhafi.aetherhop.core.theme.AetherHopTheme
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.chat.ChatScreen
import com.kadhafi.aetherhop.presentation.radar.MainRadarScreen

class MainActivity : ComponentActivity() {

    private lateinit var repository: P2pRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = P2pRepositoryImpl(applicationContext)

        setContent {
            AetherHopTheme {
                var selectedPeer by remember { mutableStateOf<PeerNode?>(null) }
                val messages by repository.messages.collectAsState()

                if (selectedPeer == null) {
                    MainRadarScreen(
                        peers = emptyList(),
                        isScanning = true,
                        onPeerClick = { peer ->
                            selectedPeer = peer
                        }
                    )
                } else {
                    ChatScreen(
                        peerName = selectedPeer?.name ?: "Peer",
                        messages = messages,
                        onSendMessage = { text ->
                            selectedPeer?.address?.let { addr ->
                                repository.sendChatMessage(addr, text, "Me")
                            }
                        },
                        onBackClick = {
                            selectedPeer = null
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.stopServices()
    }
}
