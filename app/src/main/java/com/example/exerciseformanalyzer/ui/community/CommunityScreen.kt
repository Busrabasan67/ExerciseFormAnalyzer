package com.example.exerciseformanalyzer.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupInvite
import com.example.exerciseformanalyzer.model.firestore.FsGroupJoinRequest
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupDetail: (FsGroup) -> Unit
) {
    val event by viewModel.event.collectAsState()
    val myInvites by viewModel.myInvites.collectAsState()
    val incomingJoinRequests by viewModel.incomingJoinRequests.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var waitingCreateSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAll() }

    LaunchedEffect(selectedTab) {
        viewModel.loadAll()
        when (selectedTab) {
            2 -> viewModel.markInvitesSeen()
            3 -> viewModel.markIncomingJoinRequestsSeen()
        }
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is CommunityEvent.Success -> {
                snackbarHost.showSnackbar(e.message)
                if (waitingCreateSuccess) {
                    selectedTab = 0
                    showCreateDialog = false
                    waitingCreateSuccess = false
                }
                viewModel.resetEvent()
            }
            is CommunityEvent.GroupCreated -> {
                selectedTab = 0
                showCreateDialog = false
                waitingCreateSuccess = false
                viewModel.selectGroup(e.group)
                onNavigateToGroupDetail(e.group)
                viewModel.resetEvent()
            }
            is CommunityEvent.Error -> {
                snackbarHost.showSnackbar("Hata: ${e.message}")
                waitingCreateSuccess = false
                viewModel.resetEvent()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Topluluklar", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Gruplarım", "Keşfet", "Davetler", "Gelen").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                BadgedBox(badge = {
                                    val count = when (index) {
                                        2 -> myInvites.size
                                        3 -> incomingJoinRequests.size
                                        else -> 0
                                    }
                                    if (count > 0) Badge { Text(count.toString()) }
                                }) {
                                    Text(title, maxLines = 1)
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
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Grup Oluştur") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> MyGroupsTab(viewModel = viewModel, onNavigateToDetail = onNavigateToGroupDetail)
                1 -> ExploreTab(viewModel = viewModel, onNavigateToDetail = onNavigateToGroupDetail)
                2 -> MyInvitesTab(viewModel = viewModel)
                3 -> IncomingJoinRequestsTab(viewModel = viewModel)
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            isLoading = event is CommunityEvent.Loading,
            onDismiss = { showCreateDialog = false; viewModel.resetEvent() },
            onCreate = { name, desc, isPrivate ->
                waitingCreateSuccess = true
                viewModel.createGroup(name, desc, isPrivate)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme 0: Keşfet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExploreTab(
    viewModel: CommunityViewModel,
    onNavigateToDetail: (FsGroup) -> Unit
) {
    val groups by viewModel.exploreGroups.collectAsState()

    if (groups.isEmpty()) {
        EmptyState(message = "Keşfedilecek grup yok.")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups) { (group, status) ->
                ExploreGroupCard(
                    group = group,
                    userStatus = status,
                    onClick = { onNavigateToDetail(group) },
                    onJoin = { viewModel.joinPublicGroup(group) },
                    onSendRequest = { viewModel.sendJoinRequest(group) }
                )
            }
        }
    }
}

@Composable
private fun ExploreGroupCard(
    group: FsGroup,
    userStatus: String,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    onSendRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grup kapağı / ikonu
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        color = if (group.isPrivate)
                            Color(0xFF00A896)
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!group.coverImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.coverImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (group.isPrivate) Icons.Default.Lock else Icons.Default.Group,
                        contentDescription = null,
                        tint = if (group.isPrivate)
                            Color.White
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (group.description.isNotBlank()) {
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (group.isPrivate) "Kapalı Grup" else "Herkese Açık",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (group.isPrivate)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Buton durumu
            when (userStatus) {
                "member" -> Text(
                    text = "Üyesiniz",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                "pendingRequest" -> Text(
                    text = "İstek gönderildi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                "pendingInvite" -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "Davet Var",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    if (group.isPrivate) {
                        Button(
                            onClick = onSendRequest,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("İstek", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = onJoin,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Katıl", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme 1: Gruplarım
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyGroupsTab(
    viewModel: CommunityViewModel,
    onNavigateToDetail: (FsGroup) -> Unit
) {
    val myGroups by viewModel.myGroups.collectAsState()
    val unreadGroupIds by viewModel.unreadGroupIds.collectAsState()

    if (myGroups.isEmpty()) {
        EmptyState(message = "Henüz bir gruba dahil değilsiniz.\nYeni bir grup oluşturun veya bir gruba katılın.")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(myGroups) { group ->
                MyGroupCard(
                    group = group,
                    isAdmin = viewModel.isAdmin(group),
                    hasNotification = group.groupId in unreadGroupIds,
                    onClick = { onNavigateToDetail(group) }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MyGroupCard(
    group: FsGroup,
    isAdmin: Boolean,
    hasNotification: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (hasNotification) {
                        Badge()
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (group.isPrivate) Color(0xFF5E60CE) else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!group.coverImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = group.coverImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = if (group.isPrivate) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (group.isPrivate) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF5E60CE),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (group.description.isNotBlank()) {
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isAdmin) {
                    Text(
                        text = "Yönetici",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme 2: Davetlerim — Admin'den gelen davetler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MyInvitesTab(viewModel: CommunityViewModel) {
    val invites by viewModel.myInvites.collectAsState()

    if (invites.isEmpty()) {
        EmptyState(
            message = "Bekleyen davetiniz yok.",
            icon = Icons.Default.MailOutline
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(invites) { invite ->
                InviteCard(
                    invite = invite,
                    onAccept = { viewModel.acceptGroupInvite(invite) },
                    onReject = { viewModel.rejectGroupInvite(invite) }
                )
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: FsGroupInvite,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MailOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = invite.groupName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${invite.fromUserName} sizi bu gruba davet etti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onReject) { Text("Reddet") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept) { Text("Kabul Et") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sekme 3: Gelen İstekler — Kullanıcıların admin'e gönderdiği istekler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncomingJoinRequestsTab(viewModel: CommunityViewModel) {
    val requests by viewModel.incomingJoinRequests.collectAsState()

    if (requests.isEmpty()) {
        EmptyState(
            message = "Yönettiğiniz gizli gruplar için bekleyen katılma isteği yok.",
            icon = Icons.Default.People
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(requests) { request ->
                JoinRequestCard(
                    request = request,
                    onAccept = { viewModel.acceptJoinRequest(request) },
                    onReject = { viewModel.rejectJoinRequest(request) }
                )
            }
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: FsGroupJoinRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = request.groupName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = request.fromUserName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = request.fromUserName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (request.fromUserEmail.isNotBlank()) {
                        Text(
                            text = request.fromUserEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reddet") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept) { Text("Kabul Et") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grup oluşturma dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreateGroupDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, isPrivate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Yeni Grup Oluştur",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Grup Adı *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Gizli Grup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (isPrivate) "Katılmak için yönetici onayı gerekir." else "Herkes direkt katılabilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("İptal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name, description, isPrivate) },
                        enabled = !isLoading && name.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Oluştur")
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Boş durum
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Group
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
