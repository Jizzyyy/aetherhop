package com.kadhafi.aetherhop.presentation.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.core.qr.QrCodeGenerator
import com.kadhafi.aetherhop.domain.model.PeerPairingPayload
import kotlinx.serialization.json.Json

@Composable
fun QrPairingDialog(
    pairingPayloadJson: String,
    fingerprintChecksum: String,
    onImportPayload: (PeerPairingPayload) -> Unit = {},
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(pairingPayloadJson) {
        QrCodeGenerator.generateQrBitmap(pairingPayloadJson, 512)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var inputJson by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var verifiedPayload by remember { mutableStateOf<PeerPairingPayload?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_pairing_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Tampilkan QR") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Input Manual") }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        text = stringResource(R.string.qr_pairing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(10.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.fingerprint_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fingerprintChecksum.take(16),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Tempel JSON payload atau string pairing rekan untuk memverifikasi sidik jari:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputJson,
                        onValueChange = {
                            inputJson = it
                            parseError = null
                        },
                        placeholder = { Text("{\"deviceId\":\"...\",\"deviceName\":\"...\"}") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )

                    parseError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }

                    verifiedPayload?.let { p ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Terverifikasi: ${p.deviceName}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("FP: ${p.checksumFingerprint}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            try {
                                val trimmed = inputJson.trim()
                                val parsed = Json.decodeFromString<PeerPairingPayload>(trimmed)
                                verifiedPayload = parsed
                                parseError = null
                                onImportPayload(parsed)
                            } catch (e: Exception) {
                                parseError = "Format JSON tidak valid atau korup: ${e.localizedMessage}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifikasi & Pasangkan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss_button))
            }
        }
    )
}
