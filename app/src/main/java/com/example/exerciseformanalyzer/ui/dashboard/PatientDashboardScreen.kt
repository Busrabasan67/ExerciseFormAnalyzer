package com.example.exerciseformanalyzer.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.WorkoutReportEntity
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
    val taskTitle: String = "",
    val firebaseTaskId: String = "",    // Firestore DocId
    val exerciseIndex: Int,
    val targetType: String,
    val targetReps: Int,
    val targetDurationSeconds: Int,
    val targetSets: Int,
    val completedSets: Int,
    val restTimeSeconds: Int?,
    val scheduleType: String = "DAILY",
    val repsDoneInCurrentSet: Int = 0,
    val durDoneInCurrentSet: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: PatientViewModel,
    onNavigateToCamera: (ExerciseType?) -> Unit,
    onNavigateToTaskExercise: (TaskExerciseStartParams) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit = {},

    onNavigateToLeaderboard: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToChat: (expertUid: String, expertName: String) -> Unit
) {
    val currentUser by viewModel.observeCurrentUser().collectAsState(initial = null)
    val categorizedTasks by viewModel.categorizedTasks.collectAsState()
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val isEmailVerified by viewModel.isEmailVerified.collectAsStateWithLifecycle()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val hasCommunityNotifications by viewModel.hasCommunityNotifications.collectAsState()
    val expertNotes by viewModel.expertNotes.collectAsState()
    val pendingExpertSwitch by viewModel.pendingExpertSwitch.collectAsState()
    val expertProfile by viewModel.observeExpertProfile(currentUser?.expertUid ?: "").collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedMainTab by remember { mutableIntStateOf(0) }
    var showUnlinkExpertDialog by remember { mutableStateOf(false) }
    var taskToHideFromHistory by remember { mutableStateOf<TaskAssignmentEntity?>(null) }
    val yourExpertLabel = stringResource(R.string.ui_your_expert)
    val mainTabs = listOf(stringResource(R.string.ui_overview), stringResource(R.string.ui_statistics))

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
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer) 
            viewModel.setShowLogoutDialog(false)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {},
                    actions = {
                        val expertUid = currentUser?.expertUid
                        if (!expertUid.isNullOrEmpty()) {
                            IconButton(onClick = { onNavigateToChat(expertUid, expertProfile?.fullName ?: yourExpertLabel) }) {
                                Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = stringResource(R.string.ui_send_message), tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                            }
                        }
                        IconButton(onClick = onNavigateToLeaderboard) { 
                            Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = stringResource(R.string.ui_leaderboard), tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp)) 
                        }

                        TextButton(
                            onClick = onNavigateToGroups,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BadgedBox(
                                    badge = {
                                        if (hasCommunityNotifications) {
                                            Badge(containerColor = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color(0xFF2E7D32))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.groups_title), style = MaterialTheme.typography.labelLarge, color = Color(0xFF2E7D32))
                            }
                        }

                        // Profil / Baş Harfler Butonu
                        IconButton(onClick = onNavigateToProfile) {
                            val initials = (currentUser?.fullName ?: "").split(" ")
                                .filter { it.isNotEmpty() }
                                .take(2)
                                .map { it.first().uppercase() }
                                .joinToString("")

                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color(0xFFE8F5E9)
                            ) {
                                if (!currentUser?.profileImageUrl.isNullOrEmpty()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(currentUser?.profileImageUrl),
                                        contentDescription = stringResource(R.string.profile_title),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initials.ifEmpty { "?" },
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(onClick = { viewModel.setShowLogoutDialog(true) }) {
                            Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.logout), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                
                // --- EMAIL VERIFICATION BANNER ---
                var showSuccessBanner by remember { mutableStateOf(false) }
                LaunchedEffect(isEmailVerified) {
                    if (isEmailVerified) {
                        // Eğer az önce doğrulandıysa başarı mesajı göster
                        if (!isEmailVerified) { // Bu mantık StateFlow değiştiğinde tetiklenir
                           showSuccessBanner = true
                           kotlinx.coroutines.delay(4000)
                           showSuccessBanner = false
                        }
                    } else {
                        while(true) {
                            kotlinx.coroutines.delay(4000)
                            viewModel.reloadUser { verified ->
                                if (verified) showSuccessBanner = true
                            }
                            if (isEmailVerified) break
                        }
                    }
                }

                if (showSuccessBanner) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50), // Yeşil
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.ui_success_email_verified), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                if (!isEmailVerified && !showSuccessBanner) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.email_not_verified),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { 
                                    viewModel.sendVerificationEmail()
                                    android.widget.Toast.makeText(context, context.getString(R.string.verification_email_sent), android.widget.Toast.LENGTH_LONG).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.verify_now), style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(
                                onClick = { viewModel.reloadUser() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.check_status),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Profil tamamlama uyarısı (Kalori hesabı için kilo kontrolü)
                val isProfileIncomplete = currentUser != null && (currentUser?.weightKg == null || currentUser?.weightKg!! <= 0f)
                if (isProfileIncomplete && !showSuccessBanner) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_fill_weight_for_calories),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onNavigateToProfile,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.ui_go_to_profile_cap), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
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
                                    text = stringResource(R.string.ui_verify_email_warning),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        if (incomingRequests.isNotEmpty()) {
                            Text(stringResource(R.string.ui_connection_requests), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            for (req in incomingRequests) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("${req.doctorName} ${stringResource(R.string.ui_sent_connection_request)}", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.respondToRequest(req, "ACCEPTED") }) {
                                                Text(stringResource(R.string.ui_accept))
                                            }
                                            OutlinedButton(onClick = { viewModel.respondToRequest(req, "REJECTED") }) {
                                                Text(stringResource(R.string.ui_reject))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!currentUser?.expertUid.isNullOrEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (expertProfile != null) stringResource(R.string.ui_followed_by_expert, expertProfile?.fullName ?: "") else stringResource(R.string.ui_followed_by_your_expert),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { showUnlinkExpertDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.ui_unlink_relationship), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }

                        if (expertNotes.isNotEmpty()) {
                            Text(stringResource(R.string.ui_expert_notes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            for (note in expertNotes) {
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
                        Text(stringResource(R.string.ui_todays_tasks), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.today.isEmpty()) {
                            Text(stringResource(R.string.ui_no_tasks_today), style = MaterialTheme.typography.bodyMedium)
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
                        Text(stringResource(R.string.ui_ongoing_tasks), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.inProgress.isEmpty()) {
                            Text(stringResource(R.string.ui_no_ongoing_tasks), style = MaterialTheme.typography.bodyMedium)
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

                        // NOT ACTIVE TODAY
                        Text(stringResource(R.string.ui_inactive_today), style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.inactiveToday.isNotEmpty()) {
                            categorizedTasks.inactiveToday.forEach { task ->
                                    PatientTaskCard(
                                        task = task,
                                        viewModel = viewModel,
                                        onNavigateToExercise = onNavigateToTaskExercise,
                                        onHideFromHistory = { taskToHideFromHistory = it },
                                        onRemoveGroupTask = { groupTask ->
                                            viewModel.removeGroupProgramTask(groupTask) { _, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                            }
                        } else {
                            Text(stringResource(R.string.ui_none), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // TAMAMLANANLAR
                        Text(stringResource(R.string.ui_completed_tasks), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (categorizedTasks.completed.isEmpty()) {
                            Text(stringResource(R.string.ui_no_completed_tasks), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categorizedTasks.completed.forEach { task ->
                                    PatientTaskCard(
                                        task = task,
                                        viewModel = viewModel,
                                        onNavigateToExercise = onNavigateToTaskExercise,
                                        onHideFromHistory = { taskToHideFromHistory = it },
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
                        PatientReportCard(report = report)
                    }
                }
            } else {
                // STATS TAB
                val stats by viewModel.observePatientStats().collectAsState(initial = com.example.exerciseformanalyzer.model.WorkoutStats())
                
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    var selectedStatsTab by remember { mutableIntStateOf(0) }
                    val statsTabs = listOf(stringResource(R.string.ui_calories_tab), stringResource(R.string.ui_performance_tab), stringResource(R.string.ui_tasks))
                    
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
                            Text(stringResource(R.string.ui_last_7_days_calories), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            CalorieBarChart(data = stats.dailyCalories)
                        }
                        1 -> {
                            Text(stringResource(R.string.ui_performance_summary), style = MaterialTheme.typography.titleMedium)
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
                                        Text(stringResource(R.string.ui_today), style = MaterialTheme.typography.labelSmall)
                                        Text("${todayReports.size} ${stringResource(R.string.ui_exercises_count)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Card(modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(stringResource(R.string.ui_burned), style = MaterialTheme.typography.labelSmall)
                                        Text("${todayCalories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.ui_total_tasks_completed), style = MaterialTheme.typography.labelSmall)
                                        Text("$totalCompletedTasks", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(stringResource(R.string.ui_form_score_trend), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            FormScoreLineChart(data = stats.scoreTrend)
                        }
                        2 -> {
                            Text(stringResource(R.string.ui_task_completion_status), style = MaterialTheme.typography.titleSmall)
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

        pendingExpertSwitch?.let {
            AlertDialog(
                onDismissRequest = { viewModel.cancelExpertSwitch() },
                title = { Text(stringResource(R.string.ui_expert_change)) },
                text = { Text(stringResource(R.string.ui_expert_switch_warning)) },
                confirmButton = {
                    Button(onClick = { viewModel.confirmExpertSwitch() }) { Text(stringResource(R.string.ui_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelExpertSwitch() }) { Text(stringResource(R.string.ui_cancel)) }
                }
            )
        }

        if (showUnlinkExpertDialog) {
            AlertDialog(
                onDismissRequest = { showUnlinkExpertDialog = false },
                title = { Text(stringResource(R.string.ui_unlink_relationship)) },
                text = { Text(stringResource(R.string.ui_unlink_warning)) },
                confirmButton = {
                    Button(
                        onClick = {
                            val user = currentUser
                            viewModel.unlinkCurrentExpert(
                                patientName = user?.fullName.orEmpty(),
                                oldExpertId = user?.expertUid.orEmpty()
                            ) { _, message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                            showUnlinkExpertDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.ui_unlink_relationship)) }
                },
                dismissButton = {
                    TextButton(onClick = { showUnlinkExpertDialog = false }) { Text(stringResource(R.string.ui_cancel)) }
                }
            )
        }

        taskToHideFromHistory?.let { task ->
            AlertDialog(
                onDismissRequest = { taskToHideFromHistory = null },
                title = { Text(stringResource(R.string.ui_remove_from_history)) },
                text = { Text(stringResource(R.string.ui_remove_from_history_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.hideTaskFromHistory(task) { _, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        taskToHideFromHistory = null
                    }) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { taskToHideFromHistory = null }) { Text(stringResource(R.string.ui_cancel)) }
                }
            )
        }
    }
}

@Composable
private fun PatientTaskCard(
    task: com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity,
    viewModel: PatientViewModel,
    onNavigateToExercise: (TaskExerciseStartParams) -> Unit,
    onHideFromHistory: (TaskAssignmentEntity) -> Unit = {},
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
    val groupName = Regex("^\\[(.+)]").find(task.title)?.groupValues?.getOrNull(1) ?: stringResource(R.string.ui_group_label)

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
                        text = if (isGroupTask) "${stringResource(R.string.ui_group_label)}: $groupName" else "${stringResource(R.string.ui_expert_label)}: ${if (task.expertUid == "SYSTEM") stringResource(R.string.ui_system_label) else stringResource(R.string.ui_consultant_label)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val effectiveStatus = when {
                    percent >= 0.99f && totalSets > 0 -> "completed"
                    percent > 0f || (progress?.status ?: "").lowercase() == "in_progress" -> "in_progress"
                    else -> "pending"
                }
                PatientTaskStatusBadge(effectiveStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))
            PatientFrequencyInfoRow(task)

            if (task.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(R.string.ui_expert_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

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
                    Text(stringResource(R.string.ui_program_delete))
                }
            }

            if (task.status.equals("COMPLETED", ignoreCase = true) || task.status.equals("DONE", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onHideFromHistory(task) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_delete_from_history))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                val dateText = if (task.createdAt > 0L) sdf.format(Date(task.createdAt)) else stringResource(R.string.ui_not_specified)
                Text("${stringResource(R.string.ui_assigned_at)}: $dateText", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            // ── İlerleme çubuğu ──
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ui_progress_sets), style = MaterialTheme.typography.labelSmall)
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
                    if (expanded) stringResource(R.string.ui_hide_exercises) else stringResource(R.string.ui_show_exercises, exerciseListForCard.size),
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
                            Text(stringResource(R.string.ui_task_not_active_today), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    for (itemIdx in exerciseListForCard.indices) {
                        val exerciseItem: org.json.JSONObject = exerciseListForCard[itemIdx]
                        val exerciseTypeKey: String = exerciseItem.getString("exerciseType")
                        val progressItem: org.json.JSONObject? = progressMapForCard[exerciseTypeKey]
                        val isExCompleted = progressItem?.optString("status").equals("completed", ignoreCase = true)
                        val exCompletedSets = progressItem?.optInt("completedSets", 0) ?: 0

                        val actualReps = exerciseItem.optInt("actualReps", 0)
                        val actualDur = exerciseItem.optInt("actualDurationSeconds", 0)
                        val repsDoneInCurrentSet = maxOf(0, actualReps - (exCompletedSets * exerciseItem.optInt("targetReps", 0)))
                        val durDoneInCurrentSet = maxOf(0, actualDur - (exCompletedSets * exerciseItem.optInt("targetDurationSeconds", 0)))
                        val isPartiallyDone = (repsDoneInCurrentSet > 0 || durDoneInCurrentSet > 0) && !isExCompleted

                        ExerciseStartRow(
                            exerciseJson = exerciseItem,
                            progressJson = progressItem,
                            isCompleted = isExCompleted,
                            completedSets = exCompletedSets,
                            canStart = viewModel.isTaskActiveToday(task) &&
                                    task.status != "inactive" && task.status != "removed" &&
                                    !isExCompleted,
                            isPartiallyDone = isPartiallyDone,
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
                                                taskTitle = task.title,
                                                firebaseTaskId = task.firebaseDocId ?: "",
                                                exerciseIndex = itemIdx,
                                                targetType = exerciseItem.optString("targetType", "REPS"),
                                                targetReps = exerciseItem.optInt("targetReps", 0),
                                                targetDurationSeconds = exerciseItem.optInt("targetDurationSeconds", 0),
                                                targetSets = exerciseItem.optInt("sets", 1),
                                                completedSets = exCompletedSets,
                                                restTimeSeconds = if (exerciseItem.has("restTimeSeconds") && !exerciseItem.isNull("restTimeSeconds")) exerciseItem.optInt("restTimeSeconds") else null,
                                                scheduleType = task.scheduleType,
                                                repsDoneInCurrentSet = repsDoneInCurrentSet,
                                                durDoneInCurrentSet = durDoneInCurrentSet
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
            title = { Text(stringResource(R.string.ui_program_delete)) },
            text = { Text(stringResource(R.string.ui_delete_program_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveGroupTask(task)
                        showRemoveGroupTaskDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveGroupTaskDialog = false }) {
                    Text(stringResource(R.string.ui_cancel))
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
    isPartiallyDone: Boolean = false,
    onStart: () -> Unit
) {
    val exerciseTypeEnum = try { com.example.exerciseformanalyzer.model.ExerciseType.valueOf(exerciseJson.optString("exerciseType")) } catch(e: Exception) { com.example.exerciseformanalyzer.model.ExerciseType.UNKNOWN }
    val name = if (exerciseTypeEnum != com.example.exerciseformanalyzer.model.ExerciseType.UNKNOWN) exerciseTypeEnum.displayName else exerciseJson.optString("exerciseType", stringResource(R.string.ui_exercise))
    val totalSets = exerciseJson.optInt("sets", 1)
    val targetReps = exerciseJson.optInt("targetReps", 0)
    val targetDur = exerciseJson.optInt("targetDurationSeconds", 0)
    val restTime = if (exerciseJson.has("restTimeSeconds") && !exerciseJson.isNull("restTimeSeconds")) exerciseJson.optInt("restTimeSeconds") else null
    val targetStr = if (targetReps > 0) "$targetReps ${stringResource(R.string.ui_reps)}" else "$targetDur ${stringResource(R.string.ui_duration_label)}"

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
                contentDescription = stringResource(R.string.ui_completed),
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
                val displayCompletedSets = minOf(completedSets, totalSets)
                Text(
                    "$displayCompletedSets / $totalSets ${stringResource(R.string.ui_set)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (restTime != null && restTime > 0) {
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${stringResource(R.string.ui_rest_abbr)}: ${restTime}sn", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                    stringResource(R.string.ui_completed_check),
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
                Text(if (isPartiallyDone) stringResource(R.string.ui_continue) else stringResource(R.string.ui_start), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PatientFrequencyInfoRow(task: com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity) {
    val customDaysLabel = stringResource(R.string.ui_custom_days)
    val dayMap = mapOf(
        2 to stringResource(R.string.ui_mon),
        3 to stringResource(R.string.ui_tue),
        4 to stringResource(R.string.ui_wed),
        5 to stringResource(R.string.ui_thu),
        6 to stringResource(R.string.ui_fri),
        7 to stringResource(R.string.ui_sat),
        1 to stringResource(R.string.ui_sun)
    )
    val text = when (task.scheduleType) {
        "DAILY" -> stringResource(R.string.ui_every_day)
        "WEEKLY" -> stringResource(R.string.ui_weekly)
        "CUSTOM" -> {
            try {
                val daysArr = org.json.JSONArray(task.daysOfWeekJson)
                val selectedList = mutableListOf<String>()
                for (i in 0 until daysArr.length()) {
                    val dayIdx = daysArr.getInt(i)
                    dayMap[dayIdx]?.let { selectedList.add(it) }
                }
                "$customDaysLabel (${selectedList.joinToString(", ")})"
            } catch (e: Exception) { customDaysLabel }
        }
        else -> task.scheduleType
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Text("${stringResource(R.string.ui_plan_label)}: $text", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun PatientTaskStatusBadge(status: String) {
    val (label, color) = when (status.lowercase()) {
        "completed" -> stringResource(R.string.ui_completed) to Color(0xFF4CAF50)
        "in_progress" -> stringResource(R.string.ui_in_progress) to Color(0xFF2196F3)
        else -> stringResource(R.string.ui_waiting) to Color(0xFFFBC02D)
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
    
    val targetStr = if (targetReps > 0) "$targetReps ${stringResource(R.string.ui_reps)}" else "$targetDur ${stringResource(R.string.ui_duration_label)}"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(targetStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text("$compSets / $totalSets ${stringResource(R.string.ui_set)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
@Composable
fun PatientReportCard(report: WorkoutReportEntity) {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    val dateStr = sdf.format(java.util.Date(report.timestamp))
    val reportExerciseName = report.exerciseName
        .takeUnless {
            it.isBlank() ||
                it.equals("Algılanıyor...", ignoreCase = true) ||
                it.equals("Detecting...", ignoreCase = true) ||
                it.equals("Bilinmeyen", ignoreCase = true) ||
                it.equals("Bilinmiyor", ignoreCase = true) ||
                it.equals("Unknown", ignoreCase = true)
        }
        ?: stringResource(R.string.ui_unknown)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reportExerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                
                Surface(
                    color = when {
                        report.score >= 80 -> Color(0xFFE8F5E9)
                        report.score >= 60 -> Color(0xFFFFF3E0)
                        else -> Color(0xFFFFEBEE)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "%${report.score}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            report.score >= 80 -> Color(0xFF2E7D32)
                            report.score >= 60 -> Color(0xFFEF6C00)
                            else -> Color(0xFFC62828)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReportInfoItem(
                    icon = Icons.Default.Repeat,
                    label = stringResource(R.string.ui_reps),
                    value = "${report.reps}"
                )
                ReportInfoItem(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.ui_duration_label),
                    value = "${report.totalTimeSeconds}sn"
                )
                ReportInfoItem(
                    icon = Icons.Default.Whatshot,
                    label = stringResource(R.string.ui_calories),
                    value = "${report.caloriesBurned.toInt()}kcal"
                )
            }

            if (!report.feedback.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = report.feedback,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
fun ReportInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
