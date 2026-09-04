package com.kadhafi.aetherhop.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R

@Composable
fun AudioVuMeterOverlay(
    isTransmitting: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isTransmitting) return

    val infiniteTransition = rememberInfiniteTransition(label = "VuMeterAnim")
    val barHeight1 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar1"
    )
    val barHeight2 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar2"
    )
    val barHeight3 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar3"
    )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(4.dp).height(barHeight1.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(4.dp).height(barHeight2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(4.dp).height(barHeight3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(4.dp).height(barHeight1.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            }

            Text(
                text = stringResource(R.string.ptt_transmitting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
