import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.chatbot.Result

class RoomRepository(private val firestore: FirebaseFirestore) {

    // ✅ Create a new chat room
    suspend fun createRoom(name: String): Result<Unit> = try {
        val room = Room(name = name)
        firestore.collection("rooms").add(room).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e) // ✅ FIXED
    }

    // ✅ Get all existing chat rooms
    suspend fun getRooms(): Result<List<Room>> = try {
        val querySnapshot = firestore.collection("rooms").get().await()
        val rooms = querySnapshot.documents.mapNotNull { document ->
            document.toObject(Room::class.java)?.copy(id = document.id)
        }
        Result.Success(rooms)
    } catch (e: Exception) {
        Result.Error(e) // ✅ FIXED
    }
}