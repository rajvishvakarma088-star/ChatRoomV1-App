import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.chatbot.Result
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessageRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage = Firebase.storage
) {

    suspend fun sendMessage(roomId: String, message: Message): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .collection("messages")
            .add(message)
            .await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun uploadAttachment(
        context: Context,
        roomId: String,
        uri: Uri,
        attachmentType: String
    ): Result<AttachmentPayload> = try {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri))
            ?.takeIf { it.isNotBlank() }
            ?: "bin"
        val fileName = "rooms/$roomId/${attachmentType}_${UUID.randomUUID()}.$extension"
        val reference = storage.reference.child(fileName)
        reference.putFile(uri).await()
        val downloadUrl = reference.downloadUrl.await().toString()
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "$attachmentType.$extension"
        Result.Success(
            AttachmentPayload(
                url = downloadUrl,
                type = attachmentType,
                name = displayName
            )
        )
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun getChatMessages(roomId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Message::class.java).orEmpty())
            }

        awaitClose { listener.remove() }
    }
}

data class AttachmentPayload(
    val url: String,
    val type: String,
    val name: String
)

data class Message(
    val senderFirstName: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentUrl: String = "",
    val attachmentType: String = "",
    val attachmentName: String = "",
    val messageType: String = "text",
    val isSentByCurrentUser: Boolean = false
)
