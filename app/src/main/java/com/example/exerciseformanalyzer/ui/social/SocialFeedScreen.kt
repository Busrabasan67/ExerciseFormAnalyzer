package com.example.exerciseformanalyzer.ui.social

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.ui.dashboard.PatientViewModel
import com.example.exerciseformanalyzer.model.firestore.FirestoreActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    viewModel: PatientViewModel,
    onNavigateBack: () -> Unit
) {
    val activities by viewModel.activities.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDynamicSocialData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_activity_feed)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                    }
                }
            )
        }
    ) { paddingVals ->
        if (activities.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingVals), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_no_activity), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activities) { activity ->
                    ActivityCard(activity)
                }
            }
        }
    }
}

@Composable
fun ActivityCard(activity: FirestoreActivity) {
    var likes by remember { mutableIntStateOf(activity.likeCount) }
    var isLiked by remember { mutableStateOf(false) }
    val userName = activity.userName
        .takeUnless { it.isBlank() || it.equals("Bilinmiyor", ignoreCase = true) || it.equals("Bilinmeyen", ignoreCase = true) || it.equals("Unknown", ignoreCase = true) }
        ?: stringResource(R.string.ui_unknown_user)
    val workoutCompletedText = "antrenmanını tamamladı"
    val activityDescription = if (activity.activityType == "WORKOUT") {
        val exerciseName = activity.description.substringBefore(" $workoutCompletedText").trim()
        if (exerciseName.isBlank() || exerciseName.contains("Algılanıyor", ignoreCase = true) || exerciseName.contains("Detecting", ignoreCase = true)) {
            stringResource(R.string.ui_workout_completed_generic)
        } else if (activity.description.contains(workoutCompletedText, ignoreCase = true)) {
            stringResource(R.string.ui_workout_completed_activity, exerciseName)
        } else {
            activity.description.ifBlank { stringResource(R.string.ui_workout_completed_generic) }
        }
    } else {
        activity.description
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar Placeholder
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(userName.firstOrNull()?.toString() ?: "?")
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(userName, style = MaterialTheme.typography.titleMedium)
                    Text(activityDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats display
            val statsText = activity.statistics.entries.joinToString(" • ") { "${it.value}" }
            Text(statsText, style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).alpha(0.5f), thickness = 0.5.dp)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { 
                    isLiked = !isLiked
                    if (isLiked) likes++ else likes--
                }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$likes")
                }
                
                TextButton(onClick = { /* Comment Logic */ }) {
                    Icon(imageVector = Icons.Default.Comment, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${activity.commentCount}")
                }
                
                IconButton(onClick = { /* Ping/Motivation Logic */ }) {
                    Icon(imageVector = Icons.Default.EmojiEmotions, contentDescription = stringResource(R.string.ui_motivate), tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
