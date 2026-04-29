package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.ui.dashboard.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    viewModel: com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel,
    patientUid: String,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.observePatientStats(patientUid).collectAsState(initial = com.example.exerciseformanalyzer.model.WorkoutStats())
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())
    val patientName = patients.find { it.uid == patientUid }?.fullName ?: "Hasta Detayı"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(patientName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
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
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Kalori", "Performans", "Görev Durumu")

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (selectedTab) {
                0 -> {
                    Text("Haftalık Kalori Yakımı", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CalorieBarChart(data = stats.dailyCalories)
                }
                1 -> {
                    Text("Form Puanı Trendi", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    FormScoreLineChart(data = stats.scoreTrend)
                }
                2 -> {
                    Text("Görev Tamamlama Oranı", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TaskPieChart(stats = stats.completionStats)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Summary Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Hızlı Özet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalKcal = stats.dailyCalories.sumOf { it.second.toDouble() }.toInt()
                    val avgScore = if (stats.scoreTrend.isNotEmpty()) stats.scoreTrend.map { it.second }.average().toInt() else 0
                    
                    Text("• Haftalık Toplam: $totalKcal kcal")
                    Text("• Ortalam Form Puanı: $avgScore")
                    Text("• Tamamlanan Görev: ${stats.completionStats["COMPLETED"] ?: 0}")
                }
            }
        }
    }
}
