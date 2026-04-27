import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    private val messageRepository = MessageRepository(Injection.instance())
    private val roomRepository = RoomRepository(Injection.instance())
    private val callRepository = CallRepository(Injection.instance())
    private val userRepository = UserRepository(
        FirebaseAuth.getInstance(),
        Injection.instance()
    )

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> get() = _messages

    private val _roomDetails = MutableLiveData<Room?>()
    val roomDetails: LiveData<Room?> get() = _roomDetails

    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> get() = _currentUser

    private val _composerState = MutableLiveData<Result<String>>(Result.Idle)
    val composerState: LiveData<Result<String>> get() = _composerState

    private val _roomId = MutableLiveData<String?>()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is Result.Success -> _currentUser.value = result.data
                is Result.Error -> _composerState.value = Result.Error(result.exception)
                else -> Unit
            }
        }
    }

    fun setRoomId(roomId: String) {
        if (_roomId.value == roomId) return
        _roomId.value = roomId
        observeMessages(roomId)
        observeRoom(roomId)
    }

    private fun observeMessages(roomId: String) {
        viewModelScope.launch {
            messageRepository.getChatMessages(roomId).collectLatest { _messages.value = it }
        }
    }

    private fun observeRoom(roomId: String) {
        viewModelScope.launch {
            roomRepository.observeRoom(roomId).collectLatest { _roomDetails.value = it }
        }
    }

    fun sendMessage(text: String) {
        val roomId = _roomId.value ?: return
        val user = _currentUser.value ?: return
        if (text.isBlank()) return

        _composerState.value = Result.Loading
        viewModelScope.launch {
            _composerState.value = messageRepository.sendMessage(
                roomId = roomId,
                message = Message(
                    senderFirstName = user.firstName,
                    senderId = user.email,
                    text = text.trim(),
                    messageType = "text"
                )
            ).mapToComposerResult()
        }
    }

    fun sendAttachment(context: Context, uri: Uri, attachmentType: String) {
        val roomId = _roomId.value ?: return
        val user = _currentUser.value ?: return

        _composerState.value = Result.Loading
        viewModelScope.launch {
            when (val uploadResult = messageRepository.uploadAttachment(context, roomId, uri, attachmentType)) {
                is Result.Success -> {
                    val payload = uploadResult.data
                    _composerState.value = messageRepository.sendMessage(
                        roomId = roomId,
                        message = Message(
                            senderFirstName = user.firstName,
                            senderId = user.email,
                            text = if (attachmentType == "audio") "Shared an audio clip" else "",
                            attachmentUrl = payload.url,
                            attachmentType = payload.type,
                            attachmentName = payload.name,
                            messageType = payload.type
                        )
                    ).mapToComposerResult()
                }
                is Result.Error -> _composerState.value = Result.Error(uploadResult.exception)
                else -> Unit
            }
        }
    }

    fun startCall(videoEnabled: Boolean) {
        val room = _roomDetails.value ?: return
        val email = _currentUser.value?.email ?: return
        val callType = if (videoEnabled) "video" else "audio"
        val callRoom = buildConferenceRoomId(room.id, callType)

        viewModelScope.launch {
            roomRepository.updateCallState(room.id, callType, callRoom, email)
            messageRepository.sendMessage(
                room.id,
                Message(
                    senderFirstName = "System",
                    senderId = email,
                    text = if (videoEnabled) "Video call started" else "Voice call started",
                    messageType = "system"
                )
            )
        }
    }

    fun endCall() {
        val room = _roomDetails.value ?: return
        val email = _currentUser.value?.email ?: return
        viewModelScope.launch {
            callRepository.endCallForRoom(room.id)
            messageRepository.sendMessage(
                room.id,
                Message(
                    senderFirstName = "System",
                    senderId = email,
                    text = "Call ended",
                    messageType = "system"
                )
            )
        }
    }

    fun clearComposerState() {
        _composerState.value = Result.Idle
    }
}

private fun Result<Unit>.mapToComposerResult(): Result<String> {
    return when (this) {
        is Result.Success -> Result.Success("ok")
        is Result.Error -> Result.Error(exception)
        Result.Loading -> Result.Loading
        Result.Idle -> Result.Idle
    }
}
