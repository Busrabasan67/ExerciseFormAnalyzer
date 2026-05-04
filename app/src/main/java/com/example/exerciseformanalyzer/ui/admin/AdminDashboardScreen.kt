package com.example.exerciseformanalyzer.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    patientViewModel: com.example.exerciseformanalyzer.ui.dashboard.PatientViewModel,
    expertViewModel: com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel,
    onNavigateToCamera: (com.example.exerciseformanalyzer.model.ExerciseType?) -> Unit,
    onNavigateToTaskExercise: (com.example.exerciseformanalyzer.ui.dashboard.TaskExerciseStartParams) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToSocial: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToPatientDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val selectedPanel by viewModel.selectedAdminPanel.collectAsState()
    val panelTabs = listOf("Sistem Admin", "Hasta Görünümü", "Doktor Görünümü")
    
    var selectedAdminSubTab by remember { mutableIntStateOf(0) }
    val adminTabs = listOf("Genel Bakış", "Kullanıcılar", "Görev & Rozetler")
    
    val stats by viewModel.adminSystemStats.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchAdminStats()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Admin Paneli") },
                    actions = {
                        IconButton(onClick = { viewModel.setShowLogoutDialog(true) }) {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = "Çıkış")
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
                                    val allUsers by viewModel.allUsers.collectAsState()
                                    UserManagementTab(allUsers)
                                }
                                2 -> QuestBadgeTab()
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
                        onNavigateToSocial = onNavigateToSocial,
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
                        onNavigateToSocial = onNavigateToSocial,
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
    Column {
        Text("Sistem İstatistikleri", style = MaterialTheme.typography.headlineSmall)
    
        Spacer(modifier = Modifier.height(16.dp))
     Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Toplam Hasta", stats.totalUsers.toString(), Modifier.weight(1f))
            StatCard("Toplam Uzman", stats.totalExperts.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Bugünkü Antrenman", stats.dailyWorkouts.toString(), Modifier.weight(1f))
            StatCard("Aktif Gruplar", stats.activeGroups.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        StatCard("Toplam Yakılan Kalori", "${stats.totalCalories.toInt()} kcal", Modifier.fillMaxWidth())
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun UserManagementTab(users: List<com.example.exerciseformanalyzer.model.firestore.FirestoreUser>) {
    Column {
        Text("Sistem Kullanıcıları", style = MaterialTheme.typography.titleMedium)
        Text("${users.size} kayıt bulundu.", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (users.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                items(users) { user ->
                    UserListItem(user)
                }
            }
        }
    }
}

@Composable
fun UserListItem(user: com.example.exerciseformanalyzer.model.firestore.FirestoreUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when(user.role) {
                "ADMIN" -> MaterialTheme.colorScheme.tertiaryContainer
                "EXPERT" -> MaterialTheme.colorScheme.secondaryContainer
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
            }
            Badge(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text(user.role, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun QuestBadgeTab() {
    Column {
        Text("Rozet & Görev Oluşturucu", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tüm sistem kullanıcıları için yeni hedefler tanımlayın.", style = MaterialTheme.typography.bodySmall)
        
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Görev Adı") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Hedef (örn: 50 Squat)") }, modifier = Modifier.fillMaxWidth())
        
        Button(onClick = {}, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
            Text("Sistem Genelinde Yayınla")
        }
    }
}
