package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel.TaskExerciseInput
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskDialog(
    onDismissRequest: () -> Unit,
    onAssignTask: (title: String, note: String, dueDate: Long, exercises: List<TaskExerciseInput>, sched: String, days: List<Int>, auto: Boolean, weeks: Int?) -> Unit
) {
    var title by remember { mutableStateOf("Haftalık Antrenman") }
    var note by remember { mutableStateOf("") }
    
    // Eksik alanlar için stateler eklendi
    var sched by remember { mutableStateOf("DAILY") }
    var days by remember { mutableStateOf(emptyList<Int>()) }
    var auto by remember { mutableStateOf(false) }
    var weeks by remember { mutableStateOf<Int?>(null) }
    
    // Default task list with 1 item
    val exercises = remember { mutableStateListOf(TaskExerciseInput()) }
    var showError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Yeni Görev Ata") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Görev Başlığı") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Açıklama / Not") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Text("Egzersizler:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    itemsIndexed(exercises) { index, item ->
                        ExerciseRow(
                            item = item,
                            onUpdate = { updatedItem -> exercises[index] = updatedItem },
                            onRemove = { exercises.removeAt(index) }
                        )
                        Divider()
                    }
                }
                
                TextButton(
                    onClick = { exercises.add(TaskExerciseInput()) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                ) {
                    Text("+ Egzersiz Ekle")
                }
                
                if (showError.isNotEmpty()) {
                    Text(showError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, 1) // Yarına kadar (Basitlik için)
                
                when {
                    exercises.isEmpty() -> showError = "En az 1 egzersiz eklemelisiniz."
                    exercises.any { it.targetValue.toIntOrNull() == null || (it.targetValue.toIntOrNull() ?: 0) <= 0 } -> 
                        showError = "Tüm hedefler 0'dan büyük bir sayı olmalıdır."
                    else -> {
                        onAssignTask(title, note, c.timeInMillis, exercises.toList(), sched, days, auto, weeks)
                    }
                }
            }) {
                Text("Ata")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("İptal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseRow(
    item: TaskExerciseInput,
    onUpdate: (TaskExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = item.exerciseType.displayName,
                    onValueChange = { },
                    label = { Text("Egzersiz") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ExerciseType.values().filter { it != ExerciseType.UNKNOWN }.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.displayName) },
                            onClick = {
                                val isDuration = selectionOption == ExerciseType.PLANK
                                onUpdate(item.copy(exerciseType = selectionOption, isDurationBased = isDuration))
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = item.targetValue,
                onValueChange = { onUpdate(item.copy(targetValue = it)) },
                label = { Text(if (item.isDurationBased) "Süre (Saniye)" else "Tekrar Sayısı") }
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = item.sets.toString(),
                onValueChange = {
                    val s = it.toIntOrNull() ?: 1
                    onUpdate(item.copy(sets = s))
                },
                label = { Text("Set Sayısı") }
            )
        }
        
        IconButton(onClick = onRemove, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
        }
    }
}
