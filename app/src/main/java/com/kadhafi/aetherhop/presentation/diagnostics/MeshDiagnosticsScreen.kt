package com.kadhafi.aetherhop.presentation.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.data.mesh.LinkQualityCalculator
import com.kadhafi.aetherhop.data.mesh.NodeTelemetry
import com.kadhafi.aetherhop.domain.model.TelemetryBroadcastPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshDiagnosticsScreen(
    telemetryList: List<NodeTelemetry>,
    peerTelemetryMap: Map<String, TelemetryBroadcastPayload> = emptyMap(),
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (telemetryList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_diagnostics),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(telemetryList, key = { it.peerId }) { telemetry ->
                    val broadcast = peerTelemetryMap[telemetry.peerId]
                    val batteryStr = broadcast?.let { " • Baterai: ${it.batteryPercent}%" } ?: ""
                    val lqiScore = LinkQualityCalculator.calculateLqi(-70, telemetry.rttMs, telemetry.packetLossPercentage)
                    val lqiRating = LinkQualityCalculator.getLqiRating(lqiScore)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Node: ${telemetry.peerId}") },
                            supportingContent = {
                                Text("LQI: $lqiScore/100 ($lqiRating)\nRTT: ${telemetry.rttMs} ms • Packet Loss: ${String.format("%.1f", telemetry.packetLossPercentage)}%$batteryStr")
                            },
                            leadingContent = {
                                Icon(Icons.Default.Speed, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}
