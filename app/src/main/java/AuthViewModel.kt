import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.Result
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private var userRepository = UserRepository(
        FirebaseAuth.getInstance(),
        Injection.instance()
    )

    private val _authResult = MutableLiveData<Result<Boolean>>(Result.Idle)
    val authResult: LiveData<Result<Boolean>> = _authResult

    // ✅ NEW: LiveData to track login status
    private val _isUserLoggedIn = MutableLiveData<Boolean>()
    val isUserLoggedIn: LiveData<Boolean> get() = _isUserLoggedIn

    init {
        checkLoginStatus() // ✅ Called once when ViewModel is created
    }

    // ✅ Checks if FirebaseAuth already has a logged-in user
    fun checkLoginStatus() {
        _isUserLoggedIn.value = FirebaseAuth.getInstance().currentUser != null
    }

    fun signUp(email: String, password: String, firstName: String, lastName: String) {
        _authResult.value = Result.Loading
        viewModelScope.launch {
            val result = userRepository.signUp(email, password, firstName, lastName)
            _authResult.value = result
            if (result is Result.Success) {
                _isUserLoggedIn.value = true
            }
        }
    }


    fun login(email: String, password: String) {
        viewModelScope.launch {
            val result = userRepository.login(email, password)
            _authResult.value = result
            if (result is Result.Success) {
                _isUserLoggedIn.value = true
            }
        }
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        _authResult.value = Result.Idle
        _isUserLoggedIn.value = false
    }




}

object Injection {
    private val instance: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    fun instance(): FirebaseFirestore {
        return instance
    }
}
