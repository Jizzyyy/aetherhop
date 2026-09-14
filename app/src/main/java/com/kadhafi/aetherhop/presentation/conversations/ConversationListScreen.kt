package com.kadhafi.aetherhop.presentation.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.R
import com.kadhafi.aetherhop.data.local.entity.ConversationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<ConversationEntity>,
    onConversationClick: (ConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit = {},
    onCreateChannelClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var selectedFilterTab by remember { mutableStateOf("ALL") }
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversations_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateChannelClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Channel")
            }
        }
    ) { innerPadding ->
        val filteredConversations = remember(conversations, selectedFilterTab) {
            when (selectedFilterTab) {
                "CHANNELS" -> conversations.filter { it.isChannel }
                "DIRECT" -> conversations.filter { !it.isChannel }
                else -> conversations
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "Semua",
                    "CHANNELS" to "Saluran Publik",
                    "DIRECT" to "Pesan Pribadi"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilterTab == key,
                        onClick = { selectedFilterTab = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_conversations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredConversations, key = { it.conversationId }) { conv ->
                        Card(
                            onClick = { onConversationClick(conv) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(conv.title)
                                        if (conv.isChannel) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "CHANNEL",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                supportingContent = { Text(conv.lastMessageText) },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (conv.isChannel) Icons.Default.Group else Icons.Default.Forum,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (conv.unreadCount > 0) {
                                            Badge { Text("${conv.unreadCount}") }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        IconButton(onClick = { conversationToDelete = conv }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete_action),
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (conversationToDelete != null) {
        val conv = conversationToDelete!!
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(conv.conversationId)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}
