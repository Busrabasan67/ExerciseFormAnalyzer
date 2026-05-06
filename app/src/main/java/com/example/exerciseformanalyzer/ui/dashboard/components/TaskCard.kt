package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.data.local.entity.TaskAssignmentEntity
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.TaskExerciseStartParams
import org.json.JSONArray

@Composable
fun TaskCard(
    task: TaskAssignmentEntity,
    isExpertLinked: Boolean,
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

            data class ParsedExercise(
                val index: Int,
                val name: String,
                val tType: String,
                val targetReps: Int,
                val targetDur: Int,
                val sets: Int,
                val completedSets: Int,
                val actualReps: Int,
                val actualDur: Int,
                val status: String,
                val progressStr: String,
                val restTimeSeconds: Int?,
                val repsDoneInCurrentSet: Int,
                val durDoneInCurrentSet: Int,
                val isPartiallyDone: Boolean
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
                        val sets = ex.optInt("sets", 1)
                        val cSets = ex.optInt("completedSets", 0)
                        val exStatus = ex.optString("status", "PENDING")
                        val restTime = if (ex.has("restTimeSeconds") && !ex.isNull("restTimeSeconds")) ex.optInt("restTimeSeconds") else null

                        val rDone = maxOf(0, aReps - (cSets * tReps))
                        val dDone = maxOf(0, aDur - (cSets * tDur))
                        val isPartiallyDone = (rDone > 0 || dDone > 0) && exStatus != "COMPLETED"

                        val progressStr = if (tType == "DURATION") {
                            "Set: $cSets/$sets • $aDur/$tDur Sn"
                        } else {
                            "Set: $cSets/$sets • $aReps/$tReps Tekrar"
                        }

                        list.add(
                            ParsedExercise(
                                index = i,
                                name = name,
                                tType = tType,
                                targetReps = tReps,
                                targetDur = tDur,
                                sets = sets,
                                completedSets = cSets,
                                actualReps = aReps,
                                actualDur = aDur,
                                status = exStatus,
                                progressStr = progressStr,
                                restTimeSeconds = restTime,
                                repsDoneInCurrentSet = rDone,
                                durDoneInCurrentSet = dDone,
                                isPartiallyDone = isPartiallyDone
                            )
                        )
                    }
                } catch (e: Exception) { }
                list
            }

            if (parsedExercises.isEmpty()) {
                Text("Egzersiz detayları okunamadı", style = MaterialTheme.typography.bodySmall)
            } else {
                parsedExercises.forEach { exData ->
                    val isCompleted = exData.status == "COMPLETED"
                    val rowColor = if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
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
                                    imageVector = Icons.Filled.Check,
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

                            val isInactive = task.status == "inactive" || task.status == "removed" || !isExpertLinked

                            Button(
                                onClick = {
                                    if (exerciseType != null) {
                                        onNavigateToTaskExercise(
                                            TaskExerciseStartParams(
                                                exerciseType = exerciseType,
                                                taskId = task.id,
                                                exerciseIndex = exData.index,
                                                targetType = exData.tType,
                                                targetReps = exData.targetReps,
                                                targetDurationSeconds = exData.targetDur,
                                                targetSets = exData.sets,
                                                completedSets = exData.completedSets,
                                                restTimeSeconds = exData.restTimeSeconds,
                                                repsDoneInCurrentSet = exData.repsDoneInCurrentSet,
                                                durDoneInCurrentSet = exData.durDoneInCurrentSet
                                            )
                                        )
                                    } else {
                                        onNavigateToCamera(null)
                                    }
                                },
                                enabled = !isInactive,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(if (exData.isPartiallyDone) "Devam Et" else "Başla", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val finalStatusText = when (task.status) {
                "COMPLETED"   -> "Tüm Görev Tamamlandı!"
                "IN_PROGRESS" -> "Devam Ediyor..."
                "MISSED"      -> "Kaçırıldı"
                "inactive", "removed" -> "Bu görev artık aktif değil."
                else          -> {
                    if (!isExpertLinked) "Bu görev artık aktif değil."
                    else "Bekliyor"
                }
            }
            val finalStatusColor =
                if (task.status == "COMPLETED")
                    MaterialTheme.colorScheme.primary
                else if (task.status == "inactive" || task.status == "removed" || !isExpertLinked)
                    MaterialTheme.colorScheme.error
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
