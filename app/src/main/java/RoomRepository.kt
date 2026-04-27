import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class RoomRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun createRoom(name: String, password: String): Result<Unit> = try {
        val email = auth.currentUser?.email ?: throw IllegalStateException("No authenticated user")
        val room = Room(
            name = name.trim(),
            password = password.trim().takeIf { it.isNotEmpty() }?.let(::hashPassword).orEmpty(),
            createdBy = email,
            members = listOf(email),
            memberCount = 1
        )
        firestore.collection("rooms").add(room).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun joinRoom(room: Room, password: String): Result<Room> {
        return try {
            val email = auth.currentUser?.email ?: throw IllegalStateException("No authenticated user")
            val providedHash = password.trim().takeIf { it.isNotEmpty() }?.let(::hashPassword).orEmpty()
            if (room.password.isNotEmpty() && room.password != providedHash) {
                Result.Error(IllegalArgumentException("Incorrect room password"))
            } else {
                val updatedMembers = (room.members + email).distinct()
                firestore.collection("rooms")
                    .document(room.id)
                    .update(
                        mapOf(
                            "members" to updatedMembers,
                            "memberCount" to updatedMembers.size
                        )
                    )
                    .await()

                Result.Success(room.copy(members = updatedMembers, memberCount = updatedMembers.size))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateCallState(
        roomId: String,
        callType: String?,
        callRoom: String?,
        callHost: String?
    ): Result<Unit> = try {
        firestore.collection("rooms")
            .document(roomId)
            .update(
                mapOf(
                    "activeCallType" to (callType ?: ""),
                    "activeCallRoom" to (callRoom ?: ""),
                    "activeCallHost" to (callHost ?: ""),
                    "activeCallStartedAt" to if (callType == null) 0L else System.currentTimeMillis()
                )
            )
            .await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun listenToRooms(onUpdate: (List<Room>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration {
        return firestore.collection("rooms").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }

            val roomList = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Room::class.java)?.copy(id = doc.id)
            }.orEmpty()

            onUpdate(roomList.sortedByDescending { it.activeCallStartedAt })
        }
    }

    fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        val registration = firestore.collection("rooms")
            .document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Room::class.java)?.copy(id = snapshot.id))
            }

        awaitClose { registration.remove() }
    }

    suspend fun deleteRoom(room: Room): Result<Unit> = try {
        firestore.collection("rooms").document(room.id).delete().await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun deleteAllRooms(): Result<Unit> = try {
        val snapshot = firestore.collection("rooms").get().await()
        snapshot.documents.forEach { it.reference.delete().await() }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }
}

private fun hashPassword(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
