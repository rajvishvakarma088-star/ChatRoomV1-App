import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.chatbot.Result
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MessageRepository(private val firestore: FirebaseFirestore) {

    suspend fun sendMessage(roomId: String, message: Message): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .collection("messages").add(message).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun getChatMessages(roomId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("rooms") // Corrected collection name
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Firestore Error: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                println("Received ${messages.size} messages")
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }
}
data class Message(
    val senderFirstName: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByCurrentUser: Boolean = false
)