import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val signalingRepository: CallSignalingRepository = CallSignalingRepository(firestore)
) {

    suspend fun startOrJoinCall(
        room: Room,
        user: User,
        requestedType: String
    ): Result<Unit> = try {
        val existingParticipants = firestore.collection("rooms")
            .document(room.id)
            .collection("callParticipants")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(CallParticipant::class.java) }

        if (existingParticipants.none { it.userId == user.email } && existingParticipants.size >= 2) {
            Result.Error(IllegalStateException("This call currently supports 2 participants"))
        } else {
            val activeType = room.activeCallType.ifBlank { requestedType }
            val activeHost = room.activeCallHost.ifBlank { user.email }
            val activeConferenceRoom = room.activeCallRoom.ifBlank {
                buildConferenceRoomId(room.id, activeType)
            }

            firestore.collection("rooms")
                .document(room.id)
                .update(
                    mapOf(
                        "activeCallType" to activeType,
                        "activeCallRoom" to activeConferenceRoom,
                        "activeCallHost" to activeHost,
                        "activeCallStartedAt" to if (room.activeCallStartedAt == 0L) System.currentTimeMillis() else room.activeCallStartedAt
                    )
                )
                .await()

            upsertParticipant(
                roomId = room.id,
                participant = CallParticipant(
                    userId = user.email,
                    displayName = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user.email },
                    joinedAt = System.currentTimeMillis(),
                    isMuted = false,
                    isCameraEnabled = activeType == "video",
                    isHandRaised = false,
                    isHost = activeHost == user.email,
                    connectionState = "connecting"
                )
            )

            Result.Success(Unit)
        }
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun updateParticipant(
        roomId: String,
        userId: String,
        updates: Map<String, Any>
    ): Result<Unit> = try {
        firestore.collection("rooms")
            .document(roomId)
            .collection("callParticipants")
            .document(userId)
            .update(updates)
            .await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun leaveCall(roomId: String, userId: String): Result<Unit> = try {
        val participantsRef = firestore.collection("rooms")
            .document(roomId)
            .collection("callParticipants")

        participantsRef.document(userId).delete().await()

        val remaining = participantsRef.get().await().documents.mapNotNull {
            it.toObject(CallParticipant::class.java)
        }.sortedBy { it.joinedAt }

        val roomRef = firestore.collection("rooms").document(roomId)
        if (remaining.isEmpty()) {
            signalingRepository.clearSession(roomId)
            roomRef.update(
                mapOf(
                    "activeCallType" to "",
                    "activeCallRoom" to "",
                    "activeCallHost" to "",
                    "activeCallStartedAt" to 0L
                )
            ).await()
        } else if (remaining.size == 1) {
            signalingRepository.clearSession(roomId)
            roomRef.update(
                mapOf(
                    "activeCallType" to "",
                    "activeCallRoom" to "",
                    "activeCallHost" to "",
                    "activeCallStartedAt" to 0L
                )
            ).await()
        } else {
            val nextHost = remaining.first()
            remaining.forEach { participant ->
                participantsRef.document(participant.userId)
                    .update("isHost", participant.userId == nextHost.userId)
                    .await()
            }
            roomRef.update("activeCallHost", nextHost.userId).await()
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun endCallForRoom(roomId: String): Result<Unit> = try {
        val roomRef = firestore.collection("rooms").document(roomId)
        val participantsRef = roomRef.collection("callParticipants")
        participantsRef.get().await().documents.forEach { it.reference.delete().await() }
        signalingRepository.clearSession(roomId)
        roomRef.update(
            mapOf(
                "activeCallType" to "",
                "activeCallRoom" to "",
                "activeCallHost" to "",
                "activeCallStartedAt" to 0L
            )
        ).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun observeParticipants(roomId: String): Flow<List<CallParticipant>> = callbackFlow {
        val registration = firestore.collection("rooms")
            .document(roomId)
            .collection("callParticipants")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val participants = snapshot?.documents
                    ?.mapNotNull { it.toObject(CallParticipant::class.java) }
                    .orEmpty()
                    .sortedWith(compareByDescending<CallParticipant> { it.isHost }.thenBy { it.joinedAt })

                trySend(participants)
            }

        awaitClose { registration.remove() }
    }

    private suspend fun upsertParticipant(roomId: String, participant: CallParticipant) {
        firestore.collection("rooms")
            .document(roomId)
            .collection("callParticipants")
            .document(participant.userId)
            .set(participant)
            .await()
    }
}

fun buildConferenceRoomId(roomId: String, callType: String): String {
    return "chatbot_${roomId}_${callType}".replace(Regex("[^A-Za-z0-9_]"), "_")
}

data class CallParticipant(
    val userId: String = "",
    val displayName: String = "",
    val joinedAt: Long = 0L,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isHandRaised: Boolean = false,
    val isHost: Boolean = false,
    val connectionState: String = "connecting"
)
