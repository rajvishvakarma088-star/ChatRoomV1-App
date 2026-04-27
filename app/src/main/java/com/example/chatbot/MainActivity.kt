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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
            val authViewModel: AuthViewModel = viewModel()
            ChatBotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    NavigationGraph(
                        navController = navController,
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

    fun isValidEmail(value: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()

    fun validateForm(): Boolean {
        return when {
            email.isBlank() || password.isBlank() || firstName.isBlank() || lastName.isBlank() -> {
                errorMessage = "All fields are required"
                false
            }
            !isValidEmail(email) -> {
                errorMessage = "Use a valid email address"
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

    LaunchedEffect(authResult) {
        when (authResult) {
            is Result.Success -> {
                Toast.makeText(context, "Signup successful", Toast.LENGTH_SHORT).show()
                onNavigateToLogin()
            }
            is Result.Error -> {
                errorMessage = (authResult as Result.Error).exception.message ?: "Something went wrong"
            }
            Result.Loading -> errorMessage = null
            else -> Unit
        }
    }

    AuthShell(
        title = "Create your space",
        subtitle = "Rooms, media sharing, and live calls in one place."
    ) {
        val isLoading = authResult == Result.Loading
        AuthCard {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (validateForm()) {
                        authViewModel.signUp(
                            email.trim(),
                            password.trim(),
                            firstName.trim(),
                            lastName.trim()
                        )
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create account")
                }
            }
            Text(
                "Already have an account? Sign in",
                modifier = Modifier.clickable(onClick = onNavigateToLogin),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignup: () -> Unit,
    onSignInSuccsess: () -> Unit
) {
    val authResult by authViewModel.authResult.observeAsState()
    val localContext = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authResult) {
        when (authResult) {
            is Result.Success -> {
                onSignInSuccsess()
                Toast.makeText(localContext, "Login successful", Toast.LENGTH_SHORT).show()
            }
            is Result.Error -> {
                errorMessage = (authResult as Result.Error).exception.message ?: "Login failed"
            }
            Result.Loading -> errorMessage = null
            else -> Unit
        }
    }

    AuthShell(
        title = "Welcome back",
        subtitle = "Jump into secure rooms, shared media, and live sessions."
    ) {
        val isLoading = authResult == Result.Loading
        AuthCard {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { authViewModel.login(email.trim(), password.trim()) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign in")
                }
            }
            Text(
                "Need an account? Create one",
                modifier = Modifier.clickable(onClick = onNavigateToSignup),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AuthShell(title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                        Color(0xFFF5F7FA)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
            }
            content()
        }
    }
}

@Composable
private fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}
