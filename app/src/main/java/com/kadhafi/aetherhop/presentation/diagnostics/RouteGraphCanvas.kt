package com.kadhafi.aetherhop.presentation.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.data.mesh.RouteEntry

@Composable
fun RouteGraphCanvas(
    routes: List<RouteEntry>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)

            // Draw central self-node
            drawCircle(
                color = primaryColor,
                radius = 8.dp.toPx(),
                center = center
            )

            // Draw next-hop directed route vectors
            val count = routes.size.coerceAtLeast(1)
            routes.forEachIndexed { index, route ->
                val angleRad = Math.toRadians((360.0 / count * index))
                val distancePx = 120.dp.toPx() * route.hops
                val endX = center.x + (distancePx * kotlin.math.cos(angleRad)).toFloat()
                val endY = center.y + (distancePx * kotlin.math.sin(angleRad)).toFloat()
                val endOffset = Offset(endX, endY)

                drawLine(
                    color = secondaryColor.copy(alpha = 0.6f),
                    start = center,
                    end = endOffset,
                    strokeWidth = 2f
                )

                drawCircle(
                    color = secondaryColor,
                    radius = 6.dp.toPx(),
                    center = endOffset
                )
            }
        }
    }
}
