import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallViewModel : ViewModel() {

    private val roomRepository = RoomRepository(Injection.instance())
    private val callRepository = CallRepository(Injection.instance())
    private val userRepository = UserRepository(
        FirebaseAuth.getInstance(),
        Injection.instance()
    )

    private val _room = MutableLiveData<Room?>()
    val room: LiveData<Room?> get() = _room

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> get() = _currentUser

    private val _participants = MutableLiveData<List<CallParticipant>>(emptyList())
    val participants: LiveData<List<CallParticipant>> get() = _participants

    private val _callState = MutableLiveData<Result<String>>(Result.Idle)
    val callState: LiveData<Result<String>> get() = _callState

    private var initializedRoomId: String? = null

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is Result.Success -> _currentUser.value = result.data
                is Result.Error -> _callState.value = Result.Error(result.exception)
                else -> Unit
            }
        }
    }

    fun initialize(roomId: String, roomName: String, requestedType: String, autoStart: Boolean) {
        if (initializedRoomId == roomId) return
        initializedRoomId = roomId

        observeRoom(roomId)
        observeParticipants(roomId)

        viewModelScope.launch {
            while (_currentUser.value == null) {
                kotlinx.coroutines.delay(50)
            }
            val currentRoom = Room(id = roomId, name = roomName)
            _room.value = currentRoom
        }
    }

    private fun observeRoom(roomId: String) {
        viewModelScope.launch {
            roomRepository.observeRoom(roomId).collectLatest { _room.value = it }
        }
    }

    private fun observeParticipants(roomId: String) {
        viewModelScope.launch {
            callRepository.observeParticipants(roomId).collectLatest { _participants.value = it }
        }
    }

    fun ensureJoined(requestedType: String) {
        val room = _room.value ?: return
        val user = _currentUser.value ?: return
        _callState.value = Result.Loading
        viewModelScope.launch {
            _callState.value = when (val result = callRepository.startOrJoinCall(room, user, requestedType)) {
                is Result.Success -> Result.Success("joined")
                is Result.Error -> Result.Error(result.exception)
                Result.Loading -> Result.Loading
                Result.Idle -> Result.Idle
            }
        }
    }

    fun toggleMute() {
        val roomId = _room.value?.id ?: return
        val userId = _currentUser.value?.email ?: return
        val participant = _participants.value?.firstOrNull { it.userId == userId } ?: return
        viewModelScope.launch {
            callRepository.updateParticipant(roomId, userId, mapOf("isMuted" to !participant.isMuted))
        }
    }

    fun toggleCamera() {
        val roomId = _room.value?.id ?: return
        val userId = _currentUser.value?.email ?: return
        val participant = _participants.value?.firstOrNull { it.userId == userId } ?: return
        viewModelScope.launch {
            callRepository.updateParticipant(roomId, userId, mapOf("isCameraEnabled" to !participant.isCameraEnabled))
        }
    }

    fun toggleHand() {
        val roomId = _room.value?.id ?: return
        val userId = _currentUser.value?.email ?: return
        val participant = _participants.value?.firstOrNull { it.userId == userId } ?: return
        viewModelScope.launch {
            callRepository.updateParticipant(roomId, userId, mapOf("isHandRaised" to !participant.isHandRaised))
        }
    }

    fun updateConnectionState(state: String) {
        val roomId = _room.value?.id ?: return
        val userId = _currentUser.value?.email ?: return
        viewModelScope.launch {
            callRepository.updateParticipant(roomId, userId, mapOf("connectionState" to state))
        }
    }

    fun leaveCall() {
        val roomId = _room.value?.id ?: return
        val userId = _currentUser.value?.email ?: return
        viewModelScope.launch {
            callRepository.leaveCall(roomId, userId)
            _callState.value = Result.Success("left")
        }
    }

    fun clearState() {
        _callState.value = Result.Idle
    }
}
