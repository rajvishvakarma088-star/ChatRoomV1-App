import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppEntryPoint(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    var checkedAuth by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // ✅ Already logged in — go to ChatRoom screen
            navController.navigate(Screen.ChatRoomsScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            // 🚪 Not logged in — proceed to login/signup
            checkedAuth = true
        }
    }

    if (checkedAuth) {
        NavigationGraph(navController = navController, authViewModel = authViewModel)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}