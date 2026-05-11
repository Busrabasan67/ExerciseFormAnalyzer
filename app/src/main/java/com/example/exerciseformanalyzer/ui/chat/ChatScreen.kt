package com.example.exerciseformanalyzer.ui.chat

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.exerciseformanalyzer.model.firestore.FirestoreChatMessage
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    otherUid: String,
    otherUserName: String,
    onNavigateBack: () -> Unit
) {
    val otherUser: FirestoreUser? by viewModel.otherUser.collectAsState(initial = null)
    val currentUserProfile: FirestoreUser? by viewModel.currentUser.collectAsState(initial = null)
    val messages: List<FirestoreChatMessage> by viewModel.messages.collectAsState(initial = emptyList())
    val currentUid = viewModel.getCurrentUid()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(otherUid) {
        viewModel.observeMessages(otherUid)
        viewModel.markConversationRead(otherUid)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val timelineItems = remember(messages) { buildChatTimeline(messages) }

    val displayName = otherUser?.fullName ?: otherUserName

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (otherUser?.profileImageUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(otherUser?.profileImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.ui_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            val context = androidx.compose.ui.platform.LocalContext.current
            Surface(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Mesaj yaz...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        trailingIcon = {
                            if (messageText.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        if (com.example.exerciseformanalyzer.util.NetworkUtils.isNetworkAvailable(context)) {
                                            viewModel.sendMessage(otherUid, messageText, context)
                                            messageText = ""
                                        } else {
                                            android.widget.Toast.makeText(context, "İnternet gerekiyor.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = stringResource(R.string.ui_send),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingVals ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingVals),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ui_no_messages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(timelineItems, key = { _, item ->
                    when (item) {
                        is ChatTimelineItem.DateHeader -> "date_${item.dateKey}"
                        is ChatTimelineItem.Message -> item.message.id.ifBlank { "message_${item.message.createdAt}" }
                    }
                }) { index, item ->
                    when (item) {
                        is ChatTimelineItem.DateHeader -> ChatDateHeader(item.label)
                        is ChatTimelineItem.Message -> {
                            val nextItem = timelineItems.getOrNull(index + 1)
                            val isLastInGroup = nextItem !is ChatTimelineItem.Message || nextItem.message.senderId != item.message.senderId
                            
                            TextMessageCard(
                                message = item.message, 
                                currentUid = currentUid,
                                otherUser = otherUser,
                                currentUser = currentUserProfile,
                                showAvatar = isLastInGroup
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class ChatTimelineItem {
    data class DateHeader(val dateKey: String, val label: String) : ChatTimelineItem()
    data class Message(val message: FirestoreChatMessage) : ChatTimelineItem()
}

private fun buildChatTimeline(messages: List<FirestoreChatMessage>): List<ChatTimelineItem> {
    val items = mutableListOf<ChatTimelineItem>()
    var lastDateKey: String? = null
    messages.sortedBy { it.createdAt }.forEach { message ->
        val dateKey = chatDateKey(message.createdAt)
        if (dateKey != lastDateKey) {
            items += ChatTimelineItem.DateHeader(dateKey, chatDateLabel(message.createdAt))
            lastDateKey = dateKey
        }
        items += ChatTimelineItem.Message(message)
    }
    return items
}

private fun chatDateKey(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp.coerceAtLeast(0L) }
    return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
}

private fun chatDateLabel(timestamp: Long): String {
    val locale = Locale("tr", "TR")
    val messageDate = Calendar.getInstance(locale).apply { timeInMillis = timestamp.coerceAtLeast(0L) }
    val today = Calendar.getInstance(locale)
    
    val yesterday = Calendar.getInstance(locale).apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) && 
        messageDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Bugün"
        messageDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && 
        messageDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Dün"
        else -> SimpleDateFormat("d MMMM yyyy", locale).format(messageDate.time)
    }
}

@Composable
private fun ChatDateHeader(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TextMessageCard(
    message: FirestoreChatMessage, 
    currentUid: String,
    otherUser: FirestoreUser?,
    currentUser: FirestoreUser?,
    showAvatar: Boolean
) {
    val isMe = message.senderId == currentUid
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val userProfile = if (isMe) currentUser else otherUser
    
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val timeColor = textColor.copy(alpha = 0.7f)
    
    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            if (showAvatar) UserAvatar(userProfile) else Spacer(modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = textColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    val timeStr = sdf.format(message.createdAt)
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = timeColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            if (showAvatar) UserAvatar(userProfile) else Spacer(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun UserAvatar(user: FirestoreUser?) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (user?.profileImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(user.profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (user?.fullName?.take(1) ?: "?").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
