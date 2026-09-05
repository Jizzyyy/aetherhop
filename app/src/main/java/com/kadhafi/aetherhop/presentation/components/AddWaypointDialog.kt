package com.kadhafi.aetherhop.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R

@Composable
fun AddWaypointDialog(
    onDismiss: () -> Unit,
    onAddWaypoint: (label: String, type: String) -> Unit
) {
    var labelState by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("CAMP") }
    val waypointTypes = listOf("CAMP", "HAZARD", "MEDICAL", "RENDEZVOUS")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_waypoint_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = labelState,
                    onValueChange = { labelState = it },
                    label = { Text(stringResource(R.string.waypoint_label_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.waypoint_type_label), style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    waypointTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (labelState.isNotBlank()) {
                        onAddWaypoint(labelState.trim(), selectedType)
                    }
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}
