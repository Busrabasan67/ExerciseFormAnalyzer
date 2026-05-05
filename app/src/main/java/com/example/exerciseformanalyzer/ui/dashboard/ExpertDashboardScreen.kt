package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Delete
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.components.AssignTaskDialog
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDashboardScreen(
    viewModel: ExpertViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToPatientDetail: (String) -> Unit,
    onNavigateToGroups: () -> Unit,

    onLogout: () -> Unit
) {
    val patients by viewModel.observeMyPatients().collectAsState(initial = emptyList())
    val tasks by viewModel.filteredTasks.collectAsState(initial = emptyList())
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val requestStatus by viewModel.requestStatus.collectAsState()
    val hasCommunityNotifications by viewModel.hasCommunityNotifications.collectAsState()
    val isEmailVerified by viewModel.isEmailVerified.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Hastalarım", "Görev Ata", "Takip")
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Form state for Task Assignment
    var assignmentPatientId by remember { mutableStateOf<String?>(null) }
    var assignmentTitle by remember { mutableStateOf("") }
    var assignmentNote by remember { mutableStateOf("") }
    
    var patientIdToRemove by remember { mutableStateOf<String?>(null) }
    var taskToEdit by remember { mutableStateOf<TaskAssignmentEntity?>(null) }
    var taskIdToDelete by remember { mutableStateOf<TaskAssignmentEntity?>(null) }
    var exerciseToDelete by remember { mutableStateOf<Triple<TaskAssignmentEntity, Int, String>?>(null) }
    
    LaunchedEffect(viewModel.currentUid) {
        if (viewModel.currentUid.isNotEmpty()) {
            viewModel.syncExpertData()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncExpertData()
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
                    title = { Text(stringResource(R.string.expert_dashboard_title)) },
                    actions = {

                        TextButton(onClick = onNavigateToGroups) {
                            BadgedBox(
                                badge = {
                                    if (hasCommunityNotifications) {
                                        Badge()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.groups_title))
                            }
                        }
                        TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                        TextButton(onClick = { viewModel.setShowLogoutDialog(true) }) { 
                            Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                
                // --- EMAIL VERIFICATION BANNER ---
                var showSuccessBanner by remember { mutableStateOf(false) }
                LaunchedEffect(isEmailVerified) {
                    if (isEmailVerified) {
                        // Başarı mesajı gösterilecek bir tetikleyici eklenebilir
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
                        color = Color(0xFF4CAF50),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("E-posta adresiniz başarıyla doğrulandı!", color = Color.White, style = MaterialTheme.typography.labelLarge)
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
            }
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
                                onValueChange = { 
                                    searchQuery = it
                                    viewModel.searchPatients(it)
                                },
                                label = { Text("Hasta E-posta Ara") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { viewModel.searchPatients(searchQuery) }),
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.searchPatients(searchQuery) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Ara")
                                    }
                                }
                            )
                            if (!searchError.isNullOrEmpty()) {
                                Text(text = searchError ?: "", color = MaterialTheme.colorScheme.error)
                            }
                            
                            // Arama Sonuçları (Autocomplete)
                            if (searchQuery.length >= 2 && searchResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Arama Sonuçları", style = MaterialTheme.typography.labelMedium)
                                searchResults.forEach { user ->
                                    val isPending = sentRequests.any { it.patientId == user.uid && it.status == "pending" }
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(user.fullName, style = MaterialTheme.typography.titleSmall)
                                                Text(user.email, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Button(
                                                onClick = { viewModel.sendConnectionRequest(user) },
                                                enabled = !isPending,
                                                colors = if (isPending) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.buttonColors()
                                            ) {
                                                Text(if (isPending) "Beklemede" else "İstek Gönder")
                                            }
                                        }
                                    }
                                }
                            }

                            if (requestStatus == "REMOVED") {
                                Text("Hasta listenizden kaldırıldı.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(3000)
                                    viewModel.clearRequestStatus()
                                }
                            }

                            if (!requestStatus.isNullOrEmpty() && requestStatus != "REMOVED") {
                                Text(requestStatus ?: "", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                                LaunchedEffect(requestStatus) {
                                    kotlinx.coroutines.delay(3000)
                                    viewModel.clearRequestStatus()
                                }
                            }

                            // Bekleyen İstekler Bölümü
                            if (sentRequests.any { it.status == "pending" }) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Bekleyen İstekler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                sentRequests.filter { it.status == "pending" }.forEach { req ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(req.patientName, style = MaterialTheme.typography.titleSmall)
                                                Text(req.patientEmail, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text("Beklemede", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Text(stringResource(R.string.patients_label), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(patients) { patient ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(patient.fullName, style = MaterialTheme.typography.titleMedium)
                                            Text(patient.email, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        IconButton(onClick = { patientIdToRemove = patient.uid }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Kaldır", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
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
                    // FOLLOW-UP / TAKİP
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Verilen Görevlerin Durumu", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TaskFilterChips(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (tasks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (selectedFilter == TaskFilter.ALL) {
                                    Text("Henüz görev yok.")
                                } else {
                                    Text("Seçili kriterde görev bulunamadı.")
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(tasks) { task ->
                                    val patient = patients.find { it.uid == task.patientUid }
                                    TaskTrackingCard(
                                        task = task,
                                        patientName = patient?.fullName ?: "Bilinmeyen Hasta",
                                        onEdit = { taskToEdit = it },
                                        onDelete = { taskIdToDelete = it },
                                        onDeleteExercise = { t, idx, name -> exerciseToDelete = Triple(t, idx, name) }
                                    )
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

        // Edit Task Dialog
        taskToEdit?.let { task ->
            val initialExercises = remember(task.exercisesJson) {
                try {
                    val arr = JSONArray(task.exercisesJson)
                    List(arr.length()) { i ->
                        val obj = arr.getJSONObject(i)
                        ExpertViewModel.TaskExerciseInput(
                            exerciseType = ExerciseType.valueOf(obj.optString("exerciseType", "SQUAT")),
                            isDurationBased = obj.optString("targetType") == "DURATION",
                            targetValue = (if (obj.optString("targetType") == "DURATION") obj.optInt("targetDurationSeconds") else obj.optInt("targetReps")).toString(),
                            sets = obj.optInt("sets", 1).toString(),
                            restTimeSeconds = obj.optInt("restTimeSeconds", 30).toString(),
                            difficulty = obj.optString("difficulty", "MEDIUM"),
                            category = obj.optString("category", "STRENGTH"),
                            videoUrl = obj.optString("videoUrl").takeIf { it.isNotEmpty() }
                        )
                    }
                } catch (e: Exception) { emptyList() }
            }

            val initialDays = remember(task.daysOfWeekJson) {
                try {
                    val arr = JSONArray(task.daysOfWeekJson)
                    List(arr.length()) { i -> arr.getInt(i) }
                } catch (e: Exception) { emptyList() }
            }

            AssignTaskDialog(
                onDismissRequest = { taskToEdit = null },
                dialogTitle = "Görevi Düzenle",
                defaultTitle = task.title,
                defaultNote = task.note,
                defaultSched = task.scheduleType,
                defaultDays = initialDays,
                defaultAuto = task.autoRepeat,
                defaultWeeks = task.repeatDurationWeeks,
                initialExercises = initialExercises,
                submitText = "Güncelle",
                onAssignTask = { title, note, dueDate, exercises, sched, days, auto, weeks ->
                    viewModel.updateTask(
                        taskId = task.id,
                        firebaseDocId = task.firebaseDocId,
                        patientUid = task.patientUid,
                        title = title,
                        note = note,
                        dueDate = dueDate,
                        exercises = exercises,
                        scheduleType = sched,
                        daysOfWeek = days,
                        autoRepeat = auto,
                        repeatWeeks = weeks
                    )
                    taskToEdit = null
                }
            )
        }

        // Delete Task Confirmation
        taskIdToDelete?.let { task ->
            AlertDialog(
                onDismissRequest = { taskIdToDelete = null },
                title = { Text("Görevi Sil") },
                text = { Text("'${task.title}' görevini tamamen silmek istediğinize emin misiniz? Bu işlem geri alınamaz.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTask(task.id, task.firebaseDocId)
                        taskIdToDelete = null
                    }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskIdToDelete = null }) {
                        Text("İptal")
                    }
                }
            )
        }

        // Delete Individual Exercise Confirmation
        exerciseToDelete?.let { (task, index, name) ->
            AlertDialog(
                onDismissRequest = { exerciseToDelete = null },
                title = { Text("Egzersizi Sil") },
                text = { Text("'$name' egzersizini bu görevden silmek istediğinize emin misiniz?") },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            val arr = JSONArray(task.exercisesJson)
                            val updatedList = mutableListOf<JSONObject>()
                            for (i in 0 until arr.length()) {
                                if (i != index) updatedList.add(arr.getJSONObject(i))
                            }
                            
                            val taskExerciseInputs = updatedList.map { obj ->
                                ExpertViewModel.TaskExerciseInput(
                                    exerciseType = ExerciseType.valueOf(obj.optString("exerciseType", "SQUAT")),
                                    isDurationBased = obj.optString("targetType") == "DURATION",
                                    targetValue = (if (obj.optString("targetType") == "DURATION") obj.optInt("targetDurationSeconds") else obj.optInt("targetReps")).toString(),
                                    sets = obj.optInt("sets", 1).toString(),
                                    restTimeSeconds = obj.optInt("restTimeSeconds", 30).toString(),
                                    difficulty = obj.optString("difficulty", "MEDIUM"),
                                    category = obj.optString("category", "STRENGTH"),
                                    videoUrl = obj.optString("videoUrl").takeIf { it.isNotEmpty() }
                                )
                            }

                            val days = try {
                                val dArr = JSONArray(task.daysOfWeekJson)
                                List(dArr.length()) { i -> dArr.getInt(i) }
                            } catch (e: Exception) { emptyList() }

                            viewModel.updateTask(
                                taskId = task.id,
                                firebaseDocId = task.firebaseDocId,
                                patientUid = task.patientUid,
                                title = task.title,
                                note = task.note,
                                dueDate = task.dueDate,
                                exercises = taskExerciseInputs,
                                scheduleType = task.scheduleType,
                                daysOfWeek = days,
                                autoRepeat = task.autoRepeat,
                                repeatWeeks = task.repeatDurationWeeks
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("ExpertDashboard", "Egzersiz silme hatası", e)
                        }
                        exerciseToDelete = null
                    }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exerciseToDelete = null }) {
                        Text("İptal")
                    }
                }
            )
        }

        patientIdToRemove?.let { pid ->
            AlertDialog(
                onDismissRequest = { patientIdToRemove = null },
                title = { Text("Hastayı kaldır") },
                text = { Text("Bu hastayı listenizden kaldırmak istediğinize emin misiniz?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removePatient(pid)
                        patientIdToRemove = null
                    }) {
                        Text("Kaldır", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { patientIdToRemove = null }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
fun TaskFilterChips(
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TaskFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    val label = when(filter) {
                        TaskFilter.ALL -> "Hepsi"
                        TaskFilter.PENDING -> "Bekleyenler"
                        TaskFilter.IN_PROGRESS -> "Devam Edenler"
                        TaskFilter.COMPLETED -> "Tamamlananlar"
                        TaskFilter.INACTIVE -> "Pasifler"
                    }
                    Text(label)
                }
            )
        }
    }
}

@Composable
fun TaskTrackingCard(
    task: TaskAssignmentEntity,
    patientName: String,
    onEdit: (TaskAssignmentEntity) -> Unit,
    onDelete: (TaskAssignmentEntity) -> Unit,
    onDeleteExercise: (TaskAssignmentEntity, Int, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
    // Parse exercises to calculate progress
    val exercises = remember(task.exercisesJson) {
        try {
            val arr = JSONArray(task.exercisesJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj
            }
        } catch (e: Exception) { emptyList() }
    }
    
    val totalSets = exercises.sumOf { it.optInt("sets", 0) }
    val completedSets = exercises.sumOf { it.optInt("completedSets", 0) }
    val progress = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hasta: $patientName",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TaskStatusBadge(task.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Info Rows
            val createdAtText = if (task.createdAt > 0L) sdf.format(Date(task.createdAt)) else "Belirtilmedi"
            TaskInfoRow(Icons.Default.Event, "Verildi: $createdAtText")
            
            val updatedAtText = if (task.updatedAt > task.createdAt && (task.updatedAt - task.createdAt) > 5000) {
                sdf.format(Date(task.updatedAt))
            } else null
            
            if (updatedAtText != null) {
                TaskInfoRow(Icons.Default.Edit, "Güncellendi: $updatedAtText")
            }
            
            val planText = when(task.scheduleType) {
                "DAILY" -> "Her Gün"
                "WEEKLY" -> "Haftalık"
                "CUSTOM" -> {
                    val daysText = try {
                        val arr = JSONArray(task.daysOfWeekJson)
                        val dayMap = mapOf(
                            2 to "Pzt", 3 to "Sal", 4 to "Çar", 5 to "Per", 6 to "Cum", 7 to "Cmt", 1 to "Paz"
                        )
                        val list = mutableListOf<Int>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.getInt(i))
                        }
                        // Sort: Monday(2) to Sunday(1)
                        list.sortedWith(compareBy { if (it == 1) 8 else it })
                            .mapNotNull { dayMap[it] }
                            .joinToString(", ")
                    } catch (e: Exception) { "Belirtilmedi" }
                    "Özel Günler • $daysText"
                }
                else -> task.scheduleType
            }
            TaskInfoRow(Icons.Default.Repeat, "Plan: $planText")
            
            if (task.repeatDurationWeeks != null) {
                TaskInfoRow(Icons.Default.Timer, "Süre: ${task.repeatDurationWeeks} hafta")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Section
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Egzersiz: ${exercises.size}", style = MaterialTheme.typography.bodySmall)
                Text("Set: $completedSets / $totalSets", style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            TaskProgressBar(progress)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Expand Toggle
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "Özeti Kapat" else "Detay")
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onEdit(task) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDelete(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    exercises.forEachIndexed { index, ex ->
                        ExerciseDetailRow(
                            ex = ex,
                            onDelete = {
                                val name = ex.optString("exerciseType", "Egzersiz")
                                onDeleteExercise(task, index, name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun TaskStatusBadge(status: String?) {
    val safeStatus = status?.lowercase() ?: "pending"
    val (text, color) = when (safeStatus) {
        "pending" -> "Bekliyor" to Color(0xFFFBC02D) // Sarımsı
        "in_progress" -> "Devam Ediyor" to Color(0xFF2196F3) // Mavi
        "completed", "done" -> "Tamamlandı" to Color(0xFF4CAF50) // Yeşil
        "missed" -> "Kaçırıldı" to Color(0xFFF44336) // Kırmızı
        "inactive", "removed" -> "Pasif" to Color.Gray
        else -> (status ?: "Bekliyor") to Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TaskProgressBar(progress: Float) {
    Column {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Text(
            text = "%${(progress * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.End),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ExerciseDetailRow(ex: org.json.JSONObject, onDelete: () -> Unit) {
    val name = ex.optString("exerciseType", "Egzersiz")
    val sets = ex.optInt("sets", 0)
    val comp = ex.optInt("completedSets", 0)
    val reps = ex.optInt("targetReps", 0)
    val dur = ex.optInt("targetDurationSeconds", 0)
    val targetStr = if (reps > 0) "$reps Tekrar" else "$dur Sn"
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$comp / $sets Set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Egzersizi Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(targetStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            val difficulty = ex.optString("difficulty", "MEDIUM")
            Text(difficulty, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
