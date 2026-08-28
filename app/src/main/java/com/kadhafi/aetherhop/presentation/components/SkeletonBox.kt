package com.kadhafi.aetherhop.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
    )
}
