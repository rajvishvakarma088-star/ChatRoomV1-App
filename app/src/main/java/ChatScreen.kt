import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatbot.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatScreen(
    roomId: String,
    roomName : String,
    messageViewModel: MessageViewModel = viewModel()
) {
    val messages by messageViewModel.messages.observeAsState(emptyList())
    val currentUser by messageViewModel.currentUser.observeAsState()
    val text = remember { mutableStateOf("") }



    // Set roomId only once when ChatScreen is launched
    LaunchedEffect(roomId) {
        messageViewModel.setRoomId(roomId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        var expanded by remember { mutableStateOf(false) }
        Row(modifier = Modifier.padding(top = 22.dp)) {
            IconButton(onClick = {}) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
            }

            Text(
                text = roomName,
                style = TextStyle(
                    fontSize = 22.371429443359375.sp,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }

        }
    }

        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true // for bottom-up message view
        ) {
            items(messages.reversed()) { message ->
                ChatMessageItem(
                    message = message.copy(
                        isSentByCurrentUser = message.senderId == currentUser?.email
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                textStyle = TextStyle.Default.copy(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp).size(50.dp),
                shape = RoundedCornerShape(50),

            )

            IconButton(
                onClick = {
                    if (text.value.isNotBlank()) {
                        messageViewModel.sendMessage(text.value.trim())
                        text.value = ""
                    }
                    messageViewModel.loadMessages()
                }
            ) {
                Image(painter = painterResource(R.drawable.group_24 )
                    , contentDescription = null ,
                    modifier = Modifier.size(60.dp),
                    )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatMessageItem(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = if (message.isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (message.isSentByCurrentUser)
                        colorResource(id = R.color.purple_700)
                    else
                        Color.LightGray
                )
                .padding(8.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isSentByCurrentUser) Color.White else Color.Black,
                style = TextStyle(fontSize = 16.sp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.senderFirstName,
            style = TextStyle(fontSize = 12.sp, color = Color.Gray)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
            )
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
private fun formatTimestamp(timestamp: Long): String {
    val messageDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val now = LocalDateTime.now()

    return when {
        isSameDay(messageDateTime, now) -> "today ${formatTime(messageDateTime)}"
        isSameDay(messageDateTime.plusDays(1), now) -> "yesterday ${formatTime(messageDateTime)}"
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
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return formatter.format(dateTime)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
@Preview
fun ChatScreenPreview() {
    ChatScreen(roomId = "sampleRoom" , roomName = "genral")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
@Preview
fun ChatItemPreview() {
    ChatMessageItem(message = Message())
}