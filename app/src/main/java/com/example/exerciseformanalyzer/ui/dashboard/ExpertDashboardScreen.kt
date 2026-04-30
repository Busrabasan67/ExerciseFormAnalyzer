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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.components.AssignTaskDialog
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import org.json.JSONArray
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDashboardScreen(
    viewModel: ExpertViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToPatientDetail: (String) -> Unit,
    onNavigateToSocial: () -> Unit,
    onLogout: () -> Unit
) {
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())
    val tasks by viewModel.observeTasksByExpert().collectAsState(initial = emptyList())
    
    val searchResult by viewModel.searchResult.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val requestStatus by viewModel.requestStatus.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Hastalarım", "Görev Ata", "Takip")
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Form state for Task Assignment
    var assignmentPatientId by remember { mutableStateOf<String?>(null) }
    var assignmentTitle by remember { mutableStateOf("") }
    var assignmentNote by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        viewModel.syncExpertData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expert_dashboard_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSocial) { 
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Sosyal Feed") 
                    }
                    TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                    TextButton(onClick = { viewModel.setShowLogoutDialog(true) }) { 
                        Text(stringResource(R.string.logout)) 
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingVals)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // PATIENTS LIST
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                                            Text(user.email, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(onClick = { viewModel.sendRequest(user.email) }) {
                                            Text("İstek Gönder")
                                        }
                                    }
                                }
                            }
                            if (requestStatus == "SUCCESS") {
                                Text("İstek gönderildi.", color = MaterialTheme.colorScheme.primary)
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(3000)
                                    viewModel.clearRequestStatus()
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.patients_label), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(patients) { patient ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(patient.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(patient.email, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { 
                                            assignmentPatientId = patient.uid
                                            selectedTab = 1 
                                        }, modifier = Modifier.weight(1f)) {
                                            Text("Görev Ata")
                                        }
                                        OutlinedButton(onClick = { onNavigateToPatientDetail(patient.uid) }, modifier = Modifier.weight(1f)) {
                                            Text("Detay")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ASSIGN TASK FORM (Integrated logic)
                    // For simplicity, we'll keep the AssignTaskDialog but maybe the user wants it inline?
                    // "baştan yap" might mean a full screen form. 
                    // Let's use the Dialog for now but triggered from a better place, 
                    // OR move its contents here. Moving contents is better.
                    
                    if (patients.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("Önce bir hasta eklemelisiniz.")
                        }
                    } else {
                        // Inline Task Assignment Form
                        var selectedPatient by remember { mutableStateOf(patients.find { it.uid == assignmentPatientId } ?: patients[0]) }
                        
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Text("Yeni Görev Oluştur", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text("Hasta Seçin:", style = MaterialTheme.typography.labelLarge)
                                // Simplified Patient Selector
                                patients.forEach { p ->
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedPatient = p }
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(selected = selectedPatient.uid == p.uid, onClick = { selectedPatient = p })
                                        Text(p.fullName)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = { 
                                        // Reuse existing Dialog logic but as a screen flow
                                        assignmentPatientId = selectedPatient.uid
                                        // Trigger the actual dialog for complex exercise selection or implement inline
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Egzersizleri Belirle ve Ata")
                                }
                                
                                // Actually, let's keep the Dialog for the COMPLEX exercise selection 
                                // but the ENTRY point is now more visible.
                                if (assignmentPatientId != null && selectedTab == 1) {
                                    AssignTaskDialog(
                                        onDismissRequest = { assignmentPatientId = null },
                                        onAssignTask = { title, note, dueDate, exercises, sched, days, auto, weeks ->
                                            viewModel.assignTask(selectedPatient.uid, title, note, dueDate, exercises, sched, days, auto, weeks)
                                            assignmentPatientId = null
                                            selectedTab = 2 // Move to follow-up after success
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // FOLLOW-UP
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            Text("Verilen Görevlerin Durumu", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            if (tasks.isEmpty()) {
                                Text("Henüz bir görev atamadınız.")
                            }
                        }
                        items(tasks) { task ->
                            val pName = patients.find { it.uid == task.patientUid }?.fullName ?: "Bilinmeyen"
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Hasta: $pName", color = MaterialTheme.colorScheme.primary)
                                    Text("Başlık: ${task.title}")
                                    Text("Durum: ${task.status}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLogoutDialog) {
            LogoutConfirmationDialog(
                onConfirm = {
                    viewModel.setShowLogoutDialog(false)
                    onLogout()
                },
                onDismiss = { viewModel.setShowLogoutDialog(false) }
            )
        }
    }
}
