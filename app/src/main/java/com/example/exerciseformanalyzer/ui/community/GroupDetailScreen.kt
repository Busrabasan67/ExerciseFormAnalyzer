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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.example.exerciseformanalyzer.model.firestore.FsGroupMember
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser

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

    val snackbarHost = remember { SnackbarHostState() }
    val currentUid = viewModel.currentUid
    val isAdmin = viewModel.isAdmin(group)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (isAdmin) listOf("Bilgiler", "Üyeler", "Davet Gönder") else listOf("Bilgiler", "Üyeler")

    var inviteQuery by remember { mutableStateOf("") }

    LaunchedEffect(group?.groupId) {
        group?.groupId?.let { viewModel.loadGroupMembers(it) }
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is CommunityEvent.Success -> {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                // ── Bilgiler sekmesi ────────────────────────────────────────
                0 -> {
                    item {
                        GroupInfoCard(group = group)
                    }
                }

                // ── Üyeler sekmesi ──────────────────────────────────────────
                1 -> {
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
                                canRemove = isAdmin && member.userId != currentUid,
                                onRemove = {
                                    group?.let { viewModel.removeMember(it.groupId, member.userId) }
                                }
                            )
                        }
                    }
                }

                // ── Davet Gönder sekmesi (sadece admin) ─────────────────────
                2 -> {
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

// ─────────────────────────────────────────────────────────────────────────────
// Grup Bilgi Kartı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupInfoCard(group: FsGroup?) {
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

            // Rol badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (member.role == "admin")
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = if (member.role == "admin") "Yönetici" else "Üye",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (member.role == "admin")
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )
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
