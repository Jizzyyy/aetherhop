package com.kadhafi.aetherhop.presentation.radar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.kadhafi.aetherhop.core.theme.SignalWarning
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.components.RadarScanCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRadarScreen(
    peers: List<PeerNode> = emptyList(),
    connectionState: P2pConnectionState = P2pConnectionState.Idle,
    isScanning: Boolean = true,
    isBluetoothEnabled: Boolean = true,
    onPeerClick: (PeerNode) -> Unit = {}
) {
    val statusColor = when (connectionState) {
        is P2pConnectionState.Connected -> Color(0xFF00E676)
        is P2pConnectionState.Connecting, is P2pConnectionState.Discovering -> SignalWarning
        is P2pConnectionState.Error -> MaterialTheme.colorScheme.error
        is P2pConnectionState.Idle -> MaterialTheme.colorScheme.primary
    }

    val statusText = when (connectionState) {
        is P2pConnectionState.Connected -> "Terhubung: ${connectionState.deviceName}"
        is P2pConnectionState.Connecting -> "Menghubungkan ke ${connectionState.deviceName}..."
        is P2pConnectionState.Discovering -> "Mencari jaringan Wi-Fi Direct..."
        is P2pConnectionState.Error -> connectionState.message
        is P2pConnectionState.Idle -> "Radio Standby"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AetherHop", style = MaterialTheme.typography.titleLarge)
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "BLE Status",
                        tint = statusColor,
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
            if (!isBluetoothEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bluetooth mati. Harap aktifkan Bluetooth untuk memindai perangkat sekitar.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                RadarScanCanvas(
                    peers = peers,
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
                            modifier = Modifier.heightIn(max = 280.dp),
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
                                            val distText = if (peer.distanceMeters < 0) "Uncertain" else "${String.format("%.1f", peer.distanceMeters)}m"
                                            Text("$distText • ${peer.rssi} dBm") 
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
