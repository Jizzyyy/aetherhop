package com.kadhafi.aetherhop.presentation.conversations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R

@Composable
fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreateChannel: (String) -> Unit
) {
    var channelNameState by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_channel_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = channelNameState,
                    onValueChange = { channelNameState = it },
                    label = { Text(stringResource(R.string.channel_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val raw = channelNameState.trim()
                    if (raw.isNotBlank()) {
                        val formatted = if (raw.startsWith("#")) raw else "#$raw"
                        onCreateChannel(formatted)
                    }
                }
            ) {
                Text(stringResource(R.string.create_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}
