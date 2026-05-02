package com.example.exerciseformanalyzer.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.model.firestore.FsGroup
import com.example.exerciseformanalyzer.model.firestore.FsGroupMessage
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FsGroupProgram
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import com.example.exerciseformanalyzer.ui.dashboard.components.AssignTaskDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: CommunityViewModel,
    onNavigateBack: () -> Unit
) {
    val group by viewModel.selectedGroup.collectAsState()
    val members by viewModel.groupMembers.collectAsState()
    val event by viewModel.event.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val messages by viewModel.groupMessages.collectAsState()
    val programs by viewModel.groupPrograms.collectAsState()
    val appliedProgramIds by viewModel.appliedProgramIds.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    val currentUid = viewModel.currentUid
    val canInvite = viewModel.canInvite()
    val canManageRoles = viewModel.canManageRoles()
    val canShareProgram = viewModel.canShareProgram()
    val canDeleteChatContent = viewModel.canDeleteChatContent()
    val isCurrentMember = viewModel.isCurrentMember()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = when {
        canInvite -> listOf("Sohbet", "Bilgiler", "Üyeler", "Davet")
        isCurrentMember -> listOf("Sohbet", "Bilgiler", "Üyeler")
        else -> listOf("Bilgiler", "Üyeler")
    }

    var inviteQuery by remember { mutableStateOf("") }
    var showProgramDialog by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<FsGroupMember?>(null) }
    var showCloseGroupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(group?.groupId) {
        group?.groupId?.let { viewModel.loadGroupMembers(it) }
    }

    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) selectedTab = 0
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is CommunityEvent.Success -> {
                if (e.message == "Grup kapatıldı.") {
                    viewModel.resetEvent()
                    onNavigateBack()
                    return@LaunchedEffect
                }
                snackbarHost.showSnackbar(e.message)
                viewModel.resetEvent()
            }
            is CommunityEvent.Error -> {
                snackbarHost.showSnackbar("Hata: ${e.message}")
                viewModel.resetEvent()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(group?.name ?: "Grup Detayı", fontWeight = FontWeight.Bold) },
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
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (tabs[selectedTab] == "Sohbet") {
            ChatSectionFixed(
                messages = messages,
                programs = programs,
                appliedProgramIds = appliedProgramIds,
                canShareProgram = canShareProgram,
                canDelete = canDeleteChatContent,
                onSendMessage = { viewModel.sendTextMessage(it) },
                onShareProgram = { showProgramDialog = true },
                onApplyProgram = { viewModel.applyProgram(it) },
                onDeleteMessage = { viewModel.deleteMessage(it) },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tabs[selectedTab]) {
                // ── Bilgiler sekmesi ────────────────────────────────────────
                "Bilgiler" -> {
                    item {
                        GroupInfoCard(
                            group = group,
                            canCloseGroup = canManageRoles,
                            onCloseGroup = { showCloseGroupDialog = true }
                        )
                    }
                }

                // ── Üyeler sekmesi ──────────────────────────────────────────
                "Üyeler" -> {
                    if (members.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Üye bulunamadı.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        items(members) { member ->
                            MemberCard(
                                member = member,
                                canRemove = canManageRoles && member.userId != currentUid && member.role != "admin",
                                canManageRole = canManageRoles && member.userId != currentUid && member.role != "admin",
                                onRoleChange = { newRole ->
                                    group?.let { viewModel.updateMemberRole(it.groupId, member.userId, newRole) }
                                },
                                onRemove = {
                                    memberPendingRemoval = member
                                }
                            )
                        }
                    }
                }

                // ── Davet Gönder sekmesi (sadece admin) ─────────────────────
                "Sohbet" -> {
                    item {
                        ChatSection(
                            messages = messages,
                            programs = programs,
                            appliedProgramIds = appliedProgramIds,
                            canShareProgram = canShareProgram,
                            canDelete = canDeleteChatContent,
                            onSendMessage = { viewModel.sendTextMessage(it) },
                            onShareProgram = { showProgramDialog = true },
                            onApplyProgram = { viewModel.applyProgram(it) },
                            onDeleteMessage = { viewModel.deleteMessage(it) }
                        )
                    }
                }

                "Davet" -> {
                    item {
                        InviteSection(
                            query = inviteQuery,
                            onQueryChange = { q ->
                                inviteQuery = q
                                viewModel.searchUsers(q)
                            },
                            searchResults = searchResults,
                            isLoading = event is CommunityEvent.Loading,
                            onInvite = { targetUser ->
                                group?.let { viewModel.sendGroupInvite(it, targetUser) }
                                inviteQuery = ""
                                viewModel.clearSearch()
                            },
                            onClearSearch = {
                                inviteQuery = ""
                                viewModel.clearSearch()
                            }
                        )
                    }
                }
            }
        }
    }

    memberPendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text("Üyeyi Çıkar") },
            text = {
                Text("\"${member.userName}\" adlı kullanıcıyı çıkarmak istediğinize emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        group?.let { viewModel.removeMember(it.groupId, member.userId) }
                        memberPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Çıkar")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showCloseGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCloseGroupDialog = false },
            title = { Text("Grubu Kapat") },
            text = {
                Text("Grubu kapatmak istediğinize emin misiniz? Bu işlem gruba ait üyeleri, davetleri, istekleri, mesajları ve programları kalıcı olarak siler.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        group?.let { viewModel.closeGroup(it.groupId) }
                        showCloseGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kapat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseGroupDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showProgramDialog) {
        AssignTaskDialog(
            onDismissRequest = { showProgramDialog = false },
            dialogTitle = "Grup Programı Paylaş",
            defaultTitle = "Grup Programı",
            submitText = "Programı Paylaş",
            onAssignTask = { title, note, _, exercises, sched, days, auto, weeks ->
                viewModel.shareProgram(title, note, exercises, sched, days, auto, weeks)
                showProgramDialog = false
            }
        )
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// Grup Bilgi Kartı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupInfoCard(
    group: FsGroup?,
    canCloseGroup: Boolean,
    onCloseGroup: () -> Unit
) {
    if (group == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (group.isPrivate) Icons.Default.Lock else Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (group.isPrivate) "🔒 Gizli Grup" else "🌐 Herkese Açık",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (group.isPrivate)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (group.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (group.creatorName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Yönetici: ${group.creatorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (canCloseGroup) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCloseGroup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Grubu Kapat")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Üye Kartı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberCard(
    member: FsGroupMember,
    canRemove: Boolean,
    canManageRole: Boolean,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (member.role == "admin")
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.userName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (member.role == "admin")
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.userName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (member.userEmail.isNotBlank()) {
                    Text(
                        text = member.userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = roleColor(member.role).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = roleLabel(member.role),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColor(member.role)
                    )
                }

                if (canManageRole) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { onRoleChange(if (member.role == "moderator") "member" else "moderator") },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (member.role == "moderator") "Üye yap" else "Yetkili yap",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (canRemove) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Çıkar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Davet Bölümü — Admin tarafı, e-posta ile kullanıcı ara + davet gönder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatSection(
    messages: List<FsGroupMessage>,
    programs: List<FsGroupProgram>,
    appliedProgramIds: Set<String>,
    canShareProgram: Boolean,
    canDelete: Boolean,
    onSendMessage: (String) -> Unit,
    onShareProgram: () -> Unit,
    onApplyProgram: (FsGroupProgram) -> Unit,
    onDeleteMessage: (FsGroupMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val programMap = remember(programs) { programs.associateBy { it.programId } }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sohbet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (canShareProgram) {
                OutlinedButton(onClick = onShareProgram) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Program Paylaş")
                }
            }
        }

        if (messages.isEmpty()) {
            Text(
                text = "Henüz mesaj yok.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        } else {
            messages.forEach { message ->
                val program = programMap[message.programId]
                if (message.type == "program" && program != null) {
                    ProgramMessageCard(
                        message = message,
                        program = program,
                        applied = appliedProgramIds.contains(program.programId),
                        canDelete = canDelete,
                        onApply = { onApplyProgram(program) },
                        onDelete = { onDeleteMessage(message) }
                    )
                } else {
                    TextMessageCard(
                        message = message,
                        canDelete = canDelete,
                        onDelete = { onDeleteMessage(message) }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Mesaj yaz") },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSendMessage(messageText)
                    messageText = ""
                },
                enabled = messageText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChatSectionFixed(
    messages: List<FsGroupMessage>,
    programs: List<FsGroupProgram>,
    appliedProgramIds: Set<String>,
    canShareProgram: Boolean,
    canDelete: Boolean,
    onSendMessage: (String) -> Unit,
    onShareProgram: () -> Unit,
    onApplyProgram: (FsGroupProgram) -> Unit,
    onDeleteMessage: (FsGroupMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val programMap = remember(programs) { programs.associateBy { it.programId } }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sohbet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (canShareProgram) {
                OutlinedButton(onClick = onShareProgram) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Program Paylaş")
                }
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz mesaj yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    val program = programMap[message.programId]
                    if (message.type == "program" && program != null) {
                        ProgramMessageCard(
                            message = message,
                            program = program,
                            applied = appliedProgramIds.contains(program.programId),
                            canDelete = canDelete,
                            onApply = { onApplyProgram(program) },
                            onDelete = { onDeleteMessage(message) }
                        )
                    } else {
                        TextMessageCard(
                            message = message,
                            canDelete = canDelete,
                            onDelete = { onDeleteMessage(message) }
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Mesaj yaz") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    minLines = 1,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSendMessage(messageText)
                        messageText = ""
                    },
                    enabled = messageText.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TextMessageCard(
    message: FsGroupMessage,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.senderName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(roleLabel(message.senderRole), style = MaterialTheme.typography.labelSmall, color = roleColor(message.senderRole))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ProgramMessageCard(
    message: FsGroupMessage,
    program: FsGroupProgram,
    applied: Boolean,
    canDelete: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(program.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${message.senderName} paylaştı",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (program.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(program.note, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${program.exercises.size} egzersiz • ${program.scheduleType}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onApply,
                enabled = !applied,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (applied) "Program Uygulandı" else "Programı Uygula")
            }
        }
    }
}

@Composable
private fun roleColor(role: String) = when (role) {
    "admin" -> MaterialTheme.colorScheme.tertiary
    "moderator" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "Yönetici"
    "moderator" -> "Yetkili"
    else -> "Üye"
}

@Composable
private fun InviteSection(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<FirestoreUser>,
    isLoading: Boolean,
    onInvite: (FirestoreUser) -> Unit,
    onClearSearch: () -> Unit
) {
    Column {
        Text(
            text = "E-posta ile Kullanıcı Davet Et",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("E-posta ara (min. 2 karakter)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Close, contentDescription = "Temizle")
                    }
                }
            }
        )

        if (isLoading && query.length >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        }

        if (searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column {
                    searchResults.forEachIndexed { index, user ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.fullName.ifBlank { "İsimsiz Kullanıcı" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            TextButton(onClick = { onInvite(user) }) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Davet Et")
                            }
                        }
                    }
                }
            }
        } else if (query.length >= 2 && !isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Kullanıcı bulunamadı.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
