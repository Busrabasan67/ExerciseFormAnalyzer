package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel.TaskExerciseInput
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskDialog(
    onDismissRequest: () -> Unit,
    onAssignTask: (
        title: String, 
        note: String, 
        dueDate: Long, 
        exercises: List<TaskExerciseInput>, 
        scheduleType: String, 
        daysOfWeek: List<Int>, 
        autoRepeat: Boolean, 
        repeatWeeks: Int?
    ) -> Unit
) {
    var title by remember { mutableStateOf("Haftalık Antrenman") }
    var note by remember { mutableStateOf("") }
    
    // Scheduling States
    val scheduleTypes = listOf("DAILY", "WEEKLY", "CUSTOM")
    var selectedScheduleType by remember { mutableStateOf("DAILY") }
    var scheduleExpanded by remember { mutableStateOf(false) }
    
    val daysLabel = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val selectedDays = remember { mutableStateListOf<Int>() } // 1-7
    
    var autoRepeat by remember { mutableStateOf(false) }
    var repeatWeeks by remember { mutableStateOf("4") }
    
    // Exercises
    val exercises = remember { mutableStateListOf(TaskExerciseInput()) }
    var showError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = { Text("Gelişmiş Görev Planla") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Görev Başlığı") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Özel Notlar") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    minLines = 2
                )

                Text("Zamanlama", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = scheduleExpanded,
                    onExpandedChange = { scheduleExpanded = !scheduleExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when(selectedScheduleType) {
                            "DAILY" -> "Her Gün"
                            "WEEKLY" -> "Haftalık"
                            "CUSTOM" -> "Özel Günler"
                            else -> selectedScheduleType
                        },
                        onValueChange = { },
                        label = { Text("Frekans") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = scheduleExpanded,
                        onDismissRequest = { scheduleExpanded = false }
                    ) {
                        scheduleTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { 
                                    Text(when(type) {
                                        "DAILY" -> "Her Gün"
                                        "WEEKLY" -> "Haftalık"
                                        "CUSTOM" -> "Özel Günler"
                                        else -> type
                                    })
                                },
                                onClick = {
                                    selectedScheduleType = type
                                    scheduleExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedScheduleType == "CUSTOM") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        daysLabel.forEachIndexed { index, day ->
                            val dayNum = index + 1
                            FilterChip(
                                selected = selectedDays.contains(dayNum),
                                onClick = {
                                    if (selectedDays.contains(dayNum)) selectedDays.remove(dayNum)
                                    else selectedDays.add(dayNum)
                                },
                                label = { Text(day) }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = autoRepeat, onCheckedChange = { autoRepeat = it })
                    Text("Otomatik Tekrarla")
                }

                if (autoRepeat) {
                    OutlinedTextField(
                        value = repeatWeeks,
                        onValueChange = { if (it.all { c -> c.isDigit() }) repeatWeeks = it },
                        label = { Text("Süre (Hafta)") },
                        modifier = Modifier.width(150.dp).padding(start = 32.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Egzersiz İçeriği", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { exercises.add(TaskExerciseInput()) }) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle")
                    }
                }
                
                exercises.forEachIndexed { index, item ->
                    AdvancedExerciseCard(
                        item = item,
                        onUpdate = { updated -> exercises[index] = updated },
                        onRemove = { exercises.removeAt(index) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showError.isNotEmpty()) {
                    Text(showError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, 1)
                val weeks = if (autoRepeat) repeatWeeks.toIntOrNull() else null
                
                when {
                    exercises.isEmpty() -> showError = "En az 1 egzersiz ekleyin."
                    selectedScheduleType == "CUSTOM" && selectedDays.isEmpty() -> showError = "Gün seçin."
                    else -> onAssignTask(title, note, c.timeInMillis, exercises.toList(), selectedScheduleType, selectedDays.toList(), autoRepeat, weeks)
                }
            }) {
                Text("Görevi Yayınla")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("İptal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedExerciseCard(
    item: TaskExerciseInput,
    onUpdate: (TaskExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var diffExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = item.exerciseType.displayName,
                        onValueChange = { },
                        label = { Text("Egzersiz") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExerciseType.values().filter { it != ExerciseType.UNKNOWN }.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    onUpdate(item.copy(exerciseType = type, isDurationBased = (type == ExerciseType.PLANK)))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error) }
            }

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.targetValue,
                    onValueChange = { if (it.all { c -> c.isDigit() }) onUpdate(item.copy(targetValue = it)) },
                    label = { Text(if (item.isDurationBased) "Süre" else "Tekrar") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.sets.toString(),
                    onValueChange = { onUpdate(item.copy(sets = it.toIntOrNull() ?: 1)) },
                    label = { Text("Set") },
                    modifier = Modifier.weight(0.6f)
                )
                OutlinedTextField(
                    value = item.restTimeSeconds.toString(),
                    onValueChange = { onUpdate(item.copy(restTimeSeconds = it.toIntOrNull() ?: 30)) },
                    label = { Text("Dinlenme") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = diffExpanded, onExpandedChange = { diffExpanded = !diffExpanded }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when(item.difficulty) { "EASY" -> "Kolay"; "MEDIUM" -> "Orta"; "HARD" -> "Zor"; else -> item.difficulty },
                        onValueChange = { },
                        label = { Text("Zorluk") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = diffExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = diffExpanded, onDismissRequest = { diffExpanded = false }) {
                        listOf("EASY", "MEDIUM", "HARD").forEach { d ->
                            DropdownMenuItem(text = { Text(when(d) { "EASY" -> "Kolay"; "MEDIUM" -> "Orta"; "HARD" -> "Zor"; else -> d }) }, onClick = { onUpdate(item.copy(difficulty = d)); diffExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when(item.category) { "REHAB" -> "Rehabilitasyon"; "STRENGTH" -> "Güç Kon."; "CARDIO" -> "Kardiyo"; else -> item.category },
                        onValueChange = { },
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        listOf("REHAB", "STRENGTH", "CARDIO").forEach { c ->
                            DropdownMenuItem(text = { Text(when(c) { "REHAB" -> "Rehabilitasyon"; "STRENGTH" -> "Güç Kon."; "CARDIO" -> "Kardiyo"; else -> c }) }, onClick = { onUpdate(item.copy(category = c)); catExpanded = false })
                        }
                    }
                }
            }
        }
    }
}