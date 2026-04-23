package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.json.JSONArray
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import com.example.exerciseformanalyzer.ui.dashboard.components.*

data class TaskExerciseStartParams(
    val exerciseType: ExerciseType,
    val taskId: Int,
    val exerciseIndex: Int,
    val targetType: String,
    val targetReps: Int,
    val targetDurationSeconds: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCamera: (ExerciseType?) -> Unit,
    onNavigateToTaskExercise: (TaskExerciseStartParams) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit = {},
    onNavigateToSocial: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.observeCurrentUser().collectAsState(initial = null)
    val tasks by viewModel.observeMyTasks().collectAsState(initial = emptyList())
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val isEmailVerified = viewModel.isEmailVerified
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()

    var selectedMainTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Genel Bakış", "İstatistikler")

    LaunchedEffect(currentUser?.expertUid) {
        viewModel.syncPatientData(currentUser?.expertUid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.patient_dashboard_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToLeaderboard) { 
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "Sıralama") 
                    }
                    IconButton(onClick = onNavigateToSocial) { 
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Sosyal Feed") 
                    }
                    TextButton(onClick = onNavigateToGroups) {
                        Text(
                            stringResource(R.string.groups_title),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                    TextButton(onClick = { viewModel.setShowLogoutDialog(true) }) { 
                        Text(stringResource(R.string.logout)) 
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCamera(null) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.start_exercise))
            }
        }
    ) { paddingVals ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingVals)) {
            TabRow(selectedTabIndex = selectedMainTab) {
                mainTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedMainTab == index,
                        onClick = { selectedMainTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedMainTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        if (!isEmailVerified) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    text = "Lütfen e-posta adresinizi doğrulayın. Bazı özellikler kısıtlanmış olabilir.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        if (incomingRequests.isNotEmpty()) {
                            Text("Bağlantı İstekleri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            incomingRequests.forEach { (id, req) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("${req.fromExpertName} size bağlantı isteği gönderdi.", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.respondToRequest(id, "ACCEPTED", req.fromExpertId) }) {
                                                Text("Kabul Et")
                                            }
                                            OutlinedButton(onClick = { viewModel.respondToRequest(id, "REJECTED", req.fromExpertId) }) {
                                                Text("Reddet")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!currentUser?.expertUid.isNullOrEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Text(
                                    text = "Bağlı Uzmanınız Tarafından Takip Ediliyorsunuz",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val pendingTasks = tasks.filter { it.status == "PENDING" || it.status == "ASSIGNED" }
                        val inProgressTasks = tasks.filter { it.status == "IN_PROGRESS" }
                        val completedTasks = tasks.filter { it.status == "COMPLETED" || it.status == "DONE" || it.status == "MISSED" }

                        Text("Bekleyen Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pendingTasks.isEmpty()) {
                            Text("Bekleyen görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            pendingTasks.forEach { task ->
                                TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Devam Eden Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (inProgressTasks.isEmpty()) {
                            Text("Devam eden görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            inProgressTasks.forEach { task ->
                                TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Tamamlanan Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (completedTasks.isEmpty()) {
                            Text("Tamamlanan görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            completedTasks.forEach { task ->
                                TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(stringResource(R.string.my_reports), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (reports.isEmpty()) {
                            Text(stringResource(R.string.no_reports))
                        }
                    }

                    items(reports) { report ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "${stringResource(R.string.score_label)}: ${report.score} / ${report.reps} ${stringResource(R.string.reps_unit)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${report.caloriesBurned.toInt()} ${stringResource(R.string.calorie_unit)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!report.feedback.isNullOrEmpty()) {
                                    val feedbackText = if (report.feedback == "Mükemmel Form") {
                                        stringResource(R.string.perfect_form)
                                    } else {
                                        report.feedback
                                    }
                                    Text(text = feedbackText, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            } else {
                // STATS TAB
                val stats by viewModel.observePatientStats(viewModel.currentUid).collectAsState(initial = com.example.exerciseformanalyzer.model.WorkoutStats())
                
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    var selectedStatsTab by remember { mutableIntStateOf(0) }
                    val statsTabs = listOf("Kalori", "Performans", "Görevler")
                    
                    ScrollableTabRow(
                        selectedTabIndex = selectedStatsTab,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        statsTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedStatsTab == index,
                                onClick = { selectedStatsTab = index },
                                text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    when(selectedStatsTab) {
                        0 -> {
                            Text("Son 7 Günlük Kalori Yakımı", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            CalorieBarChart(data = stats.dailyCalories)
                        }
                        1 -> {
                            Text("Form Puanı Trendi (Son Egzersizler)", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            FormScoreLineChart(data = stats.scoreTrend)
                        }
                        2 -> {
                            Text("Genel Görev Tamamlama Durumu", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            TaskPieChart(stats = stats.completionStats)
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