package com.example.exerciseformanalyzer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.model.ExerciseType

/**
 * Hareket seçim ekranı — kamera AÇILMADAN önce gösterilir.
 * Kullanıcı hareket seçtikten sonra kamera ekranına geçilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    onExerciseSelected: (ExerciseType?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val exercises: List<Pair<ExerciseType, Int>> = listOf(
        Pair(ExerciseType.SQUAT, R.string.ex_squat),
        Pair(ExerciseType.PUSH_UP, R.string.ex_pushup),
        Pair(ExerciseType.SIT_UP, R.string.ex_sit_up),
        Pair(ExerciseType.DUMBBELL_ROW, R.string.ex_dumbbell_row),
        Pair(ExerciseType.BICEPS_CURL, R.string.ex_biceps_curl),
        Pair(ExerciseType.SHOULDER_PRESS, R.string.ex_shoulder_press),
        Pair(ExerciseType.PLANK, R.string.ex_plank),
        Pair(ExerciseType.HAMMER_CURL, R.string.ex_hammer_curl),
        Pair(ExerciseType.LATERAL_RAISE, R.string.ex_lateral_raise),
        Pair(ExerciseType.TRICEPS_EXTENSION, R.string.ex_triceps_extension),
        Pair(ExerciseType.TRICEPS_KICKBACK, R.string.ex_triceps_kickback),
        Pair(ExerciseType.BENT_OVER_ROW, R.string.ex_bent_over_row),
        Pair(ExerciseType.BENT_OVER_RAISE, R.string.ex_bent_over_raise),
        Pair(ExerciseType.MOUNTAIN_CLIMBER, R.string.ex_mountain_climber),
        Pair(ExerciseType.RUSSIAN_TWIST, R.string.ex_russian_twist),
        Pair(ExerciseType.HEEL_TAP, R.string.ex_heel_tap),
        Pair(ExerciseType.BICYCLE_CRUNCH, R.string.ex_bicycle_crunch),
        Pair(ExerciseType.REVERSE_CRUNCH, R.string.ex_reverse_crunch),
        Pair(ExerciseType.STRAIGHT_LEG_CRUNCH, R.string.ex_straight_leg_crunch)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_exercise_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.select_exercise_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(exercises) { (type, nameRes) ->
                ExerciseOptionCard(
                    name = stringResource(nameRes),
                    type = type,
                    onClick = { onExerciseSelected(type) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onExerciseSelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.auto_detect))
                }
            }
        }
    }
}

@Composable
private fun ExerciseOptionCard(name: String, type: ExerciseType, onClick: () -> Unit) {
    val preferredAngle = type.getMetadata().preferredAngle
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            if (preferredAngle == com.example.exerciseformanalyzer.model.CameraAngle.SIDE) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_side_profile),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
