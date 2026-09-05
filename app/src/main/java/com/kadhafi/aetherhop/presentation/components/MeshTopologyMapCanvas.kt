package com.kadhafi.aetherhop.presentation.components

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
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.core.location.BreadcrumbPoint
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
    azimuthDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

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

            // Compass cardinal rings and tactical grid lines
            for (i in 1..4) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f * i),
                    radius = maxRadius * (i / 4f),
                    center = center,
                    style = Stroke(width = 1.2f)
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

            // Draw tactical waypoints
            waypoints.forEachIndexed { index, wp ->
                val wpDist = maxRadius * 0.7f
                val wpAngleRad = Math.toRadians(((wp.id.hashCode() % 360) - azimuthDegrees + 360.0) % 360.0)
                val wpX = center.x + (wpDist * cos(wpAngleRad)).toFloat()
                val wpY = center.y + (wpDist * sin(wpAngleRad)).toFloat()
                val wpColor = when (wp.type) {
                    "MEDICAL" -> Color(0xFFFF1744)
                    "HAZARD" -> Color(0xFFFFD600)
                    "RENDEZVOUS" -> Color(0xFF00E5FF)
                    else -> Color(0xFF00E676)
                }

                drawCircle(
                    color = wpColor,
                    radius = 7.dp.toPx(),
                    center = Offset(wpX, wpY)
                )
                drawCircle(
                    color = wpColor.copy(alpha = 0.3f),
                    radius = 14.dp.toPx(),
                    center = Offset(wpX, wpY)
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
