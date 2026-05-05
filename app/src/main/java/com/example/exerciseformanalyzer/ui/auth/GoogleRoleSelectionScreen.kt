package com.example.exerciseformanalyzer.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GoogleRoleSelectionScreen(
    viewModel: AuthViewModel,
    onCompleted: (role: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRole by remember { mutableStateOf("PATIENT") }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.Success) {
            onCompleted(state.role)
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Google hesabiniz icin rol secin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bu secim profilinizi olusturmak icin gereklidir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                RoleOption(
                    label = "Hasta",
                    selected = selectedRole == "PATIENT",
                    onClick = { selectedRole = "PATIENT" }
                )
                RoleOption(
                    label = "Uzman / Doktor",
                    selected = selectedRole == "EXPERT",
                    onClick = { selectedRole = "EXPERT" }
                )

                if (uiState is AuthUiState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.completeGoogleRegistration(selectedRole) },
                    enabled = uiState !is AuthUiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Devam Et")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
