package com.kadhafi.aetherhop.presentation.components

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.core.location.BreadcrumbPoint
import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MeshTopologyMapCanvas(
    peers: List<PeerNode> = emptyList(),
    breadcrumbs: List<BreadcrumbPoint> = emptyList(),
    waypoints: List<TacticalWaypointEntity> = emptyList(),
    selectedWaypointId: String? = null,
    azimuthDegrees: Float = 0f,
    currentLocation: Location? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    val ringTextPaint = remember(primaryColor) {
        android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            textSize = 26f
            alpha = 150
            isAntiAlias = true
        }
    }

    val mappedPeers = remember(peers, azimuthDegrees) {
        peers.map { peer ->
            val normDist = if (peer.distanceMeters <= 0) 0.5f else (peer.distanceMeters.toFloat() / 20f).coerceIn(0.1f, 0.95f)
            val baseAngleDeg = (peer.id.hashCode() % 360).toFloat()
            val adjustedAngleDeg = (baseAngleDeg - azimuthDegrees + 360f) % 360f
            val peerAngleRad = Math.toRadians(adjustedAngleDeg.toDouble())

            val dotColor = when {
                peer.rssi > -60 -> Color(0xFF00E676)
                peer.rssi > -80 -> Color(0xFFFFD600)
                else -> Color(0xFFFF5252)
            }

            CalculatedMapNode(
                id = peer.id,
                name = peer.name,
                normDist = normDist,
                angleRad = peerAngleRad,
                dotColor = dotColor
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = min(size.width, size.height) / 2 * 0.85f

            // Compass cardinal rings and tactical grid lines with distance scale labels
            val ringLabels = listOf("25m", "50m", "100m", "200m")
            for (i in 1..4) {
                val ringRadius = maxRadius * (i / 4f)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f * i),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    ringLabels[i - 1],
                    center.x + 8f,
                    center.y - ringRadius + 22f,
                    ringTextPaint
                )
            }

            // Tactical Crosshair Axes (N-S, E-W)
            drawLine(
                color = primaryColor.copy(alpha = 0.25f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.25f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1f
            )

            // Draw tactical waypoints using geodesic calculation if valid GPS or hash angle
            val originLat = currentLocation?.latitude ?: -6.2088
            val originLon = currentLocation?.longitude ?: 106.8456
            waypoints.forEachIndexed { index, wp ->
                val wpDist = maxRadius * 0.7f
                val bearingDeg = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, wp.latitude, wp.longitude)
                val adjustedBearing = (bearingDeg - azimuthDegrees + 360.0) % 360.0
                val wpAngleRad = Math.toRadians(adjustedBearing)

                val wpX = center.x + (wpDist * cos(wpAngleRad)).toFloat()
                val wpY = center.y + (wpDist * sin(wpAngleRad)).toFloat()
                val wpColor = when (wp.type) {
                    "MEDICAL" -> Color(0xFFFF1744)
                    "HAZARD" -> Color(0xFFFFD600)
                    "RENDEZVOUS" -> Color(0xFF00E5FF)
                    else -> Color(0xFF00E676)
                }

                val isSelected = wp.id == selectedWaypointId
                val wpRadius = if (isSelected) 9.dp.toPx() else 7.dp.toPx()

                drawCircle(
                    color = wpColor,
                    radius = wpRadius,
                    center = Offset(wpX, wpY)
                )
                drawCircle(
                    color = wpColor.copy(alpha = if (isSelected) 0.5f else 0.3f),
                    radius = if (isSelected) 22.dp.toPx() else 14.dp.toPx(),
                    center = Offset(wpX, wpY),
                    style = if (isSelected) Stroke(width = 2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
                )

                // If this waypoint is locked, draw navigation guidance needle from center
                if (isSelected) {
                    val needleLength = maxRadius * 0.5f
                    val needleEnd = Offset(
                        center.x + (needleLength * cos(wpAngleRad)).toFloat(),
                        center.y + (needleLength * sin(wpAngleRad)).toFloat()
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3f
                    )
                    // Arrow head pointing to target
                    val arrowAngle1 = wpAngleRad + Math.toRadians(150.0)
                    val arrowAngle2 = wpAngleRad - Math.toRadians(150.0)
                    val arrowHeadSize = 16f
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = needleEnd,
                        end = Offset(
                            needleEnd.x + (arrowHeadSize * cos(arrowAngle1)).toFloat(),
                            needleEnd.y + (arrowHeadSize * sin(arrowAngle1)).toFloat()
                        ),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = needleEnd,
                        end = Offset(
                            needleEnd.x + (arrowHeadSize * cos(arrowAngle2)).toFloat(),
                            needleEnd.y + (arrowHeadSize * sin(arrowAngle2)).toFloat()
                        ),
                        strokeWidth = 3f
                    )
                }
            }

            // Draw live GPS accuracy circle if available
            val accuracyMeters = if (currentLocation != null && currentLocation.hasAccuracy()) currentLocation.accuracy else 0f
            if (accuracyMeters > 0f) {
                val accuracyRadiusPx = (accuracyMeters / 50f * maxRadius).coerceIn(12.dp.toPx(), maxRadius)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = accuracyRadiusPx,
                    center = center
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = accuracyRadiusPx,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // Draw center self-node
            drawCircle(
                color = primaryColor,
                radius = 8.dp.toPx(),
                center = center
            )

            // Draw movement breadcrumb dots
            breadcrumbs.takeLast(10).forEachIndexed { idx, point ->
                val alpha = (idx + 1) / 10f * 0.5f
                val offsetPx = (idx + 1) * 6.dp.toPx()
                drawCircle(
                    color = primaryColor.copy(alpha = alpha),
                    radius = 3.dp.toPx(),
                    center = Offset(center.x - offsetPx, center.y + offsetPx)
                )
            }

            // Draw links and peer nodes
            mappedPeers.forEach { node ->
                val peerRadius = maxRadius * node.normDist
                val peerX = center.x + (peerRadius * cos(node.angleRad)).toFloat()
                val peerY = center.y + (peerRadius * sin(node.angleRad)).toFloat()
                val peerOffset = Offset(peerX, peerY)

                // Mesh topology link vector
                drawLine(
                    color = node.dotColor.copy(alpha = 0.4f),
                    start = center,
                    end = peerOffset,
                    strokeWidth = 1.5f
                )

                // Peer node circle
                drawCircle(
                    color = node.dotColor,
                    radius = 6.dp.toPx(),
                    center = peerOffset
                )
            }
        }
    }
}

private data class CalculatedMapNode(
    val id: String,
    val name: String,
    val normDist: Float,
    val angleRad: Double,
    val dotColor: Color
)
