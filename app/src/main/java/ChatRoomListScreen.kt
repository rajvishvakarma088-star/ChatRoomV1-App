import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ChatRoomListScreen(
    authViewModel: AuthViewModel,
    roomViewModel: RoomViewModel = viewModel(), // ✅ Correct type
    onJoinRoom: (Room) -> Unit,
    navController: NavHostController
) {
    val rooms by roomViewModel.rooms.observeAsState(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.padding(top = 22.dp)){
            Text(
            text = "Chat Room",
            color = Color(0xff29a71a),
            style = TextStyle(
                fontSize = 22.371429443359375.sp,
                fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 12.dp))

            Spacer(modifier = Modifier.padding(end = 235.dp))

            var expanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete All") },
                        onClick = {
                            roomViewModel.deleteAllRooms()
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Log out") },
                        onClick = {
                            authViewModel.logout()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(12.dp))

        LazyColumn {
            items(rooms.size) { index ->
                RoomItem(room = rooms[index], onJoinRoom = onJoinRoom , onDeleteRoom = { room ->
                    roomViewModel.deleteRoom(room)
                // ← This calls the delete function in ViewModel
                } )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            )
        ) {
            Text("Create Room")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false }, // ✅ Should dismiss on outside click
            title = { Text("Create a new room" ,
                style = TextStyle(
                    fontSize = 23.371429443359375.sp,
                    fontWeight = FontWeight.Bold)
            ) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            roomViewModel.createRoom(name) // ✅ Correct ViewModel call
                            showDialog = false
                            name = ""
                        }
                    },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        ) ) {
                        Text("Add")
                    }
                    Button(onClick = { showDialog = false } ,
                        colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    )) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun RoomItem(room: Room , onJoinRoom: (Room) -> Unit = {} , onDeleteRoom: (Room) -> Unit)  {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = room.name, fontSize = 16.sp, fontWeight = FontWeight.Normal , modifier = Modifier.padding(top = 12.dp))
        OutlinedButton(
            onClick = {
             onJoinRoom(room)
            }
        ) {
            Text("Join")
        }

        IconButton(onClick = {
            onDeleteRoom(room)
        }){
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

data class Room(
    val id: String = "",
    val name: String = ""
)


