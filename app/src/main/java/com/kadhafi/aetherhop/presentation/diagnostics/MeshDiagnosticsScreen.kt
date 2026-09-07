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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            ListItem(
                                headlineContent = { Text("Node: ${telemetry.peerId}") },
                                supportingContent = {
                                    Text("LQI: $lqiScore/100 ($lqiRating)\nRTT: ${telemetry.rttMs} ms • Packet Loss: ${String.format("%.1f", telemetry.packetLossPercentage)}%$batteryStr")
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Speed, contentDescription = null)
                                }
                            )

                            if (telemetry.rssiHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tren Sinyal RSSI (15 sampel terakhir):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    val points = telemetry.rssiHistory
                                    val stepX = size.width / (points.size.coerceAtLeast(2) - 1)
                                    val minRssi = -100f
                                    val maxRssi = -40f

                                    for (i in 0 until points.size - 1) {
                                        val p1 = points[i].toFloat().coerceIn(minRssi, maxRssi)
                                        val p2 = points[i + 1].toFloat().coerceIn(minRssi, maxRssi)

                                        val y1 = size.height - ((p1 - minRssi) / (maxRssi - minRssi)) * size.height
                                        val y2 = size.height - ((p2 - minRssi) / (maxRssi - minRssi)) * size.height

                                        drawLine(
                                            color = Color(0xFF00E5FF),
                                            start = Offset(i * stepX, y1),
                                            end = Offset((i + 1) * stepX, y2),
                                            strokeWidth = 2.dp.toPx()
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
}
