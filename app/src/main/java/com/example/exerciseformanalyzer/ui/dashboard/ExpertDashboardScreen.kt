package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.components.AssignTaskDialog
import org.json.JSONArray
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())
    val tasks by viewModel.observeTasksByExpert().collectAsState(initial = emptyList())
    
    val searchResult by viewModel.searchResult.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedPatientForTask by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.syncExpertData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expert_dashboard_title)) },
                actions = {
                    TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                    TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Hasta E-posta Ara") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.searchPatient(searchQuery) }) {
                            Icon(Icons.Default.Search, contentDescription = "Ara")
                        }
                    }
                )
                if (!searchError.isNullOrEmpty()) {
                    Text(text = searchError ?: "", color = MaterialTheme.colorScheme.error)
                }
                searchResult?.let { user ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(user.email, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { viewModel.linkPatient(user.uid) }) {
                                Text("Ekle")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(R.string.patients_label), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (patients.isEmpty()) {
                    Text(stringResource(R.string.no_patients))
                }
            }

            items(patients) { patient ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = patient.fullName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "${stringResource(R.string.email_label)}: ${patient.email}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${stringResource(R.string.medical_history_label)}: ${patient.diseasesJson ?: stringResource(R.string.none_label)}", 
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { selectedPatientForTask = patient.uid }) {
                            Text("Görev Ata")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Verilen Görev Takibi", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text("Henüz hiçbir görev atamadınız.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            items(tasks) { task ->
                val targetPatient = patients.find { it.uid == task.patientUid }
                val patientName = targetPatient?.fullName ?: "Bilinmeyen Hasta"

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Hasta: $patientName", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Görev: ${if (task.title.isNotEmpty()) task.title else "Haftalık Antrenman"}", style = MaterialTheme.typography.bodyMedium)
                        
                        val parsedExercises = remember(task.exercisesJson) {
                            val list = mutableListOf<Map<String, String>>()
                            try {
                                val arr = JSONArray(task.exercisesJson)
                                for (i in 0 until arr.length()) {
                                    val ex = arr.getJSONObject(i)
                                    val name = ex.optString("exerciseType", "")
                                    val status = ex.optString("status", "PENDING")
                                    val progressStr = if (ex.optString("targetType") == "DURATION") {
                                        "${ex.optInt("actualDurationSeconds", 0)} / ${ex.optInt("targetDurationSeconds")} Sn"
                                    } else {
                                        "${ex.optInt("actualReps", 0)} / ${ex.optInt("targetReps")} Tekrar"
                                    }
                                    list.add(mapOf("name" to name, "progress" to progressStr, "status" to status))
                                }
                            } catch(e: Exception) {}
                            list
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        parsedExercises.forEach { exData ->
                            val isCompleted = exData["status"] == "COMPLETED"
                            val icon = if (isCompleted) "✅" else "⏳"
                            Text(text = "$icon ${exData["name"]}: ${exData["progress"]}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val finalStatusText = when(task.status) {
                            "COMPLETED" -> "Tamamlandı"
                            "IN_PROGRESS" -> "Devam Ediyor"
                            "DONE" -> "Eski Format (Bitti)"
                            "MISSED" -> "Kaçırıldı"
                            else -> "Bekliyor"
                        }
                        Text(text = "Genel Durum: $finalStatusText", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        
        if (selectedPatientForTask != null) {
            AssignTaskDialog(
                onDismissRequest = { selectedPatientForTask = null },
                onAssignTask = { title, note, dueDate, exercises ->
                    viewModel.assignTask(selectedPatientForTask!!, title, note, dueDate, exercises)
                    selectedPatientForTask = null
                }
            )
        }
    }
}
