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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.data.local.entity.UserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDashboardScreen(
    viewModel: ExpertViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToPatientDetail: (String) -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,

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
    val relationshipNotifications by viewModel.relationshipNotifications.collectAsState()
    val unreadChatPartnerIds by viewModel.unreadChatPartnerIds.collectAsState()
    val isEmailVerified by viewModel.isEmailVerified.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.observeCurrentUser().collectAsState(initial = null)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.ui_my_patients), stringResource(R.string.ui_assign_task), stringResource(R.string.ui_follow_up), stringResource(R.string.ui_chat), stringResource(R.string.ui_notifications))
    
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
                    title = {},
                    actions = {
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
                                    androidx.compose.foundation.Image(
                                        painter = coil.compose.rememberAsyncImagePainter(currentUser?.profileImageUrl),
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
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
            }
        }
    ) { paddingVals ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingVals)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF00C853),
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = Color(0xFF00C853)
                    )
                },
                divider = {},
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    val showBadge = when (index) {
                        3 -> unreadChatPartnerIds.isNotEmpty()
                        4 -> relationshipNotifications.isNotEmpty()
                        else -> false
                    }
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        text = {
                            BadgedBox(
                                badge = { if (showBadge) Badge(containerColor = MaterialTheme.colorScheme.error) }
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // PATIENTS LIST
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            relationshipNotifications.forEach { notification ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(notification.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { viewModel.dismissRelationshipNotification(notification.id) }) {
                                            Text(stringResource(R.string.ui_close))
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    viewModel.searchPatients(it)
                                },
                                placeholder = { Text(stringResource(R.string.ui_search_patient_email), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = Color(0xFF00C853),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { viewModel.searchPatients(searchQuery) }),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00C853)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = ""; viewModel.searchPatients("") }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear))
                                        }
                                    }
                                }
                            )
                            
                            if (!searchError.isNullOrEmpty()) {
                                Text(
                                    text = searchError ?: "", 
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            
                            // Arama Sonuçları (Autocomplete)
                            if (searchQuery.length >= 2 && searchResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(stringResource(R.string.ui_search_results), style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF2E7D32)))
                                searchResults.forEach { user ->
                                    val isPending = sentRequests.any { it.patientId == user.uid && it.status == "pending" }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(2.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(), 
                                            horizontalArrangement = Arrangement.SpaceBetween, 
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(user.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Button(
                                                onClick = { viewModel.sendConnectionRequest(user) },
                                                enabled = !isPending,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isPending) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF00C853)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 16.dp)
                                            ) {
                                                if (isPending) {
                                                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(stringResource(R.string.ui_pending))
                                                } else {
                                                    Text(stringResource(R.string.ui_send_request))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (requestStatus == "REMOVED") {
                                Text(stringResource(R.string.ui_patient_removed), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
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
                                Text(stringResource(R.string.ui_pending_requests), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.ui_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                TextButton(
                                                    onClick = { viewModel.cancelConnectionRequest(req.requestId) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Text(stringResource(R.string.ui_cancel_it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Text(
                                stringResource(R.string.patients_label), 
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(patients) { patient ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(), 
                                        horizontalArrangement = Arrangement.SpaceBetween, 
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = patient.fullName, 
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                                            )
                                            Text(
                                                text = patient.email, 
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                                            shape = CircleShape,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            IconButton(onClick = { patientIdToRemove = patient.uid }) {
                                                Icon(Icons.Default.PersonRemove, contentDescription = stringResource(R.string.ui_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { 
                                                assignmentPatientId = patient.uid
                                                selectedTab = 1 
                                            }, 
                                            modifier = Modifier.weight(1.3f),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.ui_assign_task), style = MaterialTheme.typography.labelLarge)
                                        }
                                        
                                        OutlinedButton(
                                            onClick = { onNavigateToPatientDetail(patient.uid) }, 
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.5f))
                                        ) {
                                            Text(stringResource(R.string.ui_detail), color = Color(0xFF00C853))
                                        }
                                        
                                        IconButton(
                                            onClick = { onNavigateToChat(patient.uid, patient.fullName) },
                                            modifier = Modifier
                                                .background(Color(0xFFE8F5E9), CircleShape)
                                                .size(44.dp)
                                        ) {
                                            BadgedBox(
                                                badge = {
                                                    if (patient.uid in unreadChatPartnerIds) Badge(containerColor = MaterialTheme.colorScheme.error)
                                                }
                                            ) {
                                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = stringResource(R.string.ui_chat), tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                                            }
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
                            Text(stringResource(R.string.ui_must_add_patient))
                        }
                    } else {
                        // Inline Task Assignment Form
                        var selectedPatient by remember { mutableStateOf(patients.find { it.uid == assignmentPatientId } ?: patients[0]) }
                        
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Text(stringResource(R.string.ui_create_new_task), style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(stringResource(R.string.ui_select_patient), style = MaterialTheme.typography.labelLarge)
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
                                    Text(stringResource(R.string.ui_set_exercises_assign))
                                }
                                
                                // Actually, let's keep the Dialog for the COMPLEX exercise selection 
                                // but the ENTRY point is now more visible.
                                if (assignmentPatientId != null && selectedTab == 1) {
                                    AssignTaskDialog(
                                        onDismissRequest = { assignmentPatientId = null },
                                        onAssignTask = { title, note, dueDate, exercises, sched, days, auto, weeks ->
                                            viewModel.assignTask(selectedPatient.uid, selectedPatient.fullName, title, note, dueDate, exercises, sched, days, auto, weeks)
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
                        Text(stringResource(R.string.ui_task_tracking_status), style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TaskFilterChips(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (tasks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (selectedFilter == TaskFilter.ALL) {
                                    Text(stringResource(R.string.ui_no_tasks))
                                } else {
                                    Text(stringResource(R.string.ui_no_tasks_found_filter))
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
                                    val isDisconnected = patient == null
                                    val nameToShow = patient?.fullName ?: task.patientName.takeIf { it.isNotBlank() } ?: stringResource(R.string.ui_unknown_patient)
                                    TaskTrackingCard(
                                        task = task,
                                        patientName = nameToShow,
                                        isDisconnected = isDisconnected,
                                        onEdit = { taskToEdit = it },
                                        onDelete = { taskIdToDelete = it },
                                        onDeleteExercise = { t, idx, name -> exerciseToDelete = Triple(t, idx, name) }
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    ExpertChatListTab(
                        patients = patients,
                        unreadChatPartnerIds = unreadChatPartnerIds,
                        onNavigateToChat = onNavigateToChat
                    )
                }
                4 -> {
                    ExpertNotificationsTab(
                        notifications = relationshipNotifications,
                        onDismiss = { viewModel.dismissRelationshipNotification(it) }
                    )
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
                            restTimeSeconds = if (obj.has("restTimeSeconds") && !obj.isNull("restTimeSeconds")) obj.optString("restTimeSeconds") else "",
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
            val unknownPatientLabel = stringResource(R.string.ui_unknown_patient)

            AssignTaskDialog(
                onDismissRequest = { taskToEdit = null },
                dialogTitle = stringResource(R.string.ui_edit_task),
                defaultTitle = task.title,
                defaultNote = task.note,
                defaultSched = task.scheduleType,
                defaultDays = initialDays,
                defaultAuto = task.autoRepeat,
                defaultWeeks = task.repeatDurationWeeks,
                initialExercises = initialExercises,
                submitText = stringResource(R.string.ui_update),
                onAssignTask = { title, note, dueDate, exercises, sched, days, auto, weeks ->
                    viewModel.updateTask(
                        taskId = task.id,
                        firebaseDocId = task.firebaseDocId,
                        patientUid = task.patientUid,
                        patientName = task.patientName.ifBlank { unknownPatientLabel },
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
                title = { Text(stringResource(R.string.ui_delete_task)) },
                text = { Text(stringResource(R.string.ui_delete_task_confirm, task.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTask(task.id, task.firebaseDocId)
                        taskIdToDelete = null
                    }) {
                        Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskIdToDelete = null }) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                }
            )
        }

        // Delete Individual Exercise Confirmation
        exerciseToDelete?.let { (task, index, name) ->
            val unknownPatientLabel = stringResource(R.string.ui_unknown_patient)
            AlertDialog(
                onDismissRequest = { exerciseToDelete = null },
                title = { Text(stringResource(R.string.ui_delete_exercise)) },
                text = { Text(stringResource(R.string.ui_delete_exercise_confirm, name)) },
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
                                    restTimeSeconds = if (obj.has("restTimeSeconds") && !obj.isNull("restTimeSeconds")) obj.optString("restTimeSeconds") else "",
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
                                patientName = task.patientName.ifBlank { unknownPatientLabel },
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
                        Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exerciseToDelete = null }) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                }
            )
        }

        patientIdToRemove?.let { pid ->
            AlertDialog(
                onDismissRequest = { patientIdToRemove = null },
                title = { Text(stringResource(R.string.ui_remove_patient)) },
                text = { Text(stringResource(R.string.ui_remove_patient_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removePatient(pid)
                        patientIdToRemove = null
                    }) {
                        Text(stringResource(R.string.ui_remove), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { patientIdToRemove = null }) {
                        Text(stringResource(R.string.ui_cancel))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TaskFilter.values().forEach { filter ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    val label = when(filter) {
                        TaskFilter.ALL -> stringResource(R.string.ui_all)
                        TaskFilter.PENDING -> stringResource(R.string.ui_pending)
                        TaskFilter.IN_PROGRESS -> stringResource(R.string.ui_in_progress)
                        TaskFilter.COMPLETED -> stringResource(R.string.ui_completed)
                        TaskFilter.INACTIVE -> stringResource(R.string.ui_passive)
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00C853),
                    selectedLabelColor = Color.White,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = if (!isSelected) FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                ) else null
            )
        }
    }
}

@Composable
fun TaskTrackingCard(
    task: TaskAssignmentEntity,
    patientName: String,
    isDisconnected: Boolean = false,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patientName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00796B)
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TaskStatusBadge(task.status)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isDisconnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.ui_connection_lost),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (task.note.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF1F8E9),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF388E3C))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_note_label), style = MaterialTheme.typography.labelMedium, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Info Grid (Simplified)
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    val createdAtText = if (task.createdAt > 0L) sdf.format(Date(task.createdAt)) else "-"
                    TaskInfoRow(Icons.Default.CalendarToday, stringResource(R.string.ui_start_date, createdAtText))
                    
                    val planText = when(task.scheduleType) {
                        "DAILY" -> stringResource(R.string.ui_daily)
                        "WEEKLY" -> stringResource(R.string.ui_weekly)
                        "CUSTOM" -> stringResource(R.string.ui_custom_days)
                        else -> task.scheduleType
                    }
                    TaskInfoRow(Icons.Default.Update, stringResource(R.string.ui_plan_value_label, planText))
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (task.repeatDurationWeeks != null) {
                        TaskInfoRow(Icons.Default.HourglassBottom, stringResource(R.string.ui_duration_weeks_value, task.repeatDurationWeeks))
                    }
                    val statusColor = if (progress >= 1f) Color(0xFF43A047) else Color(0xFF00C853)
                    TaskInfoRow(Icons.Default.DonutLarge, stringResource(R.string.ui_progress_label, (progress * 100).toInt()), color = statusColor)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Progress Bar
            TaskProgressBar(progress)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        if (expanded) stringResource(R.string.ui_hide_exercises) else stringResource(R.string.ui_show_exercises_count, exercises.size),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF2E7D32)
                    )
                }
                
                Row {
                    IconButton(onClick = { onEdit(task) }) {
                        Icon(Icons.Default.ModeEditOutline, contentDescription = stringResource(R.string.ui_edit), tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onDelete(task) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    val exerciseFallbackName = stringResource(R.string.ui_exercise)
                    exercises.forEachIndexed { index, ex ->
                        ExerciseDetailRow(
                            ex = ex, 
                            onDelete = { onDeleteExercise(task, index, ex.optString("exerciseType", exerciseFallbackName)) }
                        )
                        if (index < exercises.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpertChatListTab(
    patients: List<UserEntity>,
    unreadChatPartnerIds: Set<String>,
    onNavigateToChat: (String, String) -> Unit
) {
    if (patients.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ui_no_active_patient_chat))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(stringResource(R.string.ui_my_chats), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)))
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(patients) { patient ->
            val hasUnread = patient.uid in unreadChatPartnerIds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat(patient.uid, patient.fullName) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasUnread) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(if (hasUnread) 4.dp else 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = Color(0xFF00C853).copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = patient.fullName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(patient.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            if (hasUnread) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(containerColor = Color(0xFF00C853)) { Text(stringResource(R.string.ui_new_badge_text), color = Color.White) }
                            }
                        }
                        Text(patient.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ExpertNotificationsTab(
    notifications: List<com.example.exerciseformanalyzer.model.firestore.FirestoreRelationshipNotification>,
    onDismiss: (String) -> Unit
) {
    if (notifications.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp), 
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.ui_no_notifications), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(stringResource(R.string.ui_notifications_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)))
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(notifications) { notification ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9).copy(alpha = 0.8f)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF43A047).copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(notification.message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        if (notification.patientName.isNotBlank()) {
                            Text(notification.patientName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(
                        onClick = { onDismiss(notification.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text(stringResource(R.string.ui_close))
                    }
                }
            }
        }
    }
}

@Composable
fun TaskInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = Color.Gray) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
    }
}

@Composable
fun TaskStatusBadge(status: String?) {
    val safeStatus = status?.lowercase() ?: "pending"
    val (text, color) = when (safeStatus) {
        "pending" -> stringResource(R.string.ui_waiting) to Color(0xFFFFB300)
        "in_progress" -> stringResource(R.string.ui_in_progress) to Color(0xFF039BE5)
        "completed", "done" -> stringResource(R.string.ui_completed) to Color(0xFF43A047)
        "missed" -> stringResource(R.string.ui_missed) to Color(0xFFE53935)
        "inactive", "removed" -> stringResource(R.string.ui_passive) to Color.Gray
        else -> (status ?: stringResource(R.string.ui_waiting)) to Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = color
        )
    }
}

@Composable
fun TaskProgressBar(progress: Float) {
    Column {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = if (progress >= 1f) Color(0xFF43A047) else Color(0xFF00C853),
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ui_completion_label, (progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.End),
            color = if (progress >= 1f) Color(0xFF43A047) else Color(0xFF1B5E20)
        )
    }
}

@Composable
fun ExerciseDetailRow(ex: org.json.JSONObject, onDelete: () -> Unit) {
    val name = ex.optString("exerciseType", stringResource(R.string.ui_exercise))
    val sets = ex.optInt("sets", 0)
    val comp = ex.optInt("completedSets", 0)
    val reps = ex.optInt("targetReps", 0)
    val dur = ex.optInt("targetDurationSeconds", 0)
    val targetStr = if (reps > 0) stringResource(R.string.ui_reps_count, reps) else stringResource(R.string.ui_seconds_count, dur)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Surface(
                    color = Color(0xFF00C853).copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(R.string.ui_sets_progress, comp, sets), 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.ui_delete_exercise), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Text(targetStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("•", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    val rtStr = if (ex.has("restTimeSeconds") && !ex.isNull("restTimeSeconds")) ex.optString("restTimeSeconds") else null
                    val restTime = rtStr?.toIntOrNull()
                    val restTimeStr = if (restTime != null && restTime > 0) stringResource(R.string.ui_rest_time_label, restTime) else stringResource(R.string.ui_rest_time_auto)
                    Text(restTimeStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                val difficulty = ex.optString("difficulty", "MEDIUM")
                Text(difficulty.lowercase().capitalize(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
