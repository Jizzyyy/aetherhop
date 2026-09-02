package com.kadhafi.aetherhop.presentation.radar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.core.theme.SignalWarning
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.domain.model.SosPayload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import com.kadhafi.aetherhop.presentation.components.MeshTopologyMapCanvas
import com.kadhafi.aetherhop.presentation.components.RadarScanCanvas
import com.kadhafi.aetherhop.presentation.components.SkeletonBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRadarScreen(
    peers: List<PeerNode> = emptyList(),
    connectionState: P2pConnectionState = P2pConnectionState.Idle,
    isScanning: Boolean = true,
    isBluetoothEnabled: Boolean = true,
    azimuthDegrees: Float = 0f,
    activeSosAlerts: List<SosPayload> = emptyList(),
    onBroadcastSos: (String) -> Unit = {},
    onDismissSos: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onPeerClick: (PeerNode) -> Unit = {}
) {
    var showSosDialog by remember { mutableStateOf(false) }
    var sosNoteState by remember { mutableStateOf("") }
    var isMapView by remember { mutableStateOf(false) }
    val statusColor = when (connectionState) {
        is P2pConnectionState.Connected -> Color(0xFF00E676)
        is P2pConnectionState.Connecting, is P2pConnectionState.Discovering -> SignalWarning
        is P2pConnectionState.Error -> MaterialTheme.colorScheme.error
        is P2pConnectionState.Idle -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val statusText = when (connectionState) {
        is P2pConnectionState.Connected -> stringResource(R.string.status_connected, connectionState.deviceName)
        is P2pConnectionState.Connecting -> stringResource(R.string.status_connecting, connectionState.deviceName)
        is P2pConnectionState.Discovering -> stringResource(R.string.status_discovering)
        is P2pConnectionState.Error -> connectionState.message
        is P2pConnectionState.Idle -> stringResource(R.string.status_idle)
        else -> stringResource(R.string.status_idle)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSosDialog = true },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "SOS")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sos_button))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AetherHop", style = MaterialTheme.typography.titleLarge)
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                },
                actions = {
                    IconButton(onClick = { isMapView = !isMapView }) {
                        Icon(
                            imageVector = if (isMapView) Icons.Default.Radar else Icons.Default.Map,
                            contentDescription = if (isMapView) stringResource(R.string.view_mode_radar) else stringResource(R.string.view_mode_map),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
            if (activeSosAlerts.isNotEmpty()) {
                val latestSos = activeSosAlerts.last()
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${latestSos.senderName}: ${latestSos.emergencyNote}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Text(
                                text = stringResource(R.string.sos_alert_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(
                            onClick = { onDismissSos(latestSos.senderId) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onError)
                        ) {
                            Text(stringResource(R.string.dismiss_button))
                        }
                    }
                }
            }

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
                            text = stringResource(R.string.bluetooth_disabled),
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
                if (isMapView) {
                    MeshTopologyMapCanvas(
                        peers = peers,
                        azimuthDegrees = azimuthDegrees,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    RadarScanCanvas(
                        peers = peers,
                        modifier = Modifier.fillMaxSize(),
                        isScanning = isScanning
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.nearby_devices, peers.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (peers.isEmpty()) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(3) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        SkeletonBox(modifier = Modifier.size(36.dp), shape = androidx.compose.foundation.shape.CircleShape)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            SkeletonBox(modifier = Modifier.width(140.dp).height(16.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SkeletonBox(modifier = Modifier.width(80.dp).height(12.dp))
                                        }
                                    }
                                }
                            }
                        }
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
                                            val distText = if (peer.distanceMeters < 0) stringResource(R.string.uncertain_distance) else "${String.format("%.1f", peer.distanceMeters)}m"
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

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text(stringResource(R.string.sos_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sos_dialog_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sosNoteState,
                        onValueChange = { sosNoteState = it },
                        placeholder = { Text(stringResource(R.string.sos_note_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosDialog = false
                        onBroadcastSos(sosNoteState.ifBlank { "Butuh Bantuan Darurat!" })
                        sosNoteState = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.sos_broadcast_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}
