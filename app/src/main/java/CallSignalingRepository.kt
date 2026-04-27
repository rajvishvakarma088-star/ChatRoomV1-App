import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallSignalingRepository(
    private val firestore: FirebaseFirestore
) {

    fun observeSession(roomId: String): Flow<WebRtcSession?> = callbackFlow {
        val listener = sessionDocument(roomId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(WebRtcSession::class.java))
        }

        awaitClose { listener.remove() }
    }

    fun observeRemoteCandidates(roomId: String, isCaller: Boolean): Flow<List<SignalIceCandidate>> = callbackFlow {
        val listener = candidateCollection(roomId, if (isCaller) "answerCandidates" else "offerCandidates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val candidates = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(SignalIceCandidate::class.java)?.copy(id = document.id)
                }
                trySend(candidates.sortedBy { it.createdAt })
            }

        awaitClose { listener.remove() }
    }

    suspend fun ensureSession(roomId: String, hostId: String, callType: String) {
        sessionDocument(roomId)
            .set(
                mapOf(
                    "hostId" to hostId,
                    "callType" to callType,
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun saveOffer(roomId: String, hostId: String, callType: String, sdp: String, type: String) {
        sessionDocument(roomId)
            .set(
                mapOf(
                    "hostId" to hostId,
                    "callType" to callType,
                    "offerSdp" to sdp,
                    "offerType" to type,
                    "answerSdp" to "",
                    "answerType" to "",
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun saveAnswer(roomId: String, sdp: String, type: String) {
        sessionDocument(roomId)
            .set(
                mapOf(
                    "answerSdp" to sdp,
                    "answerType" to type,
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun addIceCandidate(roomId: String, isCaller: Boolean, userId: String, candidate: org.webrtc.IceCandidate) {
        candidateCollection(roomId, if (isCaller) "offerCandidates" else "answerCandidates")
            .add(
                SignalIceCandidate(
                    senderId = userId,
                    sdpMid = candidate.sdpMid.orEmpty(),
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    sdpCandidate = candidate.sdp,
                    createdAt = System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun clearSession(roomId: String) {
        deleteCollection(roomId, "offerCandidates")
        deleteCollection(roomId, "answerCandidates")
        sessionDocument(roomId).delete().await()
    }

    private suspend fun deleteCollection(roomId: String, collectionName: String) {
        val snapshot = candidateCollection(roomId, collectionName).get().await()
        snapshot.documents.forEach { it.reference.delete().await() }
    }

    private fun sessionDocument(roomId: String) = firestore.collection("rooms")
        .document(roomId)
        .collection("webrtc")
        .document("session")

    private fun candidateCollection(roomId: String, collectionName: String) = firestore.collection("rooms")
        .document(roomId)
        .collection("webrtc")
        .document("session")
        .collection(collectionName)
}

data class WebRtcSession(
    val hostId: String = "",
    val callType: String = "audio",
    val offerSdp: String = "",
    val offerType: String = "",
    val answerSdp: String = "",
    val answerType: String = "",
    val updatedAt: Long = 0L
)

data class SignalIceCandidate(
    val id: String = "",
    val senderId: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdpCandidate: String = "",
    val createdAt: Long = 0L
)
