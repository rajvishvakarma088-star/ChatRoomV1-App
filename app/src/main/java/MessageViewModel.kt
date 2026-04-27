import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    // Repositories
    private val messageRepository = MessageRepository(Injection.instance())
    private val userRepository = UserRepository(
        FirebaseAuth.getInstance(),
        Injection.instance()
    )

    // Observable message list
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> get() = _messages

    // Holds the current room ID
    private val _roomId = MutableLiveData<String?>()

    // Holds the current logged-in user
    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> get() = _currentUser

    // Load user when ViewModel is created
    init {
        loadCurrentUser()
    }

    // Loads the current user from Firebase and updates LiveData
    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result: Result<User> = userRepository.getCurrentUser()) {
                is Result.Success -> {
                    _currentUser.value = result.data
                }
                is Result.Error -> {
                    // Optional: Show error to user or log it
                    println("⚠️ Failed to load user: ${result.exception.message}")
                }
                else -> Unit
            }
        }
    }

    // Called when user enters a chat room
    fun setRoomId(roomId: String) {
        _roomId.value = roomId
        loadMessages()
    }

    // Fetch messages for the current room in real-time
    fun loadMessages() {
        viewModelScope.launch {
            _roomId.value?.let { id ->
                messageRepository.getChatMessages(id).collect { messages ->
                    _messages.value = messages
                }
            }
        }
    }

    // Send a new message
    fun sendMessage(text: String) {
        val roomId = _roomId.value
        val user = _currentUser.value

        if (roomId != null && user != null) {
            val message = Message(
                senderFirstName = user.firstName,
                senderId = user.email,
                text = text
            )

            viewModelScope.launch {
                when(val result = messageRepository.sendMessage(roomId, message)) {
                    is Result.Success -> {
                        // You can show a toast/snackbar for success
                        println("✅ Message sent")
                    }
                    is Result.Error -> {
                        // Optional: show error message
                        println("❌ Failed to send message: ${result.exception.message}")
                    }

                    Result.Idle -> TODO()
                    Result.Loading -> TODO()
                }
            }
        }
    }
}