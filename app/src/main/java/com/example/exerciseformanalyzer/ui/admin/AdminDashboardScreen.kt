package com.example.exerciseformanalyzer.ui.admin

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.ui.dashboard.AdminViewModel
import com.example.exerciseformanalyzer.ui.dashboard.AdminPanelType
import com.example.exerciseformanalyzer.ui.components.LogoutConfirmationDialog
import com.example.exerciseformanalyzer.ui.dashboard.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    mainViewModel: com.example.exerciseformanalyzer.ui.MainViewModel,
    patientViewModel: com.example.exerciseformanalyzer.ui.dashboard.PatientViewModel,
    expertViewModel: com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel,
    onNavigateToCamera: (com.example.exerciseformanalyzer.model.ExerciseType?) -> Unit,
    onNavigateToTaskExercise: (com.example.exerciseformanalyzer.ui.dashboard.TaskExerciseStartParams) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToPatientDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val selectedPanel by viewModel.selectedAdminPanel.collectAsState()
    val panelTabs = listOf(stringResource(R.string.ui_system_admin), stringResource(R.string.ui_patient_view), stringResource(R.string.ui_expert_view))
    
    var selectedAdminSubTab by remember { mutableIntStateOf(0) }
    val adminTabs = listOf(stringResource(R.string.ui_overview), stringResource(R.string.ui_users), stringResource(R.string.ui_tasks_and_badges), stringResource(R.string.ui_groups))
    
    val stats by viewModel.adminSystemStats.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchAdminStats()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setShowLogoutDialog(false)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.ui_admin_panel)) },
                    actions = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val isDarkMode by mainViewModel.isDarkMode.collectAsState()
                        
                        IconButton(onClick = { mainViewModel.setDarkMode(!isDarkMode) }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode"
                            )
                        }

                        IconButton(onClick = { 
                            viewModel.sendAdminPasswordReset { success, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.VpnKey, contentDescription = stringResource(R.string.change_password))
                        }
                        IconButton(onClick = { viewModel.setShowLogoutDialog(true) }) {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = stringResource(R.string.ui_logout))
                        }
                    }
                )
                // ANA PANEL SEÇİCİ
                TabRow(
                    selectedTabIndex = selectedPanel.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    panelTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedPanel.ordinal == index,
                            onClick = { viewModel.setAdminPanel(AdminPanelType.entries[index]) },
                            text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    ) { paddingVals ->
        Box(modifier = Modifier.padding(paddingVals)) {
            when (selectedPanel) {
                AdminPanelType.ADMIN -> {
                    Column {
                        // ALT SEKME SEÇİCİ (Admin Özel)
                        TabRow(
                            selectedTabIndex = selectedAdminSubTab,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            adminTabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedAdminSubTab == index,
                                    onClick = { selectedAdminSubTab = index },
                                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.padding(16.dp)) {
                            when (selectedAdminSubTab) {
                                0 -> OverviewTab(stats)
                                1 -> {
                                    UserManagementTab(viewModel)
                                }
                                2 -> {
                                    BadgeManagementTab(viewModel)
                                }
                                3 -> {
                                    GroupManagementTab(viewModel)
                                }
                            }
                        }
                    }
                }
                AdminPanelType.PATIENT -> {
                    com.example.exerciseformanalyzer.ui.dashboard.PatientDashboardScreen(
                        viewModel = patientViewModel,
                        onNavigateToCamera = onNavigateToCamera,
                        onNavigateToTaskExercise = onNavigateToTaskExercise,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToGroups = onNavigateToGroups,

                        onNavigateToLeaderboard = onNavigateToLeaderboard,
                        onLogout = onLogout,
                        onNavigateToChat = { _, _ -> }
                    )
                }
                AdminPanelType.EXPERT -> {
                    com.example.exerciseformanalyzer.ui.dashboard.ExpertDashboardScreen(
                        viewModel = expertViewModel,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToPatientDetail = onNavigateToPatientDetail,
                        onNavigateToGroups = onNavigateToGroups,
                        onNavigateToChat = { _, _ -> },

                        onLogout = onLogout
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
    }
}

@Composable
fun OverviewTab(stats: com.example.exerciseformanalyzer.model.AdminSystemStats) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.ui_system_summary), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.ui_admin_perf_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(stringResource(R.string.ui_total_patients), stats.totalUsers.toString(), Modifier.weight(1f), Icons.Default.People, MaterialTheme.colorScheme.primary)
            StatCard(stringResource(R.string.ui_total_experts), stats.totalExperts.toString(), Modifier.weight(1f), Icons.Default.MedicalServices, MaterialTheme.colorScheme.secondary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(stringResource(R.string.ui_daily_workouts), stats.dailyWorkouts.toString(), Modifier.weight(1f), Icons.Default.FitnessCenter, Color(0xFF4CAF50))
            StatCard(stringResource(R.string.ui_active_groups_stat), stats.activeGroups.toString(), Modifier.weight(1f), Icons.Default.Groups, Color(0xFF2196F3))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(stringResource(R.string.ui_user_distribution), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (stats.roleDistribution.isNotEmpty()) {
                    TaskPieChart(
                        stats = stats.roleDistribution,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_loading_data), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(stringResource(R.string.ui_7_day_activity_trend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (stats.workoutTrend.isNotEmpty()) {
                    CalorieBarChart(
                        data = stats.workoutTrend.map { it.first to it.second.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_no_enough_data), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(stringResource(R.string.ui_popular_exercises), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        stats.exercisePopularity.forEach { (name, count) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Badge { Text(stringResource(R.string.ui_seans_count, count)) }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementTab(viewModel: AdminViewModel) {
    val users by viewModel.filteredUsers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedRole by viewModel.selectedRoleFilter.collectAsState()

    Column {
        Text(stringResource(R.string.ui_system_users), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ui_records_found, users.size), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text(stringResource(R.string.ui_search_user_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val roles = listOf(null to stringResource(R.string.ui_all), "PATIENT" to stringResource(R.string.ui_patient), "EXPERT" to stringResource(R.string.ui_expert), "ADMIN" to "Admin")
            roles.forEach { (role, label) ->
                FilterChip(
                    selected = selectedRole == role,
                    onClick = { viewModel.updateRoleFilter(role) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (users.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                if (viewModel.allUsers.collectAsState().value.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.ui_no_results))
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                items(users) { user ->
                    UserListItem(user, viewModel)
                }
            }
        }
    }
}

@Composable
fun UserListItem(user: com.example.exerciseformanalyzer.model.firestore.FirestoreUser, viewModel: AdminViewModel) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                user.status == "PASSIVE" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                user.role == "ADMIN" -> MaterialTheme.colorScheme.tertiaryContainer
                user.role == "EXPERT" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when(user.role) {
                    "ADMIN" -> Icons.Default.AdminPanelSettings
                    "EXPERT" -> Icons.Default.MedicalServices
                    else -> Icons.Default.Person
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(user.email, style = MaterialTheme.typography.bodySmall)
                if (user.status == "PASSIVE") {
                    Text(stringResource(R.string.ui_passive_account), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Badge(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text(user.role, modifier = Modifier.padding(4.dp))
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ui_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (user.role != "EXPERT") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_make_expert)) },
                            onClick = { 
                                viewModel.updateUserRole(user.uid, "EXPERT")
                                showMenu = false
                            }
                        )
                    }
                    if (user.role != "PATIENT") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_make_patient)) },
                            onClick = { 
                                viewModel.updateUserRole(user.uid, "PATIENT")
                                showMenu = false
                            }
                        )
                    }
                    if (user.status == "ACTIVE") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_freeze_account)) },
                            onClick = { 
                                viewModel.updateUserStatus(user.uid, "PASSIVE")
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_activate_account)) },
                            onClick = { 
                                viewModel.updateUserStatus(user.uid, "ACTIVE")
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeManagementTab(viewModel: AdminViewModel) {
    val badges by viewModel.badges.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingBadgeId by remember { mutableStateOf<String?>(null) }
    
    // Form States
    var name by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var descriptionEn by remember { mutableStateOf("") }
    var targetValue by remember { mutableStateOf("") }
    var xpReward by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("SQUAT") }

    fun openDialogForCreate() {
        editingBadgeId = null
        name = ""
        nameEn = ""
        description = ""
        descriptionEn = ""
        targetValue = ""
        xpReward = ""
        category = "SQUAT"
        showDialog = true
    }

    fun openDialogForEdit(id: String, badge: com.example.exerciseformanalyzer.model.firestore.FirestoreBadgeDefinition) {
        editingBadgeId = id
        name = badge.name
        nameEn = badge.nameEn
        description = badge.description
        descriptionEn = badge.descriptionEn
        targetValue = badge.targetValue.toString()
        xpReward = badge.xpReward.toString()
        category = badge.category
        showDialog = true
    }
    
    LaunchedEffect(Unit) {
        viewModel.fetchBadges()
    }

    var badgeToDeleteId by remember { mutableStateOf<String?>(null) }

    if (badgeToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { badgeToDeleteId = null },
            title = { Text(stringResource(R.string.ui_delete_badge_title)) },
            text = { Text(stringResource(R.string.ui_delete_badge_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        badgeToDeleteId?.let { viewModel.deleteBadge(it) }
                        badgeToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { badgeToDeleteId = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.ui_badge_factory), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ui_badge_factory_desc), style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { openDialogForCreate() }, 
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ui_add))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui_new_badge))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (badges.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_no_badges_defined))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(badges) { (id, badge) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val currentLang = java.util.Locale.getDefault().language
                                val isEn = currentLang == "en"
                                val displayTitle = if (isEn && badge.nameEn.isNotBlank()) badge.nameEn else badge.name
                                val displayDesc = if (isEn && badge.descriptionEn.isNotBlank()) badge.descriptionEn else badge.description
                                
                                Text(displayTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(displayDesc, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                        val targetText = if (badge.category == "CALORIES") "${badge.targetValue} kcal" else "${badge.targetValue} ${badge.category}"
                                        Text(targetText, modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("+${badge.xpReward} XP", modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            IconButton(onClick = { openDialogForEdit(id, badge) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.ui_edit), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { badgeToDeleteId = id }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        // Enum isimleriyle birebir eşleşmeli (ExerciseType.name)
        val categories = listOf(
            "SQUAT", "PUSH_UP", "SIT_UP", "DUMBBELL_ROW", 
            "BICEPS_CURL", "PLANK", "SHOULDER_PRESS", "LATERAL_RAISE", 
            "HAMMER_CURL", "TRICEPS_EXTENSION", "TRICEPS_KICKBACK", "BENT_OVER_ROW", 
            "BENT_OVER_RAISE", "MOUNTAIN_CLIMBER", "RUSSIAN_TWIST", "HEEL_TAP", 
            "BICYCLE_CRUNCH", "REVERSE_CRUNCH", "STRAIGHT_LEG_CRUNCH",
            "CALORIES",  // Yakılan kalori bazlı
            "XP"         // Kazanılan XP bazlı
        )
        var expandedCategory by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingBadgeId == null) stringResource(R.string.ui_create_new_badge) else stringResource(R.string.ui_edit_badge)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ui_badge_name_tr)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text(stringResource(R.string.ui_badge_name_en)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.ui_desc_tr)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = descriptionEn, onValueChange = { descriptionEn = it }, label = { Text(stringResource(R.string.ui_desc_en)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        ExposedDropdownMenuBox(
                            expanded = expandedCategory,
                            onExpandedChange = { expandedCategory = !expandedCategory }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ui_category)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategory,
                                onDismissRequest = { expandedCategory = false }
                            ) {
                                categories.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            category = selectionOption
                                            expandedCategory = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val targetLabel = if (category == "CALORIES") stringResource(R.string.ui_target_kcal) else stringResource(R.string.ui_target_count)
                            OutlinedTextField(
                                value = targetValue, 
                                onValueChange = { targetValue = it.filter { char -> char.isDigit() } }, 
                                label = { Text(targetLabel) }, 
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = xpReward, 
                                onValueChange = { xpReward = it.filter { char -> char.isDigit() } }, 
                                label = { Text(stringResource(R.string.ui_xp_reward)) }, 
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && targetValue.isNotBlank() && xpReward.isNotBlank()) {
                        if (editingBadgeId == null) {
                            viewModel.createBadge(
                                name = name,
                                nameEn = nameEn,
                                description = description,
                                descriptionEn = descriptionEn,
                                category = category,
                                targetValue = targetValue.toIntOrNull() ?: 0,
                                xpReward = xpReward.toIntOrNull() ?: 0
                            )
                        } else {
                            viewModel.updateBadge(
                                badgeId = editingBadgeId!!,
                                name = name,
                                nameEn = nameEn,
                                description = description,
                                descriptionEn = descriptionEn,
                                category = category,
                                targetValue = targetValue.toIntOrNull() ?: 0,
                                xpReward = xpReward.toIntOrNull() ?: 0
                            )
                        }
                        showDialog = false
                    }
                }) {
                    Text(stringResource(R.string.ui_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.ui_cancel)) }
            }
        )
    }
}

@Composable
fun GroupManagementTab(viewModel: AdminViewModel) {
    val groups by viewModel.allGroups.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.fetchAllGroups()
    }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ui_system_groups), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { viewModel.fetchAllGroups() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ui_refresh))
            }
        }
        Text(stringResource(R.string.ui_active_groups_found, groups.size), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_group_not_found))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups) { group ->
                    AdminGroupListItem(group, viewModel)
                }
            }
        }
    }
}

@Composable
fun AdminGroupListItem(
    group: com.example.exerciseformanalyzer.model.firestore.FirestoreGroup,
    viewModel: AdminViewModel
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(group.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                }
                
                Row {
                    IconButton(onClick = { viewModel.updateGroupVisibility(group.id, group.isPrivate) }) {
                        Icon(
                            imageVector = if (!group.isPrivate) Icons.Default.Public else Icons.Default.Lock,
                            contentDescription = stringResource(R.string.ui_privacy),
                            tint = if (!group.isPrivate) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                    var showMemberDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { 
                        viewModel.loadGroupMembers(group.id)
                        showMemberDialog = true 
                    }) {
                        Icon(Icons.Default.Group, contentDescription = stringResource(R.string.ui_members), tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    if (showMemberDialog) {
                        GroupMemberManagementDialog(
                            group = group,
                            viewModel = viewModel,
                            onDismiss = { showMemberDialog = false }
                        )
                    }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Badge(containerColor = if (!group.isPrivate) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)) {
                    val statusText = if (!group.isPrivate) stringResource(R.string.ui_public) else stringResource(R.string.ui_private)
                    Text(statusText, style = MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.ui_created_date, group.createdAt?.let { java.text.SimpleDateFormat("dd/MM/yyyy").format(it) } ?: "-"), 
                     style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.ui_delete_group_q)) },
            text = { Text(stringResource(R.string.ui_delete_group_confirm, group.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(group.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.ui_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberManagementDialog(
    group: com.example.exerciseformanalyzer.model.firestore.FirestoreGroup,
    viewModel: AdminViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.selectedGroupMembers.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    var showAddMemberSection by remember { mutableStateOf(false) }
    var userSearchQuery by remember { mutableStateOf("") }
    
    // Onay Diyalogları State'leri
    var memberToChangeRole by remember { mutableStateOf<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember?>(null) }
    var memberToMakeCreator by remember { mutableStateOf<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember?>(null) }
    var memberToRemove by remember { mutableStateOf<com.example.exerciseformanalyzer.model.firestore.FirestoreGroupMember?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_manage_members_title, group.name), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                // Mevcut Üyeler
                Text(stringResource(R.string.ui_group_members_count, members.size), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(members) { member ->
                        val userInfo = allUsers.find { it.uid == member.userId }
                        val isCreator = group.creatorId == member.userId
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCreator) 
                                    Color(0xFFFFF8E1) 
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            border = null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            userInfo?.fullName ?: stringResource(R.string.ui_unknown_user), 
                                            style = MaterialTheme.typography.titleSmall, 
                                            fontWeight = FontWeight.Bold
                                        )
                                        userInfo?.email?.let { 
                                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Badge(
                                                containerColor = when(member.role.lowercase()) {
                                                    "admin" -> MaterialTheme.colorScheme.primaryContainer
                                                    "moderator" -> MaterialTheme.colorScheme.secondaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ) {
                                                Text(member.role.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                                            }
                                            
                                            if (isCreator) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                                    Text(stringResource(R.string.ui_creator_admin), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (!isCreator) {
                                        IconButton(
                                            onClick = { memberToRemove = member },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_remove), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                
                                if (!isCreator) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Yetkili/Yetki Al (Moderator Yönetimi)
                                        TextButton(
                                            onClick = { memberToChangeRole = member },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            val actionText = if (member.role.lowercase() == "moderator") 
                                                stringResource(R.string.ui_revoke_authority) 
                                            else stringResource(R.string.ui_grant_authority)
                                            
                                            Text(
                                                actionText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (member.role.lowercase() == "moderator") 
                                                    MaterialTheme.colorScheme.error 
                                                else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Yönetici Yap (Lideri Değiştir)
                                        Button(
                                            onClick = { memberToMakeCreator = member },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                                        ) {
                                            Text(stringResource(R.string.ui_make_admin), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Yeni Üye Ekleme Bölümü
                if (!showAddMemberSection) {
                    Button(
                        onClick = { showAddMemberSection = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_add_member_externally))
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = { userSearchQuery = it },
                            label = { Text(stringResource(R.string.ui_search_user)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val filteredUsers = allUsers.filter { user ->
                            (user.fullName.contains(userSearchQuery, true) || user.email.contains(userSearchQuery, true)) &&
                            members.none { it.userId == user.uid }
                        }.take(3)

                        filteredUsers.forEach { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.fullName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(user.email, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                TextButton(onClick = { 
                                    viewModel.addMemberToGroup(group.id, user.uid, "MEMBER")
                                    userSearchQuery = ""
                                }) {
                                    Text(stringResource(R.string.ui_add), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        TextButton(onClick = { showAddMemberSection = false }, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.ui_close))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_ok)) }
        }
    )

    // ONAY DİYALOGLARI
    memberToChangeRole?.let { member ->
        val userInfo = allUsers.find { it.uid == member.userId }
        AlertDialog(
            onDismissRequest = { memberToChangeRole = null },
            title = { Text(stringResource(R.string.ui_authority_change)) },
            text = { 
                val memberName = userInfo?.fullName ?: stringResource(R.string.ui_unknown_user)
                val actionText = if (member.role.lowercase() == "moderator") stringResource(R.string.ui_action_revoke_authority) else stringResource(R.string.ui_action_grant_authority)
                Text(stringResource(R.string.ui_authority_change_confirm, memberName, actionText)) 
            },
            confirmButton = {
                Button(onClick = {
                    val newRole = if (member.role.lowercase() == "moderator") "member" else "moderator"
                    viewModel.updateMemberRole(group.id, member.userId, newRole)
                    memberToChangeRole = null
                }) { Text(stringResource(R.string.ui_yes)) }
            },
            dismissButton = { TextButton(onClick = { memberToChangeRole = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }

    memberToMakeCreator?.let { member ->
        val userInfo = allUsers.find { it.uid == member.userId }
        AlertDialog(
            onDismissRequest = { memberToMakeCreator = null },
            title = { Text(stringResource(R.string.ui_transfer_admin)) },
            text = { Text(stringResource(R.string.ui_transfer_admin_confirm, userInfo?.fullName ?: stringResource(R.string.ui_unknown_user))) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeGroupCreator(group.id, member.userId)
                        memberToMakeCreator = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                ) { Text(stringResource(R.string.ui_yes_transfer)) }
            },
            dismissButton = { TextButton(onClick = { memberToMakeCreator = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }

    memberToRemove?.let { member ->
        val userInfo = allUsers.find { it.uid == member.userId }
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(stringResource(R.string.ui_remove_member)) },
            text = { Text(stringResource(R.string.ui_remove_member_confirm, userInfo?.fullName ?: stringResource(R.string.ui_unknown_user))) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMemberFromGroup(group.id, member.userId)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_yes_remove)) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }
}
