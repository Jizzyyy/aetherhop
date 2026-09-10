package com.kadhafi.aetherhop.presentation.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kadhafi.aetherhop.domain.model.MessageStatus
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.kadhafi.aetherhop.core.audio.AudioPlayerManager
import com.kadhafi.aetherhop.core.audio.AudioRecorderManager
import com.kadhafi.aetherhop.presentation.pairing.SafetyNumberVerificationDialog
import com.kadhafi.aetherhop.presentation.components.SkeletonBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerName: String,
    peerId: String = "",
    operationalStatus: String = "STANDBY",
    messages: List<ChatMessage>,
    connectionState: P2pConnectionState = P2pConnectionState.Idle,
    onSendMessage: (String) -> Unit,
    onSendQuotedMessage: (text: String, replyToId: String, replySnippet: String) -> Unit = { _, _, _ -> },
    onSendReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onSendFile: (Uri, String) -> Unit = { _, _ -> },
    onSendVoiceNote: (String, Long) -> Unit = { _, _ -> },
    onRetryMessage: (String) -> Unit = {},
    onExportChatTxt: () -> Unit = {},
    onClearChat: () -> Unit = {},
    onDeleteConversation: () -> Unit = {},
    onBackClick: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var reactionPickerMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var showSafetyNumberDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioRecorder = remember { AudioRecorderManager(context) }
    val audioPlayer = remember { AudioPlayerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stopPlayback()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
            onSendFile(it, fileName)
        }
    }

    BackHandler(onBack = onBackClick)

    val statusText = when (connectionState) {
        is P2pConnectionState.Connected -> stringResource(R.string.chat_status_connected)
        is P2pConnectionState.Connecting -> stringResource(R.string.chat_status_connecting)
        is P2pConnectionState.Error -> stringResource(R.string.chat_status_error, connectionState.message)
        else -> stringResource(R.string.chat_status_idle)
    }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) || it.senderName.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_chat_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showSafetyNumberDialog = true }
                            ) {
                                Text(peerName, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = operationalStatus.ifBlank { "STANDBY" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.encrypted_session_badge),
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_chat_txt)) },
                                onClick = {
                                    showMenu = false
                                    onExportChatTxt()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear_chat)) },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_delete_conversation)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                if (connectionState is P2pConnectionState.Connecting || connectionState is P2pConnectionState.Discovering) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SkeletonBox(modifier = Modifier.width(160.dp).height(36.dp), shape = RoundedCornerShape(12.dp))
                        SkeletonBox(modifier = Modifier.width(220.dp).height(48.dp).align(Alignment.End), shape = RoundedCornerShape(12.dp))
                        SkeletonBox(modifier = Modifier.width(180.dp).height(40.dp), shape = RoundedCornerShape(12.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_chat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = false
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            audioPlayerManager = audioPlayer,
                            onReplyClick = { replyingToMessage = msg },
                            onReactionClick = { reactionPickerMessage = msg },
                            onFileClick = { path ->
                                try {
                                    val file = java.io.File(path)
                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUri, "*/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Error opening file", e)
                                }
                            },
                            onRetryClick = { onRetryMessage(msg.id) }
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.imePadding()
            ) {
                Column {
                    replyingToMessage?.let { replyTarget ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Membalas ${replyTarget.senderName}:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(replyTarget.text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Batal Balas", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    val cannedResponses = listOf(
                        stringResource(R.string.canned_safe),
                        stringResource(R.string.canned_med),
                        stringResource(R.string.canned_rendezvous),
                        stringResource(R.string.canned_battery)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cannedResponses.forEach { response ->
                            AssistChip(
                                onClick = { onSendMessage(response) },
                                label = { Text(response, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { if (it.length <= 500) textState = it },
                        placeholder = { Text(stringResource(R.string.type_message_hint)) },
                        supportingText = {
                            if (textState.isNotEmpty()) {
                                Text(
                                    text = "${textState.length}/500",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (textState.length > 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                val result = audioRecorder.stopRecording()
                                isRecording = false
                                result?.let {
                                    onSendVoiceNote(it.audioBase64, it.durationMs)
                                }
                            } else {
                                val started = audioRecorder.startRecording()
                                isRecording = started
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                val replyTarget = replyingToMessage
                                if (replyTarget != null) {
                                    onSendQuotedMessage(textState, replyTarget.id, replyTarget.text.take(40))
                                    replyingToMessage = null
                                } else {
                                    onSendMessage(textState)
                                }
                                textState = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
    }

    if (showSafetyNumberDialog) {
        val simulatedSafetyNumber = (peerId + peerName).hashCode().toString().padStart(32, '7').take(32)
        SafetyNumberVerificationDialog(
            peerName = peerName,
            safetyNumber = simulatedSafetyNumber,
            onDismiss = { showSafetyNumberDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_chat_title)) },
            text = { Text(stringResource(R.string.clear_chat_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearChat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteConversation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (reactionPickerMessage != null) {
        val targetMsg = reactionPickerMessage!!
        val emojis = listOf("👍", "❤️", "⚠️", "🚨", "✅")
        AlertDialog(
            onDismissRequest = { reactionPickerMessage = null },
            title = { Text("Pilih Reaksi") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    emojis.forEach { emoji ->
                        TextButton(onClick = {
                            reactionPickerMessage = null
                            onSendReaction(targetMsg.id, emoji)
                        }) {
                            Text(emoji, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reactionPickerMessage = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    audioPlayerManager: AudioPlayerManager? = null,
    onReplyClick: () -> Unit = {},
    onReactionClick: () -> Unit = {},
    onFileClick: (String) -> Unit = {},
    onRetryClick: () -> Unit = {}
) {
    val alignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val senderLabel = if (message.isMine) "Me" else message.senderName
    val clipboardManager = LocalClipboardManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$senderLabel: ${message.text}"
            },
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isMine) 16.dp else 4.dp,
                bottomEnd = if (message.isMine) 4.dp else 16.dp
            ),
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (message.text.startsWith("[Berkas") && !message.mediaUri.isNullOrBlank()) {
                        onFileClick(message.mediaUri)
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    clipboardManager.setText(AnnotatedString(message.text))
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.replySnippet != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "“${message.replySnippet}”",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                if (!message.isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val formattedText = remember(message.text) {
                        ChatTextFormatter.format(message.text)
                    }
                if (message.text.startsWith("[Pesan Suara]") && !message.mediaUri.isNullOrBlank()) {
                    val isPlaying = audioPlayerManager?.isPlaying?.collectAsState()?.value == true &&
                            audioPlayerManager?.playingVoiceId?.collectAsState()?.value == message.id

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayerManager?.stopPlayback()
                                } else {
                                    try {
                                        val file = java.io.File(message.mediaUri)
                                        if (file.exists()) {
                                            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                                            audioPlayerManager?.playVoiceNote(message.id, b64)
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause Voice Note",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = formattedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                    val formattedTime = remember(message.timestamp) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (message.isMine) {
                        when (message.status) {
                            MessageStatus.PENDING -> Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.cd_status_pending),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            MessageStatus.SENT -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_status_sent),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            MessageStatus.FAILED -> IconButton(
                                onClick = onRetryClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = stringResource(R.string.cd_status_failed),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    if (message.reactions.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.reactions.forEach { (emoji, count) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "$emoji $count",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = onReactionClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.AddReaction, contentDescription = "Add Reaction", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = onReplyClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
