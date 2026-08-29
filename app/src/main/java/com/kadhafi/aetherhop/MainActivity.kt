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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kadhafi.aetherhop.core.theme.AetherHopTheme
import com.kadhafi.aetherhop.core.util.PermissionChecker
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.presentation.chat.ChatScreen
import com.kadhafi.aetherhop.presentation.components.SkeletonBox
import com.kadhafi.aetherhop.presentation.radar.MainRadarScreen
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

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { message ->
                        snackbarHostState.showSnackbar(message)
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
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
                    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
                    val peerIdentities by viewModel.peerIdentities.collectAsStateWithLifecycle()

                    if (selectedPeer == null) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                MainRadarScreen(
                                    peers = discoveredPeers,
                                    connectionState = connectionState,
                                    isScanning = isScanning,
                                    isBluetoothEnabled = isBluetoothEnabled,
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

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
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
