package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import com.example.exerciseformanalyzer.ui.dashboard.components.*

data class TaskExerciseStartParams(
    val exerciseType: ExerciseType,
    val taskId: Int,
    val exerciseIndex: Int,
    val targetType: String,
    val targetReps: Int,
    val targetDurationSeconds: Int,
    val targetSets: Int,
    val completedSets: Int,
    val restTimeSeconds: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: PatientViewModel,
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
    val categorizedTasks by viewModel.observeCategorizedTasks().collectAsState(initial = CategorizedTasks())
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val isEmailVerified = viewModel.isEmailVerified
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                            incomingRequests.forEach { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("${req.doctorName} size bağlantı isteği gönderdi.", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.respondToRequest(req.requestId, "accepted", req.doctorId) }) {
                                                Text("Kabul Et")
                                            }
                                            OutlinedButton(onClick = { viewModel.respondToRequest(req.requestId, "rejected", req.doctorId) }) {
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

                        val currentUserData = currentUser
                        if (currentUserData != null) {
                            val recommendedPlan = remember(currentUserData) {
                                com.example.exerciseformanalyzer.domain.RecommendationHelper.generatePlan(currentUserData)
                            }
                            
                            RecommendationCard(
                                plan = recommendedPlan,
                                onApply = { viewModel.applyRecommendedPlan(recommendedPlan) }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    item {
                        Text("Bekleyen Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.pending.isEmpty()) {
                            Text("Bekleyen görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.pending.forEach { task ->
                                val isExpertLinked = task.expertUid == "SYSTEM" || task.expertUid == (currentUser?.expertUid ?: "")
                                TaskCard(
                                    task = task,
                                    isExpertLinked = isExpertLinked,
                                    onNavigateToTaskExercise = { params ->
                                        scope.launch {
                                            if (task.status == "inactive" || task.status == "removed") {
                                                Toast.makeText(context, "Bu görev artık aktif değil.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            if (task.expertUid != "SYSTEM") {
                                                val isActive = viewModel.checkDoctorPatientRelation(task.expertUid, currentUser?.uid ?: "")
                                                if (!isActive) {
                                                    Toast.makeText(context, "Doktorunuzla olan bağlantınız kesildiği için bu göreve başlayamazsınız.", Toast.LENGTH_LONG).show()
                                                    return@launch
                                                }
                                            }
                                            onNavigateToTaskExercise(params)
                                        }
                                    },
                                    onNavigateToCamera = onNavigateToCamera
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Devam Eden Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.ongoing.isEmpty()) {
                            Text("Devam eden görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.ongoing.forEach { task ->
                                val isExpertLinked = task.expertUid == "SYSTEM" || task.expertUid == (currentUser?.expertUid ?: "")
                                TaskCard(
                                    task = task,
                                    isExpertLinked = isExpertLinked,
                                    onNavigateToTaskExercise = { params ->
                                        scope.launch {
                                            if (task.status == "inactive" || task.status == "removed") {
                                                Toast.makeText(context, "Bu görev artık aktif değil.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            
                                            if (task.expertUid != "SYSTEM") {
                                                val isActive = viewModel.checkDoctorPatientRelation(task.expertUid, currentUser?.uid ?: "")
                                                if (!isActive) {
                                                    Toast.makeText(context, "Doktorunuzla olan bağlantınız kesildiği için bu göreve başlayamazsınız.", Toast.LENGTH_LONG).show()
                                                    // Yerel DB'yi de güncelle ki UI yenilensin
                                                    return@launch
                                                }
                                            }
                                            onNavigateToTaskExercise(params)
                                        }
                                    },
                                    onNavigateToCamera = onNavigateToCamera
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Tamamlanan Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.completed.isEmpty()) {
                            Text("Tamamlanan görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.completed.forEach { task ->
                                val isExpertLinked = task.expertUid == "SYSTEM" || task.expertUid == (currentUser?.expertUid ?: "")
                                TaskCard(
                                    task = task,
                                    isExpertLinked = isExpertLinked,
                                    onNavigateToTaskExercise = { /* Tamamlanan görev için genellikle başla aktif olmaz ama Card imzasını korumalıyız */ },
                                    onNavigateToCamera = onNavigateToCamera
                                )
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
                val stats by viewModel.observePatientStats().collectAsState(initial = com.example.exerciseformanalyzer.model.WorkoutStats())
                
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
                            Text("Performans Özetiniz", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val totalCompletedTasks = categorizedTasks.completed.size
                            val todayReports = reports.filter { 
                                val date = java.util.Date(it.timestamp)
                                val today = java.util.Date()
                                date.year == today.year && date.month == today.month && date.date == today.date
                            }
                            val todayCalories = todayReports.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Bugün", style = MaterialTheme.typography.labelSmall)
                                        Text("${todayReports.size} Egzersiz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Card(modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Yakılan", style = MaterialTheme.typography.labelSmall)
                                        Text("${todayCalories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tamamlanan Toplam Görev", style = MaterialTheme.typography.labelSmall)
                                        Text("$totalCompletedTasks", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
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

@Composable
fun RecommendationCard(
    plan: com.example.exerciseformanalyzer.domain.RecommendedPlan,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Senin İçin Önerilen Program",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (plan.hasInjuryWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "⚠️ Program sağlık durumunuza göre optimize edildi.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Text(
                text = plan.note,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            plan.exercises.forEach { ex ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(ex.exerciseType, style = MaterialTheme.typography.bodyMedium)
                    val detail = if (ex.targetType == "REPS") "${ex.sets} Set x ${ex.targetReps} Tekrar" 
                                 else "${ex.sets} Set x ${ex.targetDurationSeconds} Sn"
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Bu Programı Uygula")
            }
        }
    }
}