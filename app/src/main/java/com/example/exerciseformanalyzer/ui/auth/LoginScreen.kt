package com.example.exerciseformanalyzer.ui.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke

import com.example.exerciseformanalyzer.ui.components.LanguageSlidingToggle

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (role: String) -> Unit,
    onLanguageChange: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkAutoLogin()
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            val successState = uiState as AuthUiState.Success
            onLoginSuccess(successState.role.uppercase())
            viewModel.resetState()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dil Butonu (Sağ Üst - Biraz daha aşağıda)
        LanguageSlidingToggle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp),
            onLanguageChange = onLanguageChange
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show)
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            var rememberMe by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it }
                )
                Text(text = stringResource(R.string.remember_me))
                Spacer(modifier = Modifier.weight(1f))
                val context = LocalContext.current
                val resetSuccessMsg = stringResource(R.string.password_reset_sent)
                TextButton(onClick = { 
                    viewModel.sendPasswordResetEmail(email) {
                        android.widget.Toast.makeText(context, resetSuccessMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text(stringResource(R.string.forgot_password), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is AuthUiState.Error) {
                Text(
                    text = (uiState as AuthUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = { viewModel.login(email, password, rememberMe) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is AuthUiState.Loading
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text(stringResource(R.string.no_account))
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // --- GOOGLE SIGN IN ---
            val context = LocalContext.current
            val gso = remember {
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(R.string.default_web_client_id)) // Firebase'den otomatik gelir
                    .requestEmail()
                    .build()
            }
            val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
            
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    account?.idToken?.let { token ->
                        viewModel.loginWithGoogle(token)
                    } ?: run {
                        viewModel.setError("Google token alınamadı.")
                    }
                } catch (e: Exception) {
                    viewModel.setError("Google girişi iptal edildi veya bir hata oluştu: ${e.message}")
                }
            }

            OutlinedButton(
                onClick = { 
                    googleSignInClient.signOut().addOnCompleteListener {
                        launcher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://www.gstatic.com/images/branding/product/2x/googleg_48dp.png",
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.login_with_google),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}
