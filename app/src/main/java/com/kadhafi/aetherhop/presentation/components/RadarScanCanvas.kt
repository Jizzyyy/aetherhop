package com.kadhafi.aetherhop.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarScanCanvas(
    peers: List<PeerNode> = emptyList(),
    modifier: Modifier = Modifier,
    isScanning: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Angle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = min(size.width, size.height) / 2 * 0.85f

            for (i in 1..3) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.15f * i),
                    radius = maxRadius * (i / 3f),
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            drawLine(
                color = primaryColor.copy(alpha = 0.2f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1f
            )

            if (isScanning) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.2f),
                            primaryColor.copy(alpha = 0.6f)
                        ),
                        center = center
                    ),
                    startAngle = angle - 90f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                    size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                )
            }

            // Plot discovered peer nodes relative to distance and hash angle
            peers.forEach { peer ->
                val normDist = if (peer.distanceMeters <= 0) 0.5f else (peer.distanceMeters.toFloat() / 20f).coerceIn(0.1f, 0.95f)
                val peerRadius = maxRadius * normDist
                val peerAngleRad = Math.toRadians((peer.id.hashCode() % 360).toDouble())
                
                val peerX = center.x + (peerRadius * cos(peerAngleRad)).toFloat()
                val peerY = center.y + (peerRadius * sin(peerAngleRad)).toFloat()

                val dotColor = when {
                    peer.rssi > -60 -> Color(0xFF00E676)
                    peer.rssi > -80 -> Color(0xFFFFD600)
                    else -> Color(0xFFFF5252)
                }

                drawCircle(
                    color = dotColor,
                    radius = 6.dp.toPx(),
                    center = Offset(peerX, peerY)
                )
                drawCircle(
                    color = dotColor.copy(alpha = 0.3f),
                    radius = 12.dp.toPx(),
                    center = Offset(peerX, peerY)
                )
            }
        }
    }
}
