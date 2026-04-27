import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.chatbot.Result

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatRoomListScreen(
    authViewModel: AuthViewModel,
    roomViewModel: RoomViewModel = viewModel(),
    onJoinRoom: (Room) -> Unit,
    navController: NavHostController
) {
    val rooms by roomViewModel.rooms.observeAsState(emptyList())
    val roomAction by roomViewModel.roomAction.observeAsState(Result.Idle)
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var joinPassword by remember { mutableStateOf("") }

    LaunchedEffect(roomAction) {
        when (val action = roomAction) {
            is Result.Success -> {
                onJoinRoom(action.data)
                roomViewModel.clearAction()
                joinPassword = ""
                selectedRoom = null
            }
            is Result.Error -> {
                snackbarHostState.showSnackbar(action.exception.message ?: "Something went wrong")
                roomViewModel.clearAction()
            }
            else -> Unit
        }
    }

    val activeCalls = rooms.count { it.activeCallType.isNotBlank() }
    val totalMembers = rooms.sumOf { it.memberCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rooms", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Private spaces for text, media, and live calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { roomViewModel.deleteAllRooms() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete all rooms")
                    }
                    IconButton(onClick = { authViewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create room")
            }
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard("Rooms", rooms.size.toString(), Icons.Outlined.ChatBubbleOutline)
                SummaryCard("Members", totalMembers.toString(), Icons.Default.People)
                SummaryCard("Live Calls", activeCalls.toString(), Icons.Default.Call)
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rooms, key = { it.id }) { room ->
                    RoomCard(
                        room = room,
                        onJoin = {
                            if (room.password.isNotEmpty()) {
                                selectedRoom = room
                            } else {
                                roomViewModel.joinRoom(room, "")
                            }
                        },
                        onDelete = { roomViewModel.deleteRoom(room) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, password ->
                roomViewModel.createRoom(name, password)
                showCreateDialog = false
            }
        )
    }

    selectedRoom?.let { room ->
        JoinRoomDialog(
            room = room,
            password = joinPassword,
            onPasswordChange = { joinPassword = it },
            onDismiss = {
                selectedRoom = null
                joinPassword = ""
            },
            onJoin = { roomViewModel.joinRoom(room, joinPassword) }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomCard(room: Room, onJoin: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Host: ${room.createdBy.ifBlank { "Unknown" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete room")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip(
                    icon = Icons.Default.People,
                    label = "${room.memberCount} joined"
                )
                if (room.password.isNotEmpty()) {
                    MetaChip(icon = Icons.Default.Lock, label = "Password")
                }
                if (room.activeCallType.isNotBlank()) {
                    MetaChip(
                        icon = if (room.activeCallType == "video") Icons.Default.VideoCall else Icons.Default.Call,
                        label = "${room.activeCallType.replaceFirstChar(Char::titlecase)} live"
                    )
                }
            }

            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
                Text(if (room.activeCallType.isNotBlank()) "Join room and call" else "Join room")
            }
        }
    }
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var roomName by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roomPassword,
                    onValueChange = { roomPassword = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "A password keeps the room private. Members can still join from multiple devices and accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(roomName, roomPassword) },
                enabled = roomName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun JoinRoomDialog(
    room: Room,
    password: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join ${room.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This room is protected. Enter the room password to join.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onJoin, enabled = password.isNotBlank()) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
