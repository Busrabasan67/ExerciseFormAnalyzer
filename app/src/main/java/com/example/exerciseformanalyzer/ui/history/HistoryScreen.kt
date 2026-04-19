package com.example.exerciseformanalyzer.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Egzersiz Geçmişi") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Geri") }
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
                if (reports.isEmpty()) {
                    Text("Henüz kaydedilmiş bir egzersiz raporu bulunmuyor.")
                }
            }

            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Rapor ID: ${report.id}", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Skor: ${report.score} / Tekrar: ${report.reps}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Süre: ${report.totalTimeSeconds} saniye", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Yakılan Kalori: ${report.caloriesBurned.toInt()} kcal", style = MaterialTheme.typography.bodyMedium)
                        
                        if (!report.feedback.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Not: ${report.feedback}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
