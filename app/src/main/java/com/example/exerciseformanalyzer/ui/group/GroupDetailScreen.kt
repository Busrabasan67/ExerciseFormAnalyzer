package com.example.exerciseformanalyzer.ui.group

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.model.LeaderboardMetric
import com.example.exerciseformanalyzer.ui.social.LeaderboardItem
import com.example.exerciseformanalyzer.ui.social.LeaderboardViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onNavigateBack: () -> Unit
) {
    val leaderboardViewModel: LeaderboardViewModel = viewModel()
    val groupViewModel: GroupViewModel = viewModel()
    
    val rankings by leaderboardViewModel.rankings.collectAsState()
    val isLoading by leaderboardViewModel.isLoading.collectAsState()
    
    val selectedGroup by groupViewModel.selectedGroup.collectAsState()
    
    val currentUid = groupViewModel.currentUid
    val isAdmin = currentUid == (selectedGroup?.creatorId ?: "")
    
    val pendingRequests by groupViewModel.pendingRequests.collectAsState()
    val firestoreMembers by groupViewModel.groupMembersFirestore.collectAsState()

    LaunchedEffect(selectedGroup?.docId) {
        val groupId = selectedGroup?.docId ?: return@LaunchedEffect
        leaderboardViewModel.setMetric(LeaderboardMetric.CALORIES)
        leaderboardViewModel.loadRankings(groupId = groupId)
        
        if (isAdmin) {
            groupViewModel.loadGroupAdminData(groupId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedGroup?.name ?: stringResource(R.string.ui_group_detail)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                    }
                }
            )
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Grup Bilgi Kartı
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedGroup?.description ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // --- YÖNETİCİ PANELİ (Sadece Admin Görür) ---
            if (isAdmin) {
                // Bekleyen İstekler
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.ui_pending_requests), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    itemsIndexed(pendingRequests) { _, (id, request) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(request.userName.ifBlank { stringResource(R.string.ui_unknown_user) }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(onClick = { groupViewModel.respondToJoinRequest(id, request, false) }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.ui_reject), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { groupViewModel.respondToJoinRequest(id, request, true) }) {
                                        Icon(Icons.Default.Check, stringResource(R.string.ui_confirm), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Üye Yönetimi
                item {
                    Text(stringResource(R.string.ui_member_management), style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(firestoreMembers) { _, member ->
                    ListItem(
                        headlineContent = { Text(member.userName.ifBlank { stringResource(R.string.ui_unknown_user) }) },
                        supportingContent = { Text(member.role) },
                        trailingContent = {
                            if (member.userId != currentUid) { // Kendini çıkaramasın
                                val groupId = selectedGroup?.docId
                                if (groupId != null) {
                                    TextButton(onClick = { groupViewModel.removeMember(groupId, member.userId) }) {
                                        Text(stringResource(R.string.ui_remove), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    )
                }
                
                item { Divider(Modifier.padding(vertical = 8.dp)) }
            }

            // --- PERFORMANS SIRALAMASI ---
            item {
                Text(stringResource(R.string.ui_group_ranking_calories), style = MaterialTheme.typography.titleMedium)
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (rankings.isEmpty()) {
                item {
                    Text(stringResource(R.string.ui_no_activity_yet), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                itemsIndexed(rankings) { index, entry ->
                    LeaderboardItem(
                        rank = index + 1,
                        name = entry.fullName
                            .takeUnless { it.isBlank() || it.equals("Bilinmiyor", ignoreCase = true) || it.equals("Bilinmeyen", ignoreCase = true) || it.equals("Unknown", ignoreCase = true) }
                            ?: stringResource(R.string.ui_unknown_user),
                        value = "${entry.value.toInt()} kcal",
                        isMe = entry.isMe
                    )
                }
            }
        }
    }
}
