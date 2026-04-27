import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import kotlinx.coroutines.launch

class RoomViewModel : ViewModel() {

    private val roomRepository = RoomRepository(Injection.instance())

    private val _rooms = MutableLiveData<List<Room>>(emptyList())
    val rooms: LiveData<List<Room>> get() = _rooms

    private val _roomAction = MutableLiveData<Result<Room>>(Result.Idle)
    val roomAction: LiveData<Result<Room>> get() = _roomAction

    init {
        roomRepository.listenToRooms(
            onUpdate = { _rooms.postValue(it) },
            onError = { _roomAction.postValue(Result.Error(it)) }
        )
    }

    fun createRoom(name: String, password: String) {
        viewModelScope.launch {
            when (val result = roomRepository.createRoom(name, password)) {
                is Result.Error -> _roomAction.value = Result.Error(result.exception)
                else -> Unit
            }
        }
    }

    fun joinRoom(room: Room, password: String) {
        _roomAction.value = Result.Loading
        viewModelScope.launch {
            _roomAction.value = roomRepository.joinRoom(room, password)
        }
    }

    fun deleteRoom(room: Room) {
        viewModelScope.launch {
            roomRepository.deleteRoom(room)
        }
    }

    fun deleteAllRooms() {
        viewModelScope.launch {
            roomRepository.deleteAllRooms()
        }
    }

    fun clearAction() {
        _roomAction.value = Result.Idle
    }
}

data class Room(
    val id: String = "",
    val name: String = "",
    val password: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(),
    val memberCount: Int = 0,
    val activeCallType: String = "",
    val activeCallRoom: String = "",
    val activeCallHost: String = "",
    val activeCallStartedAt: Long = 0L
)
