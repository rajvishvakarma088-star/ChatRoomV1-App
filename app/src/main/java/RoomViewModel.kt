import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch


class RoomViewModel : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms
    private val roomRepository: RoomRepository

    init {
        roomRepository = RoomRepository(Injection.instance())
        listenToRooms()
    }

    fun createRoom(name: String) {
        viewModelScope.launch {
            roomRepository.createRoom(name)
        }
    }


    private fun listenToRooms() {
        FirebaseFirestore.getInstance()
            .collection("rooms")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("RoomViewModel", "Listen failed.", e)
                    _rooms.value = emptyList()
                    return@addSnapshotListener
                }

                val roomList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Room::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _rooms.value = roomList
            }


    }
    fun deleteRoom(room: Room) {
        val db = FirebaseFirestore.getInstance()

        db.collection("rooms")
            .document(room.id)
            .delete()
            .addOnSuccessListener {
                Log.d("RoomViewModel", "Room deleted: ${room.name}")
            }
            .addOnFailureListener { e ->
                Log.e("RoomViewModel", "Failed to delete room: ${room.name}", e)
            }
    }

    fun deleteAllRooms() {
        val db = FirebaseFirestore.getInstance()
        db.collection("rooms")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                loadRooms() // Refresh UI
            }
            .addOnFailureListener { e ->
                Log.e("RoomViewModel", "Error deleting all rooms", e)
            }
    }

    fun loadRooms() {
        val db = FirebaseFirestore.getInstance()

        db.collection("rooms")
            .get()
            .addOnSuccessListener { snapshot ->
                val roomList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Room::class.java)?.copy(id = doc.id)
                }
                _rooms.value = roomList
                Log.d("RoomViewModel", "Rooms loaded: ${roomList.size}")
            }
            .addOnFailureListener { e ->
                _rooms.value = emptyList()
                Log.e("RoomViewModel", "Failed to load rooms", e)
            }
    }

}