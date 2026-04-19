package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panelim (Uzman)") },
                actions = {
                    TextButton(onClick = onNavigateToProfile) { Text("Profil") }
                    TextButton(onClick = onLogout) { Text("Çıkış") }
                }
            )
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            item {
                Text("Hastalarım / Danışanlarım", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (patients.isEmpty()) {
                    Text("Henüz size bağlı bir hasta yok.")
                }
            }

            items(patients) { patient ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = patient.fullName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Email: ${patient.email}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Hastalık Öyküsü: ${patient.diseasesJson ?: "Yok"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
