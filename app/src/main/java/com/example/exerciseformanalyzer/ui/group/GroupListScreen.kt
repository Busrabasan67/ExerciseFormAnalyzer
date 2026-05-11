package com.example.exerciseformanalyzer.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroup
import com.example.exerciseformanalyzer.model.firestore.FirestoreGroupInvite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    viewModel: GroupViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String, String, String, String) -> Unit
) {
    val myGroups by viewModel.observeMyGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val exploreGroups by viewModel.exploreGroups.collectAsStateWithLifecycle()
    val myInvites by viewModel.myInvites.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.ui_my_groups), stringResource(R.string.ui_discover), stringResource(R.string.ui_invitations))

    var showCreateDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // groupId to groupName

    // Verileri ilk açılışta ve sekme değişiminde yükle
    LaunchedEffect(selectedTab) {
        viewModel.loadDiscoveryData()
    }

    LaunchedEffect(uiState) {
        if (uiState is GroupUiState.Success) {
            showCreateDialog = false
            showInviteDialog = null
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.groups_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                BadgedBox(badge = {
                                    if (index == 2 && myInvites.isNotEmpty()) {
                                        Badge { Text(myInvites.size.toString()) }
                                    }
                                }) {
                                    Text(title) 
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.create_group)) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> MyGroupsTab(
                    groups = myGroups,
                    onLeave = { id -> viewModel.leaveGroup(id) },
                    onInvite = { id, name -> showInviteDialog = id to name },
                    onNavigateToDetail = onNavigateToDetail
                )
                1 -> ExploreTab(
                    groups = exploreGroups,
                    myGroupIds = myGroups.mapNotNull { it.firebaseDocId },
                    onJoinRequest = { id, name, creatorId -> viewModel.requestToJoinGroup(id, name, creatorId) },
                    onNavigateToDetail = onNavigateToDetail
                )
                2 -> InvitesTab(
                    invites = myInvites,
                    onRespond = { id, invite, accept -> viewModel.respondToInvite(id, invite, accept) }
                )
            }
        }
    }

    // Dialoglar
    if (showCreateDialog) {
        CreateGroupDialog(
            isLoading = uiState is GroupUiState.Loading,
            errorMessage = (uiState as? GroupUiState.Error)?.message,
            onDismiss = {
                showCreateDialog = false
                viewModel.resetState()
            },
            onCreate = { name, description, isPrivate ->
                viewModel.createGroup(name, description, isPrivate)
            }
        )
    }

    showInviteDialog?.let { (groupId, groupName) ->
        InviteUserDialog(
            groupName = groupName,
            isLoading = uiState is GroupUiState.Loading,
            errorMessage = (uiState as? GroupUiState.Error)?.message,
            onDismiss = {
                showInviteDialog = null
                viewModel.resetState()
            },
            onInvite = { email ->
                viewModel.inviteUserToGroup(groupId, groupName, email)
            }
        )
    }
}

@Composable
private fun MyGroupsTab(
    groups: List<GroupEntity>,
    onLeave: (String) -> Unit,
    onInvite: (String, String) -> Unit,
    onNavigateToDetail: (String, String, String, String) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState(Icons.Default.People, stringResource(R.string.ui_no_group_yet))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups) { group ->
                GroupCard(
                    groupName = group.name,
                    description = group.description ?: "",
                    isPrivate = group.isPrivate,
                    onClick = { group.firebaseDocId?.let { onNavigateToDetail(it, group.name, group.description ?: "", group.creatorUid) } },
                    actionContent = {
                        Row {
                            IconButton(onClick = { group.firebaseDocId?.let { onInvite(it, group.name) } }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Davet Et", tint = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { group.firebaseDocId?.let { onLeave(it) } }) {
                                Text(stringResource(R.string.leave_group), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ExploreTab(
    groups: List<Pair<String, FirestoreGroup>>,
    myGroupIds: List<String>,
    onJoinRequest: (String, String, String) -> Unit,
    onNavigateToDetail: (String, String, String, String) -> Unit
) {
    // Keşfet sekmesinde görünecekler:
    // 1. Herkese açık (Public) gruplar
    // 2. Kendi dahil olduğumuz (özel veya genel) tüm gruplar (durumu görmek için)
    val displayGroups = groups.filter { (id, group) ->
        !group.isPrivate || id in myGroupIds
    }

    if (displayGroups.isEmpty()) {
        EmptyState(Icons.Default.Search, stringResource(R.string.ui_no_new_groups_discover))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(displayGroups) { (id, group) ->
                val isMember = id in myGroupIds
                GroupCard(
                    groupName = group.name,
                    description = group.description,
                    isPrivate = group.isPrivate,
                    onClick = { onNavigateToDetail(id, group.name, group.description, group.creatorId) },
                    actionContent = {
                        if (isMember) {
                            Text(stringResource(R.string.ui_you_are_member), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else if (group.isPrivate) {
                            Text("Sadece Davet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            Button(onClick = { onJoinRequest(id, group.name, group.creatorId) }) {
                                Text(stringResource(R.string.ui_join_request))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InvitesTab(
    invites: List<Pair<String, FirestoreGroupInvite>>,
    onRespond: (String, FirestoreGroupInvite, Boolean) -> Unit
) {
    if (invites.isEmpty()) {
        EmptyState(Icons.Default.Mail, stringResource(R.string.ui_no_pending_invites))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(invites) { (id, invite) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(invite.groupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val inviterName = invite.fromUserName.ifBlank { stringResource(R.string.ui_unknown_user) }
                        Text("$inviterName ${stringResource(R.string.ui_invited_you_to_group)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { onRespond(id, invite, false) }) {
                                Text(stringResource(R.string.ui_reject))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onRespond(id, invite, true) }) {
                                Text(stringResource(R.string.ui_accept))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    groupName: String,
    description: String,
    isPrivate: Boolean,
    onClick: () -> Unit = {},
    actionContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = groupName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }
                if (isPrivate) {
                    Text(
                        text = stringResource(R.string.ui_private_group),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            actionContent()
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, isPrivate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.group_desc_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text(stringResource(R.string.group_private_label))
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, description, isPrivate) }, enabled = !isLoading && name.isNotBlank()) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.create_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun InviteUserDialog(
    groupName: String,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInvite: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_invite_person)) },
        text = {
            Column {
                Text(stringResource(R.string.ui_invite_instruction, groupName), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.ui_email_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onInvite(email) }, enabled = !isLoading && email.isNotBlank()) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.ui_invite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}
