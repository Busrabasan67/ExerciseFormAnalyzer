package com.example.exerciseformanalyzer.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val user by viewModel.observeCurrentUser().collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profilim") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Geri") }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            user?.let { u ->
                Text("Ad Soyad: ${u.fullName}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email: ${u.email}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rol: ${u.role}")
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Yaş: ${u.age ?: "-"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Kilo: ${u.weightKg ?: "-"} kg")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Boy: ${u.heightCm ?: "-"} cm")
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hastalıklar: ${if (u.diseasesJson.isNullOrEmpty()) "Yok" else u.diseasesJson}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sigara: ${if (u.isSmoker) "Evet" else "Hayır"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Alkol: ${if (u.isDrinker) "Evet" else "Hayır"}")
            } ?: run {
                Text("Kullanıcı bilgileri yükleniyor veya bulunamadı...")
            }
        }
    }
}
