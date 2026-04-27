import com.example.chatbot.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): Result<Boolean> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("User is null")

            firestore.collection("users").document(user.uid).set(
                mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email
                )
            ).await()

            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Boolean> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        Result.Success(true)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun getCurrentUser(): Result<User> {
        val firebaseUser = auth.currentUser ?: return Result.Error(Exception("No authenticated user found"))

        return try {
            val snapshot = firestore.collection("users").document(firebaseUser.uid).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                Result.Success(user)
            } else {
                Result.Error(Exception("User not found in Firestore"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
