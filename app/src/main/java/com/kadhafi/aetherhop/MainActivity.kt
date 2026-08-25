package com.kadhafi.aetherhop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.kadhafi.aetherhop.core.theme.AetherHopTheme
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.chat.ChatScreen
import com.kadhafi.aetherhop.presentation.radar.MainRadarScreen
import com.kadhafi.aetherhop.presentation.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AetherHopTheme {
                var selectedPeer by remember { mutableStateOf<PeerNode?>(null) }
                val messages by viewModel.messages.collectAsState()

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
                                viewModel.sendMessage(addr, text)
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
}
