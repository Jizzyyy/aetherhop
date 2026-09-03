package com.kadhafi.aetherhop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
            AetherHopTheme {
                val context = LocalContext.current
                var hasPermissions by remember {
                    mutableStateOf(
                        PermissionChecker.hasRequiredBlePermissions(context) &&
                        PermissionChecker.hasRequiredP2pPermissions(context)
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = PermissionChecker.hasRequiredBlePermissions(context) &&
                                     PermissionChecker.hasRequiredP2pPermissions(context)
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasPermissions = PermissionChecker.hasRequiredBlePermissions(context) &&
                                             PermissionChecker.hasRequiredP2pPermissions(context)
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
                    val conversations by viewModel.conversations.collectAsStateWithLifecycle(emptyList())
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
                    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
                    val azimuthDegrees by viewModel.azimuthDegrees.collectAsStateWithLifecycle()
                    val peerIdentities by viewModel.peerIdentities.collectAsStateWithLifecycle()
                    val myDeviceName by viewModel.myDeviceName.collectAsStateWithLifecycle()
                    val activeSosAlerts by viewModel.activeSosAlerts.collectAsStateWithLifecycle()
                    val powerState by viewModel.powerState.collectAsStateWithLifecycle(null)

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

                    if (showSettings) {
                        SettingsScreen(
                            currentName = myDeviceName,
                            deviceId = viewModel.deviceId,
                            powerState = powerState,
                            onSaveName = { newName ->
                                viewModel.updateDeviceName(newName)
                                viewModel.setShowSettings(false)
                            },
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
                            onBackClick = { viewModel.setShowDiagnostics(false) }
                        )
                    } else if (showConversations) {
                        ConversationListScreen(
                            conversations = conversations,
                            onConversationClick = { conv ->
                                viewModel.setShowConversations(false)
                                viewModel.selectPeer(PeerNode(id = conv.conversationId, name = conv.title, address = conv.conversationId))
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
                                    activeSosAlerts = activeSosAlerts,
                                    pairingPayloadJson = viewModel.pairingPayloadJson,
                                    fingerprintChecksum = viewModel.fingerprintChecksum,
                                    onBroadcastSos = { note ->
                                        viewModel.broadcastSos(note)
                                    },
                                    onDismissSos = { senderId ->
                                        viewModel.dismissSosAlert(senderId)
                                        emergencyPlayer.stopAlert()
                                    },
                                    onDiagnosticsClick = {
                                        viewModel.setShowDiagnostics(true)
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
                        val resolvedName = peerIdentities[peerId] ?: selectedPeer?.name ?: stringResource(R.string.unknown_peer)
                        val peerMessages = messages[selectedPeer?.id] ?: messages[selectedPeer?.address] ?: emptyList()
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            ChatScreen(
                                peerName = resolvedName,
                                messages = peerMessages,
                                connectionState = connectionState,
                                onSendMessage = { text ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendMessage(addr, text)
                                    }
                                },
                                onSendFile = { uri, fileName ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendFile(addr, uri, fileName)
                                    }
                                },
                                onSendVoiceNote = { audioBase64, durationMs ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.sendVoiceNote(addr, audioBase64, durationMs)
                                    }
                                },
                                onRetryMessage = { msgId ->
                                    selectedPeer?.address?.let { addr ->
                                        viewModel.retryMessage(msgId, addr)
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
