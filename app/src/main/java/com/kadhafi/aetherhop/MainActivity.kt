package com.kadhafi.aetherhop

import android.Manifest
import android.os.Build
import android.os.Bundle
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

                LaunchedEffect(Unit) {
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                if (!hasPermissions) {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                }

                if (!hasPermissions) {
                    PermissionRequestScreen(
                        onRequestPermissions = {
                            permissionLauncher.launch(getRequiredPermissions())
                        }
                    )
                } else {
                    var selectedPeer by remember { mutableStateOf<PeerNode?>(null) }
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
                                        selectedPeer = peer
                                        viewModel.connectToPeer(peer)
                                    }
                                )
                            }
                        }
                    } else {
                        val peerId = selectedPeer?.id ?: ""
                        val resolvedName = peerIdentities[peerId] ?: selectedPeer?.name ?: "Peer"
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
                                onBackClick = {
                                    selectedPeer = null
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
                                            text = "Menghubungkan socket P2P...",
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

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions.toTypedArray()
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
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
                text = "Izin Diperlukan",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "AetherHop membutuhkan izin Bluetooth, Wi-Fi Direct, dan Lokasi untuk mendeteksi perangkat sekitar secara P2P.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermissions) {
                Text("Berikan Izin")
            }
        }
    }
}
