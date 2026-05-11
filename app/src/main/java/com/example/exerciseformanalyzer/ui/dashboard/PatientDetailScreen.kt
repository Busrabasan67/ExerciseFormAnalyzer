package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.exerciseformanalyzer.ui.dashboard.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    viewModel: com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel,
    patientUid: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())
    val patient = remember(patients, patientUid) { patients.find { it.uid == patientUid } }
    val patientName = patient?.fullName ?: stringResource(R.string.ui_patient_detail)

    var startDate by remember { mutableStateOf<Date?>(null) }
    var endDate by remember { mutableStateOf<Date?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val stats by viewModel.observePatientStats(patientUid, startDate, endDate).collectAsState(initial = com.example.exerciseformanalyzer.model.WorkoutStats())
    val detailedAnalysis by viewModel.detailedAnalysis.collectAsState()
    
    LaunchedEffect(patientUid) {
        viewModel.loadDetailedAnalysis(patientUid)
    }
    
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(patientName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (startDate != null && endDate != null) 
                                "${sdf.format(startDate!!)} - ${sdf.format(endDate!!)}" 
                            else stringResource(R.string.ui_last_30_days_default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToChat(patientName) }) {
                        Icon(Icons.Default.Chat, contentDescription = stringResource(R.string.ui_send_message))
                    }
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.ui_select_date))
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf(stringResource(R.string.ui_profile), stringResource(R.string.ui_calories_tab), stringResource(R.string.ui_performance_tab), stringResource(R.string.ui_tasks), stringResource(R.string.ui_history), stringResource(R.string.ui_analysis))

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
                    if (patient != null) {
                        PatientProfileSection(patient)
                    } else {
                        Text(stringResource(R.string.ui_patient_info_load_failed))
                    }
                }
                1 -> {
                    Text(stringResource(R.string.ui_calorie_burn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (stats.dailyCalories.isNotEmpty()) {
                        CalorieBarChart(data = stats.dailyCalories)
                    } else {
                        EmptyDataState(stringResource(R.string.ui_no_calorie_data_range))
                    }
                }
                2 -> {
                    Text(stringResource(R.string.ui_form_score_trend_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (stats.scoreTrend.isNotEmpty()) {
                        FormScoreLineChart(data = stats.scoreTrend)
                    } else {
                        EmptyDataState(stringResource(R.string.ui_no_performance_data_range))
                    }
                }
                3 -> {
                    Text(stringResource(R.string.ui_task_status_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (stats.completionStats.isNotEmpty()) {
                        TaskPieChart(stats = stats.completionStats)
                    } else {
                        EmptyDataState(stringResource(R.string.ui_no_assigned_tasks))
                    }
                }
                4 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.ui_recent_workouts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.ui_records_count, stats.recentReports.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (stats.recentReports.isNotEmpty()) {
                        stats.recentReports.forEach { report ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp),
                                border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val reportDate = report.timestamp?.let { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(it) } ?: stringResource(R.string.ui_unknown_date)
                                    
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Surface(
                                                color = if (report.score >= 80) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (report.score >= 80) Icons.Default.CheckCircle else Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = if (report.score >= 80) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    modifier = Modifier.padding(6.dp).size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = report.exerciseName.ifEmpty { stringResource(R.string.ui_exercise) }.uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF1B5E20)
                                                )
                                                Text(reportDate, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${report.score}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                color = if (report.score >= 80) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                            Text(stringResource(R.string.ui_point), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${report.reps} ${stringResource(R.string.ui_reps)}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${report.durationSeconds / 60}dk ${report.durationSeconds % 60}sn", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                        }
                                    }
                                    
                                    if (!report.feedback.isNullOrBlank() && report.feedback != stringResource(R.string.ui_perfect_form)) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            color = Color(0xFFFFFBFA),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(0.5.dp, Color(0xFFFFDAD6))
                                        ) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFBA1A1A), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(report.feedback, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBA1A1A))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyDataState(stringResource(R.string.ui_no_workout_history))
                    }
                }
                5 -> {
                    val exerciseStats = detailedAnalysis["exerciseStats"] as? Map<String, Map<String, Any>> ?: emptyMap()
                    
                    Text(stringResource(R.string.ui_clinical_exercise_analysis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_exercise_analysis_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    if (exerciseStats.isNotEmpty()) {
                        exerciseStats.forEach { (exerciseName, data) ->
                            val avgScore = (data["avgScore"] as? Double) ?: 0.0
                            val totalReps = (data["totalReps"] as? Number)?.toInt() ?: 0
                            val sessionCount = (data["sessionCount"] as? Int) ?: 0
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = exerciseName.uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            color = if (avgScore >= 80) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${stringResource(R.string.ui_avg_score_label)}: ${String.format("%.1f", avgScore)}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (avgScore >= 80) Color(0xFF2E7D32) else Color(0xFFE65100)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = (avgScore / 100).toFloat(),
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = if (avgScore >= 80) Color(0xFF4CAF50) else Color(0xFFFFA000)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        AnalysisMiniStat(stringResource(R.string.ui_total_reps), "$totalReps")
                                        AnalysisMiniStat(stringResource(R.string.ui_session_count), "$sessionCount")
                                        AnalysisMiniStat(stringResource(R.string.ui_efficiency), "%${String.format("%.0f", avgScore)}")
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyDataState(stringResource(R.string.ui_detailed_analysis_not_ready))
                    }
                }
            } // when(selectedTab) ends

            if (selectedTab != 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.ui_statistics_summary), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val totalKcal = stats.dailyCalories.sumOf { it.second.toDouble() }.toInt()
                        val avgScore = if (stats.scoreTrend.isNotEmpty()) stats.scoreTrend.map { it.second }.average().toInt() else 0
                        
                        SummaryItem(Icons.Default.LocalFireDepartment, stringResource(R.string.ui_total_burned), "$totalKcal kcal")
                        SummaryItem(Icons.Default.Star, stringResource(R.string.ui_avg_score), "$avgScore / 100")
                        SummaryItem(Icons.Default.TaskAlt, stringResource(R.string.ui_completed_task), "${stats.completionStats["COMPLETED"] ?: 0}")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = dateRangePickerState.selectedStartDateMillis?.let { Date(it) }
                    endDate = dateRangePickerState.selectedEndDateMillis?.let { Date(it) }
                    showDateRangePicker = false
                }) {
                    Text(stringResource(R.string.ui_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PatientProfileSection(user: com.example.exerciseformanalyzer.data.local.entity.UserEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_profile_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileInfoRow(Icons.Default.Person, stringResource(R.string.ui_full_name), user.fullName)
                ProfileInfoRow(Icons.Default.Email, stringResource(R.string.email_label), user.email)
                ProfileInfoRow(Icons.Default.Wc, stringResource(R.string.ui_gender), if (user.gender == "MALE") stringResource(R.string.ui_male) else stringResource(R.string.ui_female))
                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        ProfileInfoRow(Icons.Default.Cake, stringResource(R.string.ui_age), stringResource(R.string.ui_years_old, user.age ?: "-"))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ProfileInfoRow(Icons.Default.Height, stringResource(R.string.height_label), "${user.heightCm ?: "-"} cm")
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        ProfileInfoRow(Icons.Default.MonitorWeight, stringResource(R.string.weight_label), "${user.weightKg ?: "-"} kg")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val bmi = if (user.heightCm != null && user.heightCm!! > 0 && user.weightKg != null) {
                            val h = user.heightCm!! / 100f
                            user.weightKg!! / (h * h)
                        } else null
                        ProfileInfoRow(Icons.Default.Speed, stringResource(R.string.ui_bmi), bmi?.let { String.format("%.1f", it) } ?: "-")
                    }
                }
            }
        }

        Text(stringResource(R.string.ui_health_and_activity), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileInfoRow(Icons.Default.DirectionsRun, stringResource(R.string.ui_activity_level), 
                    when(user.activityLevel) {
                        "low" -> stringResource(R.string.ui_low_activity)
                        "medium" -> stringResource(R.string.ui_medium_activity)
                        "high" -> stringResource(R.string.ui_high_activity)
                        else -> user.activityLevel ?: stringResource(R.string.ui_unknown)
                    }
                )
                ProfileInfoRow(Icons.Default.Flag, stringResource(R.string.goal_label), user.goal ?: stringResource(R.string.ui_not_specified_goal))
                
                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                
                Text(stringResource(R.string.ui_diseases_conditions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (user.diseaseInfo.isNullOrBlank()) stringResource(R.string.ui_no_disease_record) else user.diseaseInfo!!,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (user.hasHernia) Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text(stringResource(R.string.ui_hernia)) }
                    if (user.hasMeniscus) Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text(stringResource(R.string.ui_meniscus)) }
                    if (user.isSmoker) Badge { Text(stringResource(R.string.ui_smoker)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SummaryItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalysisMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EmptyDataState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.outline)
    }
}
