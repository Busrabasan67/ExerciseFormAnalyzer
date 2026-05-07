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
import com.example.exerciseformanalyzer.ui.dashboard.PatientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: PatientViewModel,
    onNavigateBack: () -> Unit
) {
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())

    // Raporları grupla: taskId (varsa) ve Tarih (gün) bazlı. 
    // taskId null ise bireysel (serbest) egzersiz olarak kalsın.
    val groupedReports = reports.groupBy { report ->
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(report.timestamp))
        if (report.taskId != null) {
            "task_${report.taskId}_$dateStr"
        } else {
            "free_${report.id}_$dateStr"
        }
    }

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

            groupedReports.forEach { (key, group) ->
                val first = group.first()
                val isTask = key.startsWith("task_")
                val dateStr = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("tr")).format(java.util.Date(first.timestamp))

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTask) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                           else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isTask) (first.taskTitle ?: "Görev Raporu") else "Serbest Egzersiz",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            group.forEachIndexed { index, report ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = "${index + 1}. ${report.exerciseName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Skor: %${report.score}", style = MaterialTheme.typography.bodySmall)
                                            Text(text = "Tekrar: ${report.reps}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Süre: ${report.totalTimeSeconds} sn", style = MaterialTheme.typography.bodySmall)
                                            Text(text = "Kalori: ${report.caloriesBurned.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (!report.feedback.isNullOrEmpty()) {
                                        Text(
                                            text = "Geri Bildirim: ${report.feedback}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    if (index < group.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
                                    }
                                }
                            }

                            if (isTask && group.size > 1) {
                                val totalCals = group.sumOf { it.caloriesBurned.toDouble() }.toInt()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Toplam Yakılan: $totalCals kcal",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
