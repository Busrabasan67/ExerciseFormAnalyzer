package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.json.JSONArray
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.MainViewModel

// Görev bağlamlı egzersiz başlatma için callback parametresi.
// taskId + exerciseIndex kesin eşleşme için zorunlu; exerciseType kamera ekranı için.
data class TaskExerciseStartParams(
    val exerciseType: ExerciseType,
    val taskId: Int,
    val exerciseIndex: Int,
    val targetType: String,
    val targetReps: Int,
    val targetDurationSeconds: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCamera: (ExerciseType?) -> Unit,
    onNavigateToTaskExercise: (TaskExerciseStartParams) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit = {},
    onLogout: () -> Unit
) {
    val currentUser by viewModel.observeCurrentUser().collectAsState(initial = null)
    val tasks by viewModel.observeMyTasks().collectAsState(initial = emptyList())
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())

    LaunchedEffect(currentUser?.expertUid) {
        viewModel.syncPatientData(currentUser?.expertUid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.patient_dashboard_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                actions = {
                    TextButton(onClick = onNavigateToGroups) {
                        Text(
                            stringResource(R.string.groups_title),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                    TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCamera(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.start_exercise))
            }
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            item {
                if (!currentUser?.expertUid.isNullOrEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Bağlı Uzmanınız Tarafından Takip Ediliyorsunuz",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val pendingTasks = tasks.filter { it.status == "PENDING" || it.status == "ASSIGNED" }
                val inProgressTasks = tasks.filter { it.status == "IN_PROGRESS" }
                val completedTasks = tasks.filter { it.status == "COMPLETED" || it.status == "DONE" || it.status == "MISSED" }

                // --- Bekleyen Görevler ---
                Text("Bekleyen Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                if (pendingTasks.isEmpty()) {
                    Text("Bekleyen görev yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    pendingTasks.forEach { task ->
                        TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Devam Eden Görevler ---
                Text("Devam Eden Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                if (inProgressTasks.isEmpty()) {
                    Text("Devam eden görev yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    inProgressTasks.forEach { task ->
                        TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Tamamlanan Görevler ---
                Text("Tamamlanan Görevler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                if (completedTasks.isEmpty()) {
                    Text("Tamamlanan görev yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    completedTasks.forEach { task ->
                        TaskCard(task, onNavigateToTaskExercise, onNavigateToCamera)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.my_reports), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.no_reports))
                }
            }

            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${stringResource(R.string.score_label)}: ${report.score} / ${report.reps} ${stringResource(R.string.reps_unit)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${report.caloriesBurned.toInt()} ${stringResource(R.string.calorie_unit)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!report.feedback.isNullOrEmpty()) {
                            val feedbackText = if (report.feedback == "Mükemmel Form") {
                                stringResource(R.string.perfect_form)
                            } else {
                                report.feedback
                            }
                            Text(text = feedbackText, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity,
    onNavigateToTaskExercise: (TaskExerciseStartParams) -> Unit,
    onNavigateToCamera: (ExerciseType?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (task.title.isNotEmpty()) task.title else "Görev",
                style = MaterialTheme.typography.titleMedium
            )
            if (task.note.isNotEmpty()) {
                Text(
                    text = task.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // exercisesJson parse ediliyor; her item index ile birlikte saklanır.
            // Index, WorkoutRepository'de kesin eşleşme için zorunludur.
            data class ParsedExercise(
                val index: Int,
                val name: String,
                val tType: String,
                val targetReps: Int,
                val targetDur: Int,
                val actualReps: Int,
                val actualDur: Int,
                val status: String,
                val progressStr: String
            )

            val parsedExercises = remember(task.exercisesJson) {
                val list = mutableListOf<ParsedExercise>()
                try {
                    val arr = JSONArray(task.exercisesJson)
                    for (i in 0 until arr.length()) {
                        val ex = arr.getJSONObject(i)
                        val name = ex.optString("exerciseType", "Bilinmeyen")
                        val tType = ex.optString("targetType", "REPS")
                        val aReps = ex.optInt("actualReps", 0)
                        val aDur = ex.optInt("actualDurationSeconds", 0)
                        val tReps = ex.optInt("targetReps", 0)
                        val tDur = ex.optInt("targetDurationSeconds", 0)
                        val exStatus = ex.optString("status", "PENDING")

                        val progressStr = if (tType == "DURATION") {
                            "$aDur / $tDur Sn"
                        } else {
                            "$aReps / $tReps Tekrar"
                        }

                        list.add(
                            ParsedExercise(
                                index = i,
                                name = name,
                                tType = tType,
                                targetReps = tReps,
                                targetDur = tDur,
                                actualReps = aReps,
                                actualDur = aDur,
                                status = exStatus,
                                progressStr = progressStr
                            )
                        )
                    }
                } catch (e: Exception) { /* JSON bozuksa sessizce atla */ }
                list
            }

            if (parsedExercises.isEmpty()) {
                Text("Egzersiz detayları okunamadı", style = MaterialTheme.typography.bodySmall)
            } else {
                parsedExercises.forEach { exData ->
                    val isCompleted = exData.status == "COMPLETED"
                    val rowColor = if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCompleted) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Tamamlandı",
                                    tint = rowColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Text("• ", color = rowColor)
                            }
                            Text(
                                exData.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = rowColor
                            )
                        }
                        Text(
                            exData.progressStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = rowColor
                        )

                        if (!isCompleted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val exerciseType = ExerciseType.values()
                                .find { it.name.equals(exData.name, ignoreCase = true) }

                            Button(
                                onClick = {
                                    if (exerciseType != null) {
                                        // TaskContext bilgileri ile görev-egzersiz bağlantısı kurulur
                                        onNavigateToTaskExercise(
                                            TaskExerciseStartParams(
                                                exerciseType = exerciseType,
                                                taskId = task.id,
                                                exerciseIndex = exData.index,
                                                targetType = exData.tType,
                                                targetReps = exData.targetReps,
                                                targetDurationSeconds = exData.targetDur
                                            )
                                        )
                                    } else {
                                        // Tanınmayan tip: serbest egzersiz olarak aç
                                        onNavigateToCamera(null)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Başla", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val finalStatusText = when (task.status) {
                "COMPLETED"   -> "Tüm Görev Tamamlandı!"
                "IN_PROGRESS" -> "Devam Ediyor..."
                "DONE"        -> "Tamamlandı"
                "MISSED"      -> "Kaçırıldı"
                else          -> "Bekliyor"
            }
            val finalStatusColor =
                if (task.status == "COMPLETED" || task.status == "DONE")
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary

            Text(
                text = "Durum: $finalStatusText",
                style = MaterialTheme.typography.labelLarge,
                color = finalStatusColor
            )
        }
    }
}

