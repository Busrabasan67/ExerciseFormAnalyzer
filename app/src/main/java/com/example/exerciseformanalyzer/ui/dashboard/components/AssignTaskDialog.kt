package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.exerciseformanalyzer.model.ExerciseType
import com.example.exerciseformanalyzer.ui.dashboard.ExpertViewModel.TaskExerciseInput
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskDialog(
    onDismissRequest: () -> Unit,
    dialogTitle: String = "Gelişmiş Görev Planla",
    defaultTitle: String = "Haftalık Antrenman",
    defaultNote: String = "",
    defaultSched: String = "DAILY",
    defaultDays: List<Int> = emptyList(),
    defaultAuto: Boolean = false,
    defaultWeeks: Int? = null,
    initialExercises: List<TaskExerciseInput>? = null,
    submitText: String = "Görevi Yayınla",
    onAssignTask: (title: String, note: String, dueDate: Long, exercises: List<TaskExerciseInput>, sched: String, days: List<Int>, auto: Boolean, weeks: Int?) -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var note by remember { mutableStateOf(defaultNote) }
    
    var sched by remember { mutableStateOf(defaultSched) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(defaultDays) } }
    var auto by remember { mutableStateOf(defaultAuto) }
    var weeksStr by remember { mutableStateOf(defaultWeeks?.toString() ?: "") }
    
    val exercises = remember { 
        mutableStateListOf<TaskExerciseInput>().apply { 
            if (initialExercises != null) {
                addAll(initialExercises)
            } else {
                add(TaskExerciseInput())
            }
        } 
    }
    var showError by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        dialogTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    item {
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }

                    // Zamanlama Bölümü
                    item {
                        Text("Zamanlama", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("DAILY" to "Her Gün", "WEEKLY" to "Haftalık", "CUSTOM" to "Özel Günler").forEach { (type, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { sched = type }) {
                                    RadioButton(selected = sched == type, onClick = { sched = type })
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (sched == "CUSTOM") {
                            Spacer(modifier = Modifier.height(8.dp))
                            val dayNames = listOf(
                                2 to "Pzt", 3 to "Sal", 4 to "Çar", 5 to "Per", 6 to "Cum", 7 to "Cmt", 1 to "Paz"
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                dayNames.forEach { (calDay, label) ->
                                    val isSelected = days.contains(calDay)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) days.remove(calDay) else days.add(calDay)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = auto, onCheckedChange = { auto = it })
                            Text("Otomatik Tekrarla")
                        }

                        if (auto) {
                            NumericTextField(
                                value = weeksStr,
                                onValueChange = { weeksStr = it },
                                label = "Süre (Hafta)",
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    // Egzersiz İçeriği Bölümü
                    item {
                        Text("Egzersiz İçeriği", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    itemsIndexed(exercises) { index, item ->
                        ExerciseAdvancedCard(
                            item = item,
                            onUpdate = { exercises[index] = it },
                            onRemove = { exercises.removeAt(index) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Button(
                            onClick = { exercises.add(TaskExerciseInput()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Ekle")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Egzersiz Ekle")
                        }
                        
                        if (showError.isNotEmpty()) {
                            Text(showError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, 1) 
                        
                        // Parse weeks with default 4 if empty and auto is on
                        val finalWeeks = if (auto) {
                            weeksStr.toIntOrNull() ?: 4
                        } else {
                            null
                        }
                        
                        // Validate exercises and handle empty strings
                        val validatedExercises = exercises.map { ex ->
                            ex.copy(
                                targetValue = ex.targetValue.ifEmpty { "1" },
                                sets = ex.sets.ifEmpty { "1" },
                                restTimeSeconds = ex.restTimeSeconds.ifEmpty { "30" }
                            )
                        }
                        
                        when {
                            exercises.isEmpty() -> showError = "En az 1 egzersiz eklemelisiniz."
                            validatedExercises.any { it.targetValue.toIntOrNull() == null || (it.targetValue.toIntOrNull() ?: 0) <= 0 } -> 
                                showError = "Tüm hedefler (Tekrar/Süre) 0'dan büyük olmalıdır."
                            validatedExercises.any { it.sets.toIntOrNull() == null || (it.sets.toIntOrNull() ?: 0) <= 0 } -> 
                                showError = "Set sayısı 0'dan büyük olmalıdır."
                            sched == "CUSTOM" && days.isEmpty() -> showError = "Özel günler için en az bir gün seçmelisiniz."
                            else -> {
                                onAssignTask(title, note, c.timeInMillis, validatedExercises, sched, days.toList(), auto, finalWeeks)
                            }
                        }
                    }) {
                        Text(submitText)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseAdvancedCard(
    item: TaskExerciseInput,
    onUpdate: (TaskExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Egzersiz Dropdown
                var expandedEx by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedEx,
                    onExpandedChange = { expandedEx = !expandedEx },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = item.exerciseType.displayName,
                        onValueChange = { },
                        label = { Text("Egzersiz") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEx) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEx,
                        onDismissRequest = { expandedEx = false }
                    ) {
                        ExerciseType.values().filter { it != ExerciseType.UNKNOWN }.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.displayName) },
                                onClick = {
                                    val isDuration = selectionOption == ExerciseType.PLANK
                                    onUpdate(item.copy(exerciseType = selectionOption, isDurationBased = isDuration))
                                    expandedEx = false
                                }
                            )
                        }
                    }
                }
                
                IconButton(onClick = onRemove, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericTextField(
                    value = item.targetValue,
                    onValueChange = { onUpdate(item.copy(targetValue = it)) },
                    label = if (item.isDurationBased) "Süre (Sn)" else "Tekrar",
                    modifier = Modifier.weight(1f)
                )
                NumericTextField(
                    value = item.sets,
                    onValueChange = { onUpdate(item.copy(sets = it)) },
                    label = "Set",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericTextField(
                    value = item.restTimeSeconds,
                    onValueChange = { onUpdate(item.copy(restTimeSeconds = it)) },
                    label = "Dinlenme (Sn)",
                    modifier = Modifier.weight(1f)
                )
                
                // Zorluk Dropdown
                var expandedDiff by remember { mutableStateOf(false) }
                val diffs = listOf("EASY" to "Kolay", "MEDIUM" to "Orta", "HARD" to "Zor")
                ExposedDropdownMenuBox(
                    expanded = expandedDiff,
                    onExpandedChange = { expandedDiff = !expandedDiff },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = diffs.find { it.first == item.difficulty }?.second ?: "Orta",
                        onValueChange = { },
                        label = { Text("Zorluk") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDiff) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDiff,
                        onDismissRequest = { expandedDiff = false }
                    ) {
                        diffs.forEach { (dVal, dLabel) ->
                            DropdownMenuItem(
                                text = { Text(dLabel) },
                                onClick = {
                                    onUpdate(item.copy(difficulty = dVal))
                                    expandedDiff = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            // Kategori Dropdown
            var expandedCat by remember { mutableStateOf(false) }
            val cats = listOf("STRENGTH" to "Güç", "CARDIO" to "Kardiyo", "FLEXIBILITY" to "Esneklik", "BALANCE" to "Denge", "REHAB" to "Rehabilitasyon")
            ExposedDropdownMenuBox(
                expanded = expandedCat,
                onExpandedChange = { expandedCat = !expandedCat },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = cats.find { it.first == item.category }?.second ?: "Güç",
                    onValueChange = { },
                    label = { Text("Kategori") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCat,
                    onDismissRequest = { expandedCat = false }
                ) {
                    cats.forEach { (cVal, cLabel) ->
                        DropdownMenuItem(
                            text = { Text(cLabel) },
                            onClick = {
                                onUpdate(item.copy(category = cVal))
                                expandedCat = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newTV ->
            if (newTV.text.all { it.isDigit() }) {
                textFieldValue = newTV
                if (newTV.text != value) {
                    onValueChange(newTV.text)
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusChanged { 
            if (it.isFocused) {
                textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
            }
        },
        singleLine = true
    )
}
