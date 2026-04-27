package com.example.chatbot

import AuthViewModel
import NavigationGraph
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.chatbot.ui.theme.ChatBotTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val authViewModel : AuthViewModel = viewModel()
            ChatBotTheme {

                Scaffold(modifier = Modifier.fillMaxSize()) {
                    NavigationGraph(navController = navController,
                        authViewModel = authViewModel
                        )
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {

    val authResult by authViewModel.authResult.observeAsState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ✅ 👇 Add your validation functions right here
    fun isValidEmail(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun validateForm(): Boolean {
        return when {
            email.isBlank() || password.isBlank() || firstName.isBlank() || lastName.isBlank() -> {
                errorMessage = "All fields are required"
                false
            }
            !isValidEmail(email) -> {
                errorMessage = "Invalid email format"
                false
            }
            password.length < 6 -> {
                errorMessage = "Password must be at least 6 characters"
                false
            }
            else -> {
                errorMessage = null
                true
            }
        }
    }

    // ✅ 👇 Observe result and show feedback
    LaunchedEffect(authResult) {
        when (authResult) {
            is Result.Success -> {
                Toast.makeText(context, "Signup successful", Toast.LENGTH_SHORT).show()
                email = ""
                password = ""
                firstName = ""
                lastName = ""
                onNavigateToLogin()
            }
            is Result.Error -> {
                val e = (authResult as Result.Error).exception.message
                errorMessage = e ?: "Something went wrong"
            }
            Result.Loading -> {
                errorMessage = "Please wait..."
            }
            else -> {}
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isLoading = authResult == Result.Loading

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        )
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        )
        Button(
            colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White,
        ),
            onClick = {
                if (validateForm()) { // ✅ validate before signup
                    authViewModel.signUp(
                        email.trim(),
                        password.trim(),
                        firstName.trim(),
                        lastName.trim()
                    )
                }
            }, enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp) ,
            shape = RoundedCornerShape(30)
        ) {
            Text(if (isLoading) "Signing Up..." else "Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Already have an account? Sign in.",
            modifier = Modifier.clickable {
                onNavigateToLogin()
            }
        )
    }
}

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignup:() -> Unit,
    onSignInSuccsess:()-> Unit

) {
    val authResult by authViewModel.authResult.observeAsState()
    var email by remember { mutableStateOf("") }
    var password by remember {
        mutableStateOf("")
    }

    val localContext = LocalContext.current

    LaunchedEffect(authResult) {
        when (authResult) {
            is Result.Success -> {
                println(" Login successful!")
                onSignInSuccsess()
                Toast.makeText(localContext , "login Successful", Toast.LENGTH_SHORT).show()

            }
            is Result.Error -> {
                val error = (authResult as Result.Error).exception
                Toast.makeText(localContext , "login failed", Toast.LENGTH_SHORT).show()

                // You can show a Snackbar or Toast here
            }
            else -> {
                // Loading or initial state

            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30),
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White ),
            onClick = {
                authViewModel.login(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(30)
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Don't have an account? Sign up.",
            modifier = Modifier.clickable {
                onNavigateToSignup()
            }
        )
    }
}

//@Preview
//@Composable
//fun LoginPreview() {
//    LoginScreen()
//}
//
//    @Preview
//    @Composable
//    fun SignupPreview() {
//        SignUpScreen()
//    }