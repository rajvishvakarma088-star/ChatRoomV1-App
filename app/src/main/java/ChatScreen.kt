import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.chatbot.Result
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatScreen(
    roomId: String,
    roomName: String,
    onBack: () -> Unit = {},
    onOpenCall: (videoEnabled: Boolean, autoStart: Boolean) -> Unit = { _, _ -> },
    messageViewModel: MessageViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by messageViewModel.messages.observeAsState(emptyList())
    val currentUser by messageViewModel.currentUser.observeAsState()
    val roomDetails by messageViewModel.roomDetails.observeAsState()
    val composerState by messageViewModel.composerState.observeAsState(Result.Idle)
    val snackbarHostState = remember { SnackbarHostState() }
    var text by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { messageViewModel.sendAttachment(context, it, "image") }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { messageViewModel.sendAttachment(context, it, "audio") }
    }

    LaunchedEffect(roomId) {
        messageViewModel.setRoomId(roomId)
    }

    LaunchedEffect(composerState) {
        when (val state = composerState) {
            is Result.Error -> {
                snackbarHostState.showSnackbar(state.exception.message ?: "Action failed")
                messageViewModel.clearComposerState()
            }
            is Result.Success -> messageViewModel.clearComposerState()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(roomDetails?.name ?: roomName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${roomDetails?.memberCount ?: 0} participants",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onOpenCall(false, true)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Audio call")
                    }
                    IconButton(onClick = {
                        onOpenCall(true, true)
                    }) {
                        Icon(Icons.Default.VideoCall, contentDescription = "Video call")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            roomDetails?.takeIf { it.activeCallType.isNotBlank() }?.let { room ->
                ActiveCallBanner(
                    room = room,
                    onJoin = { onOpenCall(room.activeCallType == "video", true) },
                    onEnd = { messageViewModel.endCall() }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { "${it.timestamp}-${it.senderId}-${it.attachmentUrl}" }) { message ->
                    MessageBubble(
                        message = message.copy(isSentByCurrentUser = message.senderId == currentUser?.email),
                        onAttachmentClick = { url -> openExternal(context, url) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ComposerBar(
                value = text,
                onValueChange = { text = it },
                onSend = {
                    messageViewModel.sendMessage(text)
                    text = ""
                },
                onImagePick = { imagePicker.launch("image/*") },
                onAudioPick = { audioPicker.launch("audio/*") },
                isLoading = composerState == Result.Loading
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActiveCallBanner(room: Room, onJoin: () -> Unit, onEnd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (room.activeCallType == "video") Icons.Default.VideoCall else Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Column {
                    Text(
                        "${room.activeCallType.replaceFirstChar(Char::titlecase)} call is live",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Started by ${room.activeCallHost}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                TextButton(onClick = onJoin) { Text("Join") }
                TextButton(onClick = onEnd) { Text("End") }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onImagePick: () -> Unit,
    onAudioPick: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onImagePick) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Text("Image")
                }
                TextButton(onClick = onAudioPick) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                    Text("Audio")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message") },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(18.dp)
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MessageBubble(message: Message, onAttachmentClick: (String) -> Unit) {
    if (message.messageType == "system") {
        Text(
            text = message.text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSentByCurrentUser) MaterialTheme.colorScheme.primary else Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (message.messageType) {
                    "image" -> {
                        AsyncImage(
                            model = message.attachmentUrl,
                            contentDescription = message.attachmentName,
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onAttachmentClick(message.attachmentUrl) }
                        )
                    }
                    "audio" -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onAttachmentClick(message.attachmentUrl) }
                                .background(Color.Black.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Column {
                                Text(message.attachmentName.ifBlank { "Audio clip" }, fontWeight = FontWeight.Medium)
                                Text("Tap to open", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = if (message.isSentByCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${message.senderFirstName} • ${formatTimestamp(message.timestamp)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun openExternal(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatTimestamp(timestamp: Long): String {
    val messageDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val now = LocalDateTime.now()

    return when {
        isSameDay(messageDateTime, now) -> formatTime(messageDateTime)
        isSameDay(messageDateTime.plusDays(1), now) -> "Yesterday ${formatTime(messageDateTime)}"
        else -> formatDate(messageDateTime)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun isSameDay(dateTime1: LocalDateTime, dateTime2: LocalDateTime): Boolean {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    return dateTime1.format(formatter) == dateTime2.format(formatter)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatTime(dateTime: LocalDateTime): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return formatter.format(dateTime)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatDate(dateTime: LocalDateTime): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    return formatter.format(dateTime)
}
