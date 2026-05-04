package com.example.exerciseformanalyzer.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import com.example.exerciseformanalyzer.ui.dashboard.components.CalorieBarChart
import com.example.exerciseformanalyzer.ui.dashboard.components.FormScoreLineChart
import com.example.exerciseformanalyzer.ui.dashboard.components.TaskPieChart
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TaskExerciseStartParams(
    val exerciseType: ExerciseType,
    val taskId: Int,
    val firebaseTaskId: String = "",    // Firestore DocId
    val exerciseIndex: Int,
    val targetType: String,
    val targetReps: Int,
    val targetDurationSeconds: Int,
    val targetSets: Int,
    val completedSets: Int,
    val restTimeSeconds: Int,
    val scheduleType: String = "DAILY"
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
    onLogout: () -> Unit,
    onNavigateToChat: (expertUid: String, expertName: String) -> Unit
) {
    val currentUser by viewModel.observeCurrentUser().collectAsState(initial = null)
    val categorizedTasks by viewModel.categorizedTasks.collectAsState()
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val isEmailVerified = viewModel.isEmailVerified
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val hasCommunityNotifications by viewModel.hasCommunityNotifications.collectAsState()
    val expertNotes by viewModel.expertNotes.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedMainTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Genel Bakış", "İstatistikler")

    LaunchedEffect(currentUser?.expertUid) {
        viewModel.syncPatientData(currentUser?.expertUid)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCommunityNotifications()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                    val expertUid = currentUser?.expertUid
                    if (!expertUid.isNullOrEmpty()) {
                        IconButton(onClick = { onNavigateToChat(expertUid, "Uzmanınız") }) {
                            Icon(imageVector = Icons.Default.Chat, contentDescription = "Uzmanya Mesaj Gönder")
                        }
                    }
                    IconButton(onClick = onNavigateToLeaderboard) { 
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "Sıralama") 
                    }
                    IconButton(onClick = onNavigateToSocial) { 
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Sosyal Feed") 
                    }
                    TextButton(onClick = onNavigateToGroups) {
                        BadgedBox(
                            badge = {
                                if (hasCommunityNotifications) {
                                    Badge()
                                }
                            }
                        ) {
                            Text(
                                stringResource(R.string.groups_title),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
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
                                            Button(onClick = { viewModel.respondToRequest(req, "ACCEPTED") }) {
                                                Text("Kabul Et")
                                            }
                                            OutlinedButton(onClick = { viewModel.respondToRequest(req, "REJECTED") }) {
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

                        if (expertNotes.isNotEmpty()) {
                            Text("Uzman Notları", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            expertNotes.forEach { note ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        val noteDate = note.createdAt?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it) } ?: ""
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Icon(Icons.Default.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Text(noteDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(note.note, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    item {
                        // BUGÜNKÜ GÖREVLER
                        Text("Bugünkü Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.today.isEmpty()) {
                            Text("Bugün için planlanmış görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.today.forEach { task ->
                                PatientTaskCard(
                                    task = task,
                                    viewModel = viewModel,
                                    onNavigateToExercise = onNavigateToTaskExercise,
                                    onRemoveGroupTask = { groupTask ->
                                        viewModel.removeGroupProgramTask(groupTask) { _, message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // DEVAM EDEN GÖREVLER
                        Text("Devam Eden Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.inProgress.isEmpty()) {
                            Text("Devam eden görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.inProgress.forEach { task ->
                                PatientTaskCard(
                                    task = task,
                                    viewModel = viewModel,
                                    onNavigateToExercise = onNavigateToTaskExercise,
                                    onRemoveGroupTask = { groupTask ->
                                        viewModel.removeGroupProgramTask(groupTask) { _, message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // BUGÜN AKTİF OLMAYANLAR
                        Text("Bugün Aktif Olmayanlar", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.inactiveToday.isNotEmpty()) {
                            categorizedTasks.inactiveToday.forEach { task ->
                                    PatientTaskCard(
                                        task = task,
                                        viewModel = viewModel,
                                        onNavigateToExercise = onNavigateToTaskExercise,
                                        onRemoveGroupTask = { groupTask ->
                                            viewModel.removeGroupProgramTask(groupTask) { _, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                            }
                        } else {
                            Text("Yok", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // TAMAMLANANLAR
                        Text("Tamamlanan Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.completed.isEmpty()) {
                            Text("Tamamlanan görev yok", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.completed.forEach { task ->
                                    PatientTaskCard(
                                        task = task,
                                        viewModel = viewModel,
                                        onNavigateToExercise = onNavigateToTaskExercise,
                                        onRemoveGroupTask = { groupTask ->
                                            viewModel.removeGroupProgramTask(groupTask) { _, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
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
private fun RecommendationCard(
    plan: com.example.exerciseformanalyzer.domain.RecommendedPlan,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
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
            
            Text(
                text = "Sağlık durumuna ve hedeflerine göre optimize edilmiş program.",
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
                    Text("${ex.sets} Set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun PatientTaskCard(
    task: com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity,
    viewModel: PatientViewModel,
    onNavigateToExercise: (TaskExerciseStartParams) -> Unit,
    onRemoveGroupTask: (TaskAssignmentEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var showRemoveGroupTaskDialog by remember { mutableStateOf(false) }
    val progress by viewModel.observeTaskProgress(task.firebaseDocId ?: "", task.scheduleType).collectAsState(initial = null)
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    val exerciseListForCard = remember(task.exercisesJson) {
        try {
            val arr = org.json.JSONArray(task.exercisesJson)
            List(arr.length()) { i -> arr.getJSONObject(i) }
        } catch (e: Exception) { emptyList<org.json.JSONObject>() }
    }

    val progressMapForCard = remember(progress?.progressJson) {
        try {
            val arr = org.json.JSONArray(progress?.progressJson ?: "[]")
            val map = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                map[obj.getString("exerciseType")] = obj
            }
            map
        } catch (e: Exception) { emptyMap<String, org.json.JSONObject>() }
    }

    val totalSets = exerciseListForCard.sumOf { it.optInt("sets", 0) }
    val completedSets = progressMapForCard.values.sumOf { it.optInt("completedSets", 0) }
    val percent = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f
    val isGroupTask = task.expertUid.startsWith("GROUP:")
    val groupName = Regex("^\\[(.+)]").find(task.title)?.groupValues?.getOrNull(1) ?: "Grup"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Başlık satırı ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isGroupTask) "Grup: $groupName" else "Uzman: ${if (task.expertUid == "SYSTEM") "Sistem" else "Danışman"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                PatientTaskStatusBadge(progress?.status ?: "pending")
            }

            Spacer(modifier = Modifier.height(8.dp))
            PatientFrequencyInfoRow(task)

            if (isGroupTask) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRemoveGroupTaskDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Programı Sil")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                val dateText = if (task.createdAt > 0L) sdf.format(Date(task.createdAt)) else "Belirtilmedi"
                Text("Veriliş: $dateText", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            // ── İlerleme çubuğu ──
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("İlerleme: $completedSets / $totalSets Set", style = MaterialTheme.typography.labelSmall)
                Text("%${(percent * 100).toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            PatientTaskProgressBar(percent)

            // ── Egzersiz listesi genişletme düğmesi ──
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    if (expanded) "Egzersizleri Gizle" else "Egzersizleri Göster (${exerciseListForCard.size})",
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            // ── Egzersiz satırları ──
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!viewModel.isTaskActiveToday(task)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bu görev bugün aktif değil.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    for (itemIdx in exerciseListForCard.indices) {
                        val exerciseItem: org.json.JSONObject = exerciseListForCard[itemIdx]
                        val exerciseTypeKey: String = exerciseItem.getString("exerciseType")
                        val progressItem: org.json.JSONObject? = progressMapForCard[exerciseTypeKey]
                        val isExCompleted = progressItem?.optString("status") == "completed"
                        val exCompletedSets = progressItem?.optInt("completedSets", 0) ?: 0

                        ExerciseStartRow(
                            exerciseJson = exerciseItem,
                            progressJson = progressItem,
                            isCompleted = isExCompleted,
                            completedSets = exCompletedSets,
                            canStart = viewModel.isTaskActiveToday(task) &&
                                    task.status != "inactive" && task.status != "removed" &&
                                    !isExCompleted,
                            onStart = {
                                val exerciseEnum = ExerciseType.values()
                                    .find { it.name.equals(exerciseTypeKey, ignoreCase = true) }
                                    ?: return@ExerciseStartRow

                                viewModel.canStartExercise(
                                    task = task,
                                    exerciseType = exerciseTypeKey,
                                    progressJson = progress?.progressJson ?: "[]"
                                ) { canDo, _ ->
                                    if (canDo) {
                                        onNavigateToExercise(
                                            TaskExerciseStartParams(
                                                exerciseType = exerciseEnum,
                                                taskId = task.id,
                                                firebaseTaskId = task.firebaseDocId ?: "",
                                                exerciseIndex = itemIdx,
                                                targetType = exerciseItem.optString("targetType", "REPS"),
                                                targetReps = exerciseItem.optInt("targetReps", 0),
                                                targetDurationSeconds = exerciseItem.optInt("targetDurationSeconds", 0),
                                                targetSets = exerciseItem.optInt("sets", 1),
                                                completedSets = exCompletedSets,
                                                restTimeSeconds = exerciseItem.optInt("restTimeSeconds", 60),
                                                scheduleType = task.scheduleType
                                            )
                                        )
                                    }
                                    // canDo=false durumunda canStartExercise içinde mesaj var
                                    // Snackbar isteği için ileride SnackbarHostState buraya geçirilebilir
                                }
                            }
                        )

                        if (itemIdx < exerciseListForCard.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    if (showRemoveGroupTaskDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveGroupTaskDialog = false },
            title = { Text("Programı Sil") },
            text = { Text("Bu grup programını ana sayfanızdan silmek istediğinize emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveGroupTask(task)
                        showRemoveGroupTaskDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveGroupTaskDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

/** Tek egzersiz satırı — kendi Başla butonu ve tamamlanma göstergesi ile */
@Composable
private fun ExerciseStartRow(
    exerciseJson: org.json.JSONObject,
    progressJson: org.json.JSONObject?,
    isCompleted: Boolean,
    completedSets: Int,
    canStart: Boolean,
    onStart: () -> Unit
) {
    val name = exerciseJson.optString("exerciseType", "Egzersiz")
    val totalSets = exerciseJson.optInt("sets", 1)
    val targetReps = exerciseJson.optInt("targetReps", 0)
    val targetDur = exerciseJson.optInt("targetDurationSeconds", 0)
    val restTime = exerciseJson.optInt("restTimeSeconds", 60)
    val targetStr = if (targetReps > 0) "$targetReps Tekrar" else "$targetDur Sn"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tamamlama ikonu veya yer tutucu
        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Tamamlandı",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Egzersiz bilgileri
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(targetStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("•", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(
                    "$completedSets / $totalSets Set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (restTime > 0) {
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("Din: ${restTime}sn", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        // Başla / Tamamlandı butonu
        if (isCompleted) {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "✓ Tamamlandı",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Başla", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PatientFrequencyInfoRow(task: com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity) {
    val text = when (task.scheduleType) {
        "DAILY" -> "Her Gün"
        "WEEKLY" -> "Haftalık"
        "CUSTOM" -> {
            try {
                val daysArr = org.json.JSONArray(task.daysOfWeekJson)
                val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                val selectedList = mutableListOf<String>()
                for (i in 0 until daysArr.length()) {
                    val dayIdx = daysArr.getInt(i)
                    dayNames.getOrNull(dayIdx - 1)?.let { selectedList.add(it) }
                }
                "Özel Günler (${selectedList.joinToString(", ")})"
            } catch (e: Exception) { "Özel Günler" }
        }
        else -> task.scheduleType
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Plan: $text", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun PatientTaskStatusBadge(status: String) {
    val (label, color) = when (status.lowercase()) {
        "completed" -> "Tamamlandı" to Color(0xFF4CAF50)
        "in_progress" -> "Devam Ediyor" to Color(0xFF2196F3)
        else -> "Bekliyor" to Color(0xFFFBC02D)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PatientTaskProgressBar(progress: Float) {
    LinearProgressIndicator(
        progress = progress,
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    )
}

@Composable
private fun PatientExerciseProgressRow(taskEx: org.json.JSONObject, progEx: org.json.JSONObject?) {
    val name = taskEx.optString("exerciseType", "")
    val totalSets = taskEx.optInt("sets", 0)
    val compSets = progEx?.optInt("completedSets", 0) ?: 0
    val targetReps = taskEx.optInt("targetReps", 0)
    val targetDur = taskEx.optInt("targetDurationSeconds", 0)
    
    val targetStr = if (targetReps > 0) "$targetReps Tekrar" else "$targetDur Sn"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(targetStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text("$compSets / $totalSets Set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
