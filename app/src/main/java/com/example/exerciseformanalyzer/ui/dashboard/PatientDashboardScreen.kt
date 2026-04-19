package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val tasks by viewModel.observeMyTasks().collectAsState(initial = emptyList())
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panelim (Hasta)") },
                actions = {
                    TextButton(onClick = onNavigateToProfile) { Text("Profil") }
                    TextButton(onClick = onLogout) { Text("Çıkış") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCamera) {
                Icon(Icons.Default.Add, contentDescription = "Egzersiz Başlat")
            }
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            item {
                Text("Bekleyen Görevler", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text("Şu an bekleyen göreviniz yok.")
                }
            }

            items(tasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Egzersiz: ${task.exerciseName}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Hedef: ${task.targetReps} tekrar", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Son Raporlarım", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text("Henüz yapılmış bir egzersiz yok.")
                }
            }

            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Skor: ${report.score} / Tekrar: ${report.reps}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Yakılan Kalori: ${report.caloriesBurned.toInt()} kcal", style = MaterialTheme.typography.bodyMedium)
                        if (!report.feedback.isNullOrEmpty()) {
                            Text(text = "Not: ${report.feedback}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
