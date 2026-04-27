import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.chatbot.LoginScreen
import com.example.chatbot.SignUpScreen
import java.net.URLDecoder

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    val isUserLoggedIn by authViewModel.isUserLoggedIn.observeAsState()

    if (isUserLoggedIn == null) return // wait for state to load

    // ✅ Use key() to force re-execution when login state changes
    androidx.compose.runtime.key(isUserLoggedIn) {
        val startDestination = if (isUserLoggedIn == true)
            Screen.ChatRoomsScreen.route
        else
            Screen.LoginScreen.route

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Screen.SignupScreen.route) {
                SignUpScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.LoginScreen.route)
                    },
                    authViewModel = authViewModel
                )
            }

            composable(Screen.LoginScreen.route) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToSignup = {
                        navController.navigate(Screen.SignupScreen.route)
                    },
                    onSignInSuccsess = {
                        navController.navigate(Screen.ChatRoomsScreen.route) {
                            popUpTo(Screen.LoginScreen.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ChatRoomsScreen.route) {
                ChatRoomListScreen(
                    authViewModel = authViewModel,
                    onJoinRoom = { room ->
                        navController.navigate(Screen.ChatScreen.withArgs(room.id, room.name))
                    },
                    navController = navController
                )
            }

            composable("${Screen.ChatScreen.route}/{roomId}/{roomName}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                val roomNameEncoded = backStackEntry.arguments?.getString("roomName") ?: ""
                val roomName = URLDecoder.decode(roomNameEncoded, "UTF-8")

                ChatScreen(
                    roomId = roomId,
                    roomName = roomName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
