package com.kadhafi.aetherhop.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformView(
    waveformBars: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color = activeColor.copy(alpha = 0.3f),
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bars = remember(waveformBars) {
        if (waveformBars.isNotEmpty()) {
            waveformBars
        } else {
            listOf(
                0.25f, 0.4f, 0.7f, 0.3f, 0.6f, 0.85f, 0.45f, 0.65f, 0.9f, 0.5f,
                0.35f, 0.75f, 0.8f, 0.4f, 0.6f, 0.95f, 0.7f, 0.5f, 0.8f, 0.35f,
                0.6f, 0.75f, 0.45f, 0.85f
            )
        }
    }

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(bars) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalBars = bars.size
            val barSpacing = 2.dp.toPx()
            val totalSpacing = (totalBars - 1) * barSpacing
            val availableWidth = (size.width - totalSpacing).coerceAtLeast(totalBars * 2.dp.toPx())
            val barWidth = (availableWidth / totalBars).coerceAtLeast(2.dp.toPx())
            val maxBarHeight = size.height

            val activeIndex = (progress * totalBars).toInt()

            bars.forEachIndexed { index, amp ->
                val barHeight = (amp * maxBarHeight).coerceIn(4.dp.toPx(), maxBarHeight)
                val x = index * (barWidth + barSpacing)
                val y = (maxBarHeight - barHeight) / 2

                val color = if (index <= activeIndex && progress > 0f) activeColor else inactiveColor

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}
