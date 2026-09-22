package com.kadhafi.aetherhop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.kadhafi.aetherhop.core.audio.TacticalSoundManager
import com.kadhafi.aetherhop.core.location.GeoJsonExporter
import com.kadhafi.aetherhop.core.location.GpxExporter
import com.kadhafi.aetherhop.core.service.MeshForegroundService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kadhafi.aetherhop.core.theme.AetherHopTheme
import com.kadhafi.aetherhop.core.util.EmergencyAlertPlayer
import com.kadhafi.aetherhop.core.util.PermissionChecker
import com.kadhafi.aetherhop.core.util.UiText
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.chat.ChatScreen
import com.kadhafi.aetherhop.presentation.components.SkeletonBox
import com.kadhafi.aetherhop.presentation.conversations.ConversationListScreen
import com.kadhafi.aetherhop.presentation.conversations.CreateChannelDialog
import com.kadhafi.aetherhop.presentation.diagnostics.MeshDiagnosticsScreen
import com.kadhafi.aetherhop.presentation.radar.MainRadarScreen
import com.kadhafi.aetherhop.presentation.settings.SettingsScreen
import com.kadhafi.aetherhop.presentation.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentTheme by viewModel.themePreset.collectAsStateWithLifecycle()
            AetherHopTheme(themePreset = currentTheme) {
                val context = LocalContext.current
                var hasPermissions by remember {
                    mutableStateOf(
                        PermissionChecker.hasRequiredBlePermissions(context) &&
                        PermissionChecker.hasRequiredP2pPermissions(context) &&
                        PermissionChecker.hasRequiredAudioPermissions(context)
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = PermissionChecker.hasRequiredBlePermissions(context) &&
                                     PermissionChecker.hasRequiredP2pPermissions(context) &&
                                     PermissionChecker.hasRequiredAudioPermissions(context)
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasPermissions = PermissionChecker.hasRequiredBlePermissions(context) &&
                                             PermissionChecker.hasRequiredP2pPermissions(context) &&
                                             PermissionChecker.hasRequiredAudioPermissions(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        snackbarHostState.showSnackbar(event.asString(context))
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermissions) {
                        permissionLauncher.launch(PermissionChecker.getRequiredPermissions())
                    }
                }

                if (!hasPermissions) {
                    PermissionRequestScreen(
                        onRequestPermissions = {
                            permissionLauncher.launch(PermissionChecker.getRequiredPermissions())
                        }
                    )
                } else {
                    val selectedPeer by viewModel.selectedPeer.collectAsStateWithLifecycle()
                    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
                    val showConversations by viewModel.showConversations.collectAsStateWithLifecycle()
                    val showDiagnostics by viewModel.showDiagnostics.collectAsStateWithLifecycle()
                    val conversations by viewModel.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
                    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
                    val azimuthDegrees by viewModel.azimuthDegrees.collectAsStateWithLifecycle()
                    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
                    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle(initialValue = emptyList())
                    val peerIdentities by viewModel.peerIdentities.collectAsStateWithLifecycle()
                    val peerTelemetry by viewModel.peerTelemetry.collectAsStateWithLifecycle()
                    val myDeviceName by viewModel.myDeviceName.collectAsStateWithLifecycle()
                    val activeSosAlerts by viewModel.activeSosAlerts.collectAsStateWithLifecycle()
                    val powerState by viewModel.powerState.collectAsStateWithLifecycle(initialValue = null)
                    val currentTheme by viewModel.themePreset.collectAsStateWithLifecycle()
                    val isHapticEnabled by viewModel.isHapticEnabled.collectAsStateWithLifecycle()
                    val operationalStatus by viewModel.operationalStatus.collectAsStateWithLifecycle()
                    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
                    val isBackgroundServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsStateWithLifecycle()
                    val activeRoutes by viewModel.activeRoutes.collectAsStateWithLifecycle()
                    val geofenceBreachAlert by viewModel.geofenceBreachAlert.collectAsStateWithLifecycle()
                    val coordinateFormat by viewModel.coordinateFormat.collectAsStateWithLifecycle()
                    val isGossipSyncing by viewModel.isGossipSyncing.collectAsStateWithLifecycle()
                    val tileCacheStats by viewModel.tileCacheStats.collectAsStateWithLifecycle()
                    val currentThermalState by viewModel.currentThermalState.collectAsStateWithLifecycle()
                    val peerAliases by viewModel.peerAliases.collectAsStateWithLifecycle()
                    val blockedPeers by viewModel.blockedPeers.collectAsStateWithLifecycle()
                    val isSurvivalModeActive by viewModel.isSurvivalModeActive.collectAsStateWithLifecycle()
                    val survivalWindowCountdown by viewModel.survivalWindowCountdown.collectAsStateWithLifecycle()
                    val deadReckoningState by viewModel.deadReckoningState.collectAsStateWithLifecycle()
                    val dualWatchState by viewModel.dualWatchState.collectAsStateWithLifecycle()
                    val currentBitrateConfig by viewModel.currentBitrateConfig.collectAsStateWithLifecycle()

                    LaunchedEffect(geofenceBreachAlert) {
                        if (geofenceBreachAlert != null) {
                            TacticalSoundManager.playPerimeterBreachAlarm()
                        }
                    }

                    LaunchedEffect(hasPermissions, isBackgroundServiceEnabled) {
                        if (hasPermissions && isBackgroundServiceEnabled) {
                            try {
                                val serviceIntent = Intent(context, MeshForegroundService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            } catch (_: Exception) {}
                        } else if (!isBackgroundServiceEnabled) {
                            try {
                                val serviceIntent = Intent(context, MeshForegroundService::class.java)
                                context.stopService(serviceIntent)
                            } catch (_: Exception) {}
                        }
                    }

                    val emergencyPlayer = remember { EmergencyAlertPlayer(context) }
                    LaunchedEffect(activeSosAlerts.size) {
                        if (activeSosAlerts.isNotEmpty()) {
                            emergencyPlayer.startAlert()
                        } else {
                            emergencyPlayer.stopAlert()
                        }
                    }

                    var showCreateChannelDialog by remember { mutableStateOf(false) }

                    if (showCreateChannelDialog) {
                        CreateChannelDialog(
                            onDismiss = { showCreateChannelDialog = false },
                            onCreateChannel = { channelName ->
                                showCreateChannelDialog = false
                                viewModel.sendChannelBroadcast(channelName, "Saluran $channelName dibuat.")
                            }
                        )
                    }

                    var showPasswordDialog by remember { mutableStateOf<String?>(null) }
                    var passwordInputState by remember { mutableStateOf("") }
                    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
                    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

                    val createBackupLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        uri?.let {
                            pendingExportUri = it
                            showPasswordDialog = "export"
                        }
                    }

                    val openBackupLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let {
                            pendingImportUri = it
                            showPasswordDialog = "import"
                        }
                    }

                    if (showPasswordDialog != null) {
                        AlertDialog(
                            onDismissRequest = { showPasswordDialog = null },
                            title = { Text(stringResource(R.string.password_hint)) },
                            text = {
                                OutlinedTextField(
                                    value = passwordInputState,
                                    onValueChange = { passwordInputState = it },
                                    label = { Text(stringResource(R.string.password_hint)) },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val pwd = passwordInputState
                                        val action = showPasswordDialog
                                        showPasswordDialog = null
                                        passwordInputState = ""
                                        if (pwd.isNotBlank()) {
                                            if (action == "export") {
                                                pendingExportUri?.let { uri ->
                                                    context.contentResolver.openOutputStream(uri)?.use { os ->
                                                        viewModel.exportBackup(os, pwd)
                                                    }
                                                }
                                            } else if (action == "import") {
                                                pendingImportUri?.let { uri ->
                                                    context.contentResolver.openInputStream(uri)?.use { isStream ->
                                                        viewModel.importBackup(isStream, pwd)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.save_button))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasswordDialog = null }) {
                                    Text(stringResource(R.string.cancel_button))
                                }
                            }
                        )
                    }

                    val exportChatTxtLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("text/plain")
                    ) { uri ->
                        uri?.let {
                            val activeMessages = messages[selectedPeer?.id] ?: messages[selectedPeer?.address] ?: emptyList()
                            val sb = StringBuilder().apply {
                                appendLine("========================================")
                                appendLine("AETHERHOP OFFLINE ENCRYPTED P2P CHAT LOG")
                                appendLine("Node: ${selectedPeer?.name} (${selectedPeer?.id})")
                                appendLine("Exported: ${java.util.Date()}")
                                appendLine("========================================")
                                activeMessages.forEach { msg ->
                                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(msg.timestamp))
                                    appendLine("[$time] ${msg.senderName}: ${msg.text}")
                                }
                            }
                            try {
                                context.contentResolver.openOutputStream(it)?.use { os ->
                                    os.write(sb.toString().toByteArray(Charsets.UTF_8))
                                }
                                viewModel.triggerUiMessage(R.string.export_chat_success)
                            } catch (_: Exception) {}
                        }
                    }

                    val exportGpxLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
                    ) { uri ->
                        uri?.let {
                            try {
                                val gpxContent = GpxExporter.exportToGpx(
                                    trackName = "AetherHop Tactical Track",
                                    waypoints = waypoints,
                                    breadcrumbs = breadcrumbs
                                )
                                context.contentResolver.openOutputStream(it)?.use { os ->
                                    os.write(gpxContent.toByteArray(Charsets.UTF_8))
                                }
                                viewModel.triggerUiMessage(R.string.export_chat_success)
                            } catch (_: Exception) {}
                        }
                    }

                    val exportGeoJsonLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/geo+json")
                    ) { uri ->
                        uri?.let {
                            try {
                                val geoJsonContent = GeoJsonExporter.exportToGeoJson(
                                    trackName = "AetherHop Tactical Features",
                                    waypoints = waypoints,
                                    breadcrumbs = breadcrumbs
                                )
                                context.contentResolver.openOutputStream(it)?.use { os ->
                                    os.write(geoJsonContent.toByteArray(Charsets.UTF_8))
                                }
                                viewModel.triggerUiMessage(R.string.export_chat_success)
                            } catch (_: Exception) {}
                        }
                    }

                    if (showSettings) {
                        SettingsScreen(
                            currentName = myDeviceName,
                            deviceId = viewModel.deviceId,
                            powerState = powerState,
                            currentTheme = currentTheme,
                            isHapticEnabled = isHapticEnabled,
                            isBackgroundServiceEnabled = isBackgroundServiceEnabled,
                            isSurvivalMode = isSurvivalModeActive,
                            isDualWatchEnabled = dualWatchState.isDualWatchEnabled,
                            coordinateFormat = coordinateFormat,
                            tileCacheStats = tileCacheStats,
                            operationalStatus = operationalStatus,
                            onOperationalStatusChange = { status -> viewModel.updateOperationalStatus(status) },
                            onHapticToggle = { enabled -> viewModel.updateHapticEnabled(enabled) },
                            onBackgroundServiceToggle = { enabled -> viewModel.toggleBackgroundService(enabled) },
                            onSurvivalModeToggle = { enabled -> viewModel.setManualSurvivalMode(enabled) },
                            onDualWatchToggle = { enabled -> viewModel.setDualWatchEnabled(enabled) },
                            onThemeSelect = { preset -> viewModel.updateThemePreset(preset) },
                            onCoordinateFormatSelect = { fmt -> viewModel.updateCoordinateFormat(fmt) },
                            onClearTileCache = { viewModel.clearTileCache() },
                            onSaveName = { newName ->
                                viewModel.updateDeviceName(newName)
                                viewModel.setShowSettings(false)
                            },
                            onExportBackupClick = { createBackupLauncher.launch("aetherhop_backup_${System.currentTimeMillis()}.aetherhop") },
                            onImportBackupClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                            onPanicWipe = {
                                viewModel.panicWipeNode {
                                    viewModel.setShowSettings(false)
                                }
                            },
                            onBackClick = {
                                viewModel.setShowSettings(false)
                            }
                        )
                    } else if (showDiagnostics) {
                        MeshDiagnosticsScreen(
                            telemetryList = viewModel.telemetryList,
                            peerTelemetryMap = peerTelemetry,
                            activeRoutes = activeRoutes,
                            onPingClick = { target -> viewModel.sendPing(target) },
                            onBackClick = { viewModel.setShowDiagnostics(false) }
                        )
                    } else if (showConversations) {
                        ConversationListScreen(
                            conversations = conversations,
                            onConversationClick = { conv ->
                                viewModel.setShowConversations(false)
                                viewModel.selectPeer(PeerNode(id = conv.conversationId, name = conv.title, address = conv.conversationId))
                            },
                            onDeleteConversation = { convId ->
                                viewModel.deleteConversation(convId)
                            },
                            onCreateChannelClick = { showCreateChannelDialog = true },
                            onBackClick = { viewModel.setShowConversations(false) }
                        )
                    } else if (selectedPeer == null) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                MainRadarScreen(
                                    peers = discoveredPeers,
                                    connectionState = connectionState,
                                    isScanning = isScanning,
                                    isBluetoothEnabled = isBluetoothEnabled,
                                    azimuthDegrees = azimuthDegrees,
                                    powerState = powerState,
                                    thermalState = currentThermalState,
                                    breadcrumbs = breadcrumbs,
                                    waypoints = waypoints,
                                    currentLocation = currentLocation,
                                    deadReckoningState = deadReckoningState,
                                    activeSosAlerts = activeSosAlerts,
                                    peerTelemetry = peerTelemetry,
                                    pairingPayloadJson = viewModel.pairingPayloadJson,
                                    fingerprintChecksum = viewModel.fingerprintChecksum,
                                    currentTheme = currentTheme,
                                    coordinateFormat = coordinateFormat,
                                    isGossipSyncing = isGossipSyncing,
                                    isSurvivalMode = isSurvivalModeActive,
                                    survivalCountdownSeconds = survivalWindowCountdown,
                                    pttBitrateLabel = currentBitrateConfig.label,
                                    onImportPairingPayload = { payload -> viewModel.importPairingPayload(payload) },
                                    onExportGpx = {
                                        exportGpxLauncher.launch("aetherhop_tactical_${System.currentTimeMillis()}.gpx")
                                    },
                                    onExportGeoJson = {
                                        exportGeoJsonLauncher.launch("aetherhop_tactical_${System.currentTimeMillis()}.geojson")
                                    },
                                    onToggleNightVision = {
                                        val nextPreset = when (currentTheme) {
                                            com.kadhafi.aetherhop.core.theme.ThemePreset.TACTICAL_RED -> com.kadhafi.aetherhop.core.theme.ThemePreset.TACTICAL_NVG_MONO
                                            com.kadhafi.aetherhop.core.theme.ThemePreset.TACTICAL_NVG_MONO -> com.kadhafi.aetherhop.core.theme.ThemePreset.DEFAULT
                                            else -> com.kadhafi.aetherhop.core.theme.ThemePreset.TACTICAL_RED
                                        }
                                        viewModel.updateThemePreset(nextPreset)
                                    },
                                    onStartPtt = {
                                        val targetAddr = selectedPeer?.address ?: discoveredPeers.firstOrNull()?.address ?: "BROADCAST"
                                        viewModel.startPttStream(targetAddr)
                                    },
                                    onStopPtt = {
                                        viewModel.stopPttStream()
                                    },
                                    onBroadcastSos = { note ->
                                        viewModel.broadcastSos(note)
                                    },
                                    onDismissSos = { senderId ->
                                        viewModel.dismissSosAlert(senderId)
                                        emergencyPlayer.stopAlert()
                                    },
                                    onAddWaypoint = { label, type ->
                                        val lat = currentLocation?.latitude ?: -6.2088
                                        val lon = currentLocation?.longitude ?: 106.8456
                                        viewModel.addWaypoint(label, lat, lon, type)
                                    },
                                    onDeleteWaypoint = { waypointId ->
                                        viewModel.deleteWaypoint(waypointId)
                                    },
                                    onConversationsClick = {
                                        viewModel.setShowConversations(true)
                                    },
                                    onSettingsClick = {
                                        viewModel.setShowSettings(true)
                                    },
                                    onPeerClick = { peer ->
                                        viewModel.selectPeer(peer)
                                        viewModel.connectToPeer(peer)
                                    }
                                )
                            }
                        }
                    } else {
                        val peerId = selectedPeer?.id ?: ""
                        val resolvedName = peerAliases[peerId] ?: peerIdentities[peerId] ?: selectedPeer?.name ?: stringResource(R.string.unknown_peer)
                        val peerMessages = messages[selectedPeer?.id] ?: messages[selectedPeer?.address] ?: emptyList()
                        
                        val peerTelem = viewModel.telemetryList.find { it.peerId == peerId }
                        val lqiRating = peerTelem?.let {
                            val score = com.kadhafi.aetherhop.data.mesh.LinkQualityCalculator.calculateLqi(-70, it.rttMs, it.packetLossPercentage)
                            com.kadhafi.aetherhop.data.mesh.LinkQualityCalculator.getLqiRating(score)
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            ChatScreen(
                                peerName = resolvedName,
                                peerId = peerId,
                                operationalStatus = peerTelemetry[peerId]?.operationalStatus ?: "STANDBY",
                                linkQualityRating = lqiRating,
                                isBlocked = blockedPeers.contains(peerId),
                                isPriorityTransmissionActive = dualWatchState.isPriorityActive,
                                prioritySenderName = dualWatchState.activePrioritySender,
                                messages = peerMessages,
                                connectionState = connectionState,
                                onSendMessage = { text ->
                                    TacticalSoundManager.playTransmitBeep()
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendMessage(addr, text)
                                    }
                                },
                                onSendQuotedMessage = { text, replyToId, replySnippet ->
                                    TacticalSoundManager.playTransmitBeep()
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendQuotedMessage(addr, text, replyToId, replySnippet)
                                    }
                                },
                                onSendReaction = { messageId, emoji ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendReaction(addr, messageId, emoji)
                                    }
                                },
                                onSendFile = { uri, fileName ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendFile(addr, uri, fileName)
                                    }
                                },
                                 onSendVoiceNote = { audioBase64, durationMs, waveform ->
                                     selectedPeer?.address?.let { addr ->
                                         viewModel.sendVoiceNote(addr, audioBase64, durationMs, waveform)
                                     }
                                 },
                                onRetryMessage = { msgId ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.retryMessage(msgId, addr)
                                    }
                                },
                                onDeleteMessage = { msgId ->
                                    viewModel.deleteMessage(msgId)
                                },
                                onSetAlias = { alias ->
                                    viewModel.setPeerAlias(peerId, alias)
                                },
                                onToggleBlock = { blocked ->
                                    viewModel.setPeerBlocked(peerId, blocked)
                                },
                                onExportChatTxt = {
                                    val safeName = selectedPeer?.name?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "peer"
                                    exportChatTxtLauncher.launch("aetherhop_chat_${safeName}_${System.currentTimeMillis()}.txt")
                                },
                                onClearChat = {
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.clearChatMessages(addr)
                                    }
                                },
                                onDeleteConversation = {
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.deleteConversation(addr)
                                        viewModel.selectPeer(null)
                                        viewModel.disconnectPeer()
                                    }
                                },
                                onBackClick = {
                                    viewModel.selectPeer(null)
                                    viewModel.disconnectPeer()
                                }
                            )

                            if (connectionState is P2pConnectionState.Connecting) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        SkeletonBox(
                                            modifier = Modifier.width(180.dp).height(24.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.connecting_socket_overlay),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.permission_required_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_required_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermissions) {
                    Text(stringResource(R.string.grant_permission))
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}
