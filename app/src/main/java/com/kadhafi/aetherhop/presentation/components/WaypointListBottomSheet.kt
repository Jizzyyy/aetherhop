package com.kadhafi.aetherhop.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointListBottomSheet(
    waypoints: List<TacticalWaypointEntity>,
    currentLat: Double = -6.2088,
    currentLon: Double = 106.8456,
    onDeleteWaypoint: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.waypoint_list_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (waypoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_waypoints),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(waypoints, key = { it.id }) { wp ->
                        val distMeters = GeodesicCalculator.calculateDistanceMeters(currentLat, currentLon, wp.latitude, wp.longitude)
                        val bearingDeg = GeodesicCalculator.calculateForwardBearingDegrees(currentLat, currentLon, wp.latitude, wp.longitude)
                        val formattedDist = GeodesicCalculator.formatDistance(distMeters)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                headlineContent = { Text(wp.label) },
                                supportingContent = {
                                    Text("Tipe: ${wp.type} • $formattedDist • Azimuth: ${bearingDeg.toInt()}°")
                                },
                                leadingContent = {
                                    val iconColor = when (wp.type) {
                                        "MEDICAL" -> Color(0xFFFF1744)
                                        "HAZARD" -> Color(0xFFFFD600)
                                        "RENDEZVOUS" -> Color(0xFF00E5FF)
                                        else -> Color(0xFF00E676)
                                    }
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = iconColor)
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteWaypoint(wp.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Waypoint",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
