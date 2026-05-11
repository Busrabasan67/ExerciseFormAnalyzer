package com.example.exerciseformanalyzer.ui.community

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
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
import androidx.compose.material3.Switch
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.PhotoCamera

import com.example.exerciseformanalyzer.util.ImageUtils
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.ui.graphics.Color

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
    val context = LocalContext.current
    val currentUid = viewModel.currentUid
    val canInvite = viewModel.canInvite()
    val canManageRoles = viewModel.canManageRoles()
    val canShareProgram = viewModel.canShareProgram()
    val canDeleteChatContent = viewModel.canDeleteChatContent()
    val isCurrentMember = viewModel.isCurrentMember()
    val canLeaveGroup = isCurrentMember && viewModel.currentMemberRole() != "admin"

    var selectedTab by remember { mutableIntStateOf(0) }
    val labelMembers = stringResource(R.string.ui_members)
    val labelGroupClosed = stringResource(R.string.ui_group_closed)
    val labelLeftGroup = stringResource(R.string.ui_left_group)
    val tabs = when {
        canInvite -> listOf(stringResource(R.string.ui_chat), stringResource(R.string.ui_info), labelMembers, stringResource(R.string.ui_invite))
        isCurrentMember -> listOf(stringResource(R.string.ui_chat), stringResource(R.string.ui_info), labelMembers)
        else -> listOf(stringResource(R.string.ui_info), labelMembers)
    }

    var inviteQuery by remember { mutableStateOf("") }
    var showProgramDialog by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<FsGroupMember?>(null) }
    var showCloseGroupDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(group?.groupId) {
        group?.groupId?.let { viewModel.loadGroupMembers(it) }
    }

    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) selectedTab = 0
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is CommunityEvent.Success -> {
                if (e.message == labelGroupClosed || e.message == labelLeftGroup) {
                    viewModel.resetEvent()
                    onNavigateBack()
                    return@LaunchedEffect
                }
                snackbarHost.showSnackbar(e.message)
                viewModel.resetEvent()
            }
            is CommunityEvent.Error -> {
                snackbarHost.showSnackbar(context.getString(R.string.ui_error_with_message, e.message))
                viewModel.resetEvent()
            }
            else -> {}
        }
    }

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun handleImageUpload(uri: android.net.Uri) {
        scope.launch {
            val bytes = ImageUtils.processImage(context, uri)
            if (bytes != null) {
                group?.groupId?.let { gid -> viewModel.uploadGroupCoverPhoto(gid, bytes) }
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { handleImageUpload(it) } }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempPhotoUri?.let { handleImageUpload(it) }
            }
        }
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(group?.name ?: stringResource(R.string.ui_group_detail), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.ui_back))
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
        if (tabs[selectedTab] == "Chat") {
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
                    "Info" -> {
                        item {
                            GroupInfoCard(
                                group = group,
                                canCloseGroup = canManageRoles,
                                canLeaveGroup = canLeaveGroup,
                                canUpdatePrivacy = canManageRoles,
                                canUploadPhoto = canManageRoles || (group?.allowMemberPhotoUpload == true && isCurrentMember),
                                onPrivacyChange = { isPrivate ->
                                    group?.let { viewModel.updateGroupPrivacy(it.groupId, isPrivate) }
                                },
                                onUploadPhoto = { showImageSourceDialog = true },
                                onUpdateMemberUploadPermission = { allowed ->
                                    group?.let { viewModel.updateGroupMemberUploadPermission(it.groupId, allowed) }
                                },
                                onLeaveGroup = { showLeaveGroupDialog = true },
                                onCloseGroup = { showCloseGroupDialog = true }
                            )
                        }
                    }
                    labelMembers -> {
                        if (members.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_member_not_found),
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
                                    onPromoteToAdmin = {
                                        group?.let { viewModel.updateMemberRole(it.groupId, member.userId, "admin") }
                                    },
                                    onRemove = {
                                        memberPendingRemoval = member
                                    }
                                )
                            }
                        }
                    }
                    "Invite" -> {
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
    }

    // Dialogs
    if (showProgramDialog) {
        AssignTaskDialog(
            onDismissRequest = { showProgramDialog = false },
            dialogTitle = stringResource(R.string.ui_share_group_program),
            submitText = stringResource(R.string.ui_share_in_chat),
            onAssignTask = { title, note, _, exercises, sched, days, auto, weeks ->
                viewModel.shareProgram(title, note, exercises, sched, days, auto, weeks)
                showProgramDialog = false
            }
        )
    }

    memberPendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text(stringResource(R.string.ui_remove_member)) },
            text = {
                Text(stringResource(R.string.ui_remove_member_confirm, member.userName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        group?.let { viewModel.removeMember(it.groupId, member.userId) }
                        memberPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
    if (showCloseGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCloseGroupDialog = false },
            title = { Text(stringResource(R.string.ui_close_group)) },
            text = {
                Text(stringResource(R.string.ui_close_group_confirm))
            },
            confirmButton = {
                Button(
                    onClick = {
                        group?.let { viewModel.closeGroup(it.groupId) }
                        showCloseGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseGroupDialog = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
    if (showLeaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupDialog = false },
            title = { Text(stringResource(R.string.ui_leave_group)) },
            text = { Text(stringResource(R.string.ui_leave_group_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        group?.let { viewModel.leaveGroup(it.groupId) }
                        showLeaveGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                     Text(stringResource(R.string.ui_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupDialog = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
    if (showImageSourceDialog) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceDialog = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = android.graphics.Color.BLACK.let { androidx.compose.ui.graphics.Color(it).copy(alpha = 0.45f) },
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.ui_group_photo),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.ui_group_cover_update_msg),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    textAlign = TextAlign.Center
                )
                Surface(
                    onClick = {
                        showImageSourceDialog = false
                        val uri = ImageUtils.createTempUri(context, "group_update_")
                        tempPhotoUri = uri
                        cameraLauncher.launch(uri)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                         Text(
                            text = stringResource(R.string.camera),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Surface(
                    onClick = {
                        showImageSourceDialog = false
                        pickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                         Text(
                            text = stringResource(R.string.gallery),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = { showImageSourceDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.ui_cancel),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
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
    canLeaveGroup: Boolean,
    canUpdatePrivacy: Boolean,
    canUploadPhoto: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    onUploadPhoto: () -> Unit,
    onUpdateMemberUploadPermission: (Boolean) -> Unit,
    onLeaveGroup: () -> Unit,
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
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!group.coverImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.coverImageUrl,
                        contentDescription = stringResource(R.string.ui_group_cover),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                }
                
                if (canUploadPhoto) {
                    IconButton(
                        onClick = onUploadPhoto,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.ui_change_cover), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (group.isPrivate) stringResource(R.string.ui_private_group) else stringResource(R.string.ui_public),
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
                        text = stringResource(R.string.ui_created_by, group.creatorName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (canUpdatePrivacy) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_private_group),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (group.isPrivate) {
                                    stringResource(R.string.ui_requires_admin_approval)
                                } else {
                                    stringResource(R.string.ui_anyone_can_join)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = group.isPrivate,
                            onCheckedChange = onPrivacyChange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_members_can_upload_photo),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (group.allowMemberPhotoUpload) {
                                    stringResource(R.string.ui_members_can_change_cover)
                                } else {
                                    stringResource(R.string.ui_only_admin_can_change_photo)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = group.allowMemberPhotoUpload,
                            onCheckedChange = onUpdateMemberUploadPermission
                        )
                    }
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
                        Text(stringResource(R.string.ui_close_group))
                    }
                }

                if (canLeaveGroup) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLeaveGroup,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.ui_leave_group))
                    }
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
    onPromoteToAdmin: () -> Unit,
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
                            if (member.role == "moderator") stringResource(R.string.ui_make_member) else stringResource(R.string.ui_make_moderator),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(
                        onClick = onPromoteToAdmin,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            stringResource(R.string.ui_make_admin_low),
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
                        contentDescription = stringResource(R.string.ui_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat Section
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
            Text(stringResource(R.string.ui_chat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (canShareProgram) {
                OutlinedButton(onClick = onShareProgram) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_share_program))
                }
            }
        }

        if (messages.isEmpty()) {
            Text(
                text = stringResource(R.string.ui_no_messages),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChatMessageTimeline(
                    messages = messages,
                    programMap = programMap,
                    appliedProgramIds = appliedProgramIds,
                    canDelete = canDelete,
                    onApplyProgram = onApplyProgram,
                    onDeleteMessage = onDeleteMessage
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text(stringResource(R.string.ui_write_message)) },
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val timelineItems = remember(messages) { buildChatTimeline(messages) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ui_chat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (canShareProgram) {
                OutlinedButton(onClick = onShareProgram) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_share_program))
                }
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ui_no_messages),
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
                items(timelineItems, key = { item ->
                    when (item) {
                        is ChatTimelineItem.DateHeader -> "date_${item.dateKey}"
                        is ChatTimelineItem.Message -> item.message.messageId.ifBlank { "message_${item.message.createdAt}" }
                    }
                }) { item ->
                    when (item) {
                        is ChatTimelineItem.DateHeader -> ChatDateHeader(item.label)
                        is ChatTimelineItem.Message -> {
                            val message = item.message
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
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text(stringResource(R.string.ui_write_message)) },
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
}

private sealed class ChatTimelineItem {
    data class DateHeader(val dateKey: String, val label: ChatDateLabel) : ChatTimelineItem()
    data class Message(val message: FsGroupMessage) : ChatTimelineItem()
}

private sealed class ChatDateLabel {
    object Today : ChatDateLabel()
    object Yesterday : ChatDateLabel()
    data class Other(val label: String) : ChatDateLabel()
}

@Composable
private fun ChatMessageTimeline(
    messages: List<FsGroupMessage>,
    programMap: Map<String, FsGroupProgram>,
    appliedProgramIds: Set<String>,
    canDelete: Boolean,
    onApplyProgram: (FsGroupProgram) -> Unit,
    onDeleteMessage: (FsGroupMessage) -> Unit
) {
    val timelineItems = remember(messages) { buildChatTimeline(messages) }
    timelineItems.forEach { item ->
        when (item) {
            is ChatTimelineItem.DateHeader -> ChatDateHeader(item.label)
            is ChatTimelineItem.Message -> {
                val message = item.message
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
}


@Composable
private fun ChatDateHeader(labelItem: ChatDateLabel) {
    val label = when(labelItem) {
        ChatDateLabel.Today -> stringResource(R.string.ui_today)
        ChatDateLabel.Yesterday -> stringResource(R.string.ui_yesterday)
        is ChatDateLabel.Other -> labelItem.label
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun buildChatTimeline(messages: List<FsGroupMessage>): List<ChatTimelineItem> {
    val items = mutableListOf<ChatTimelineItem>()
    var lastDateKey: String? = null
    messages.sortedBy { it.createdAt }.forEach { message ->
        val dateKey = chatDateKey(message.createdAt)
        if (dateKey != lastDateKey) {
            items += ChatTimelineItem.DateHeader(dateKey, chatDateLabelItem(message.createdAt))
            lastDateKey = dateKey
        }
        items += ChatTimelineItem.Message(message)
    }
    return items
}

private fun chatDateKey(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp.coerceAtLeast(0L) }
    return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
}

private fun chatDateLabelItem(timestamp: Long): ChatDateLabel {
    val locale = Locale.getDefault()
    val messageDate = Calendar.getInstance(locale).apply { timeInMillis = timestamp.coerceAtLeast(0L) }
    val today = Calendar.getInstance(locale)
    val yesterday = Calendar.getInstance(locale).apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(messageDate, today) -> ChatDateLabel.Today
        isSameDay(messageDate, yesterday) -> ChatDateLabel.Yesterday
        messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            messageDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) -> {
            ChatDateLabel.Other(SimpleDateFormat("EEEE", locale).format(messageDate.time).capitalize(locale))
        }
        else -> {
            ChatDateLabel.Other(SimpleDateFormat("d MMMM EEEE", locale).format(messageDate.time).capitalize(locale))
        }
    }
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

private fun String.capitalize(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private fun chatTimeLabel(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.coerceAtLeast(0L)))

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
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        chatTimeLabel(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error)
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
                        "${message.senderName} ${stringResource(R.string.ui_shared)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Text(
                    chatTimeLabel(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                if (canDelete) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (program.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(program.note, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_exercises_bullet_schedule, program.exercises.size, program.scheduleType),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onApply,
                enabled = !applied,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (applied) stringResource(R.string.ui_program_applied) else stringResource(R.string.ui_apply_program))
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

@Composable
private fun roleLabel(role: String): String = when (role) {
    "admin" -> stringResource(R.string.ui_role_admin)
    "moderator" -> stringResource(R.string.ui_role_moderator)
    else -> stringResource(R.string.ui_role_member)
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
            text = stringResource(R.string.ui_invite_by_email),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.ui_search_email_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear))
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
                                    text = user.fullName.ifBlank { stringResource(R.string.ui_anonymous_user) },
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
                                Text(stringResource(R.string.ui_invite))
                            }
                        }
                    }
                }
            }
        } else if (query.length >= 2 && !isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ui_user_not_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
