package com.kadhafi.aetherhop.presentation.radar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.components.RadarScanCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRadarScreen(
    peers: List<PeerNode> = emptyList(),
    isScanning: Boolean = true,
    onPeerClick: (PeerNode) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AetherHop", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "BLE Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                RadarScanCanvas(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = isScanning
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PERANGKAT SEKITAR (${peers.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (peers.isEmpty()) {
                        Text(
                            text = "Memindai jaringan radio BLE...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(peers, key = { it.id }) { peer ->
                                Card(
                                    onClick = { onPeerClick(peer) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ListItem(
                                        headlineContent = { Text(peer.name) },
                                        supportingContent = { 
                                            val formattedDist = String.format("%.1f", peer.distanceMeters)
                                            Text("${formattedDist}m • ${peer.rssi} dBm") 
                                        },
                                        leadingContent = {
                                            Icon(Icons.Default.Devices, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
