package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskDialog(
    onDismissRequest: () -> Unit,
    dialogTitle: String = stringResource(R.string.ui_advanced_task_plan),
    defaultTitle: String = stringResource(R.string.ui_weekly_workout),
    defaultNote: String = "",
    defaultSched: String = "DAILY",
    defaultDays: List<Int> = emptyList(),
    defaultAuto: Boolean = false,
    defaultWeeks: Int? = null,
    initialExercises: List<TaskExerciseInput>? = null,
    submitText: String = stringResource(R.string.ui_publish_task),
    onAssignTask: (title: String, note: String, dueDate: Long, exercises: List<TaskExerciseInput>, sched: String, days: List<Int>, auto: Boolean, weeks: Int?) -> Unit
) {
    val context = LocalContext.current
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var note by remember(defaultNote) { mutableStateOf(defaultNote) }
    
    var sched by remember(defaultSched) { mutableStateOf(defaultSched) }
    val days = remember(defaultDays) { 
        mutableStateListOf<Int>().apply { 
            clear()
            addAll(defaultDays) 
        } 
    }
    var auto by remember(defaultAuto) { mutableStateOf(defaultAuto) }
    var weeksStr by remember(defaultWeeks) { mutableStateOf(defaultWeeks?.toString() ?: "") }
    
    val exercises = remember(initialExercises) { 
        mutableStateListOf<TaskExerciseInput>().apply { 
            clear()
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
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(0xFF1B5E20), Color(0xFF00C853))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Text(
                        dialogTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.ui_task_title)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFF00C853)
                            )
                        )
                        OutlinedTextField(
                            value = note, 
                            onValueChange = { note = it }, 
                            label = { Text(stringResource(R.string.ui_special_notes_hint)) }, 
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFF00C853)
                            )
                        )
                    }

                    // Zamanlama Bölümü
                    item {
                        Text(stringResource(R.string.ui_planning_label), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("DAILY" to stringResource(R.string.ui_everyday), "WEEKLY" to stringResource(R.string.ui_weekly), "CUSTOM" to stringResource(R.string.ui_custom)).forEach { (type, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier.clickable { sched = type }
                                ) {
                                    RadioButton(
                                        selected = sched == type, 
                                        onClick = { sched = type },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00C853))
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (sched == "CUSTOM") {
                            Spacer(modifier = Modifier.height(12.dp))
                            val dayNames = listOf(
                                2 to stringResource(R.string.ui_mon), 
                                3 to stringResource(R.string.ui_tue), 
                                4 to stringResource(R.string.ui_wed), 
                                5 to stringResource(R.string.ui_thu), 
                                6 to stringResource(R.string.ui_fri), 
                                7 to stringResource(R.string.ui_sat), 
                                1 to stringResource(R.string.ui_sun)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                dayNames.forEach { (calDay, label) ->
                                    val isSelected = days.contains(calDay)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) days.remove(calDay) else days.add(calDay)
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF00C853),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFF00C853).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = auto, 
                                    onCheckedChange = { auto = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00C853))
                                )
                                Text(stringResource(R.string.ui_auto_repeat_weekly), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (auto) {
                            Spacer(modifier = Modifier.height(12.dp))
                            NumericTextField(
                                value = weeksStr,
                                onValueChange = { weeksStr = it },
                                label = stringResource(R.string.ui_duration_weeks),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // Egzersiz İçeriği Bölümü
                    item {
                        Text(stringResource(R.string.ui_exercise_program), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    itemsIndexed(exercises) { index, item ->
                        ExerciseAdvancedCard(
                            item = item,
                            onUpdate = { exercises[index] = it },
                            onRemove = { if (exercises.size > 1) exercises.removeAt(index) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        OutlinedButton(
                            onClick = { exercises.add(TaskExerciseInput()) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF00C853)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00C853))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ui_add), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_add_new_exercise), style = MaterialTheme.typography.labelLarge)
                        }
                        
                        if (showError.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                            ) {
                                Text(
                                    text = showError, 
                                    color = MaterialTheme.colorScheme.error, 
                                    style = MaterialTheme.typography.bodySmall, 
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(stringResource(R.string.ui_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            // ... existing validation logic ...
                            val c = Calendar.getInstance()
                            c.add(Calendar.DAY_OF_YEAR, 1) 
                            
                            val finalWeeks = if (auto) weeksStr.toIntOrNull() ?: 4 else null
                            val validatedExercises = exercises.map { ex ->
                                ex.copy(
                                    targetValue = ex.targetValue.ifEmpty { "1" },
                                    sets = ex.sets.ifEmpty { "1" },
                                    restTimeSeconds = ex.restTimeSeconds
                                )
                            }
                            
                            when {
                                exercises.isEmpty() -> showError = context.getString(R.string.ui_err_at_least_one_ex)
                                validatedExercises.any { it.targetValue.toIntOrNull() == null || (it.targetValue.toIntOrNull() ?: 0) <= 0 } -> 
                                    showError = context.getString(R.string.ui_err_target_positive)
                                validatedExercises.any { it.sets.toIntOrNull() == null || (it.sets.toIntOrNull() ?: 0) <= 0 } -> 
                                    showError = context.getString(R.string.ui_err_sets_positive)
                                sched == "CUSTOM" && days.isEmpty() -> showError = context.getString(R.string.ui_err_select_custom_days)
                                else -> {
                                    val sortedDays = days.sortedWith(compareBy { if (it == 1) 8 else it })
                                    onAssignTask(title, note, c.timeInMillis, validatedExercises, sched, sortedDays, auto, finalWeeks)
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                    ) {
                        Text(submitText, style = MaterialTheme.typography.titleSmall)
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        label = { Text(stringResource(R.string.ui_exercise_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEx) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00C853)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEx,
                        onDismissRequest = { expandedEx = false }
                    ) {
                        val implementedExercises = listOf(
                            ExerciseType.SQUAT, ExerciseType.PUSH_UP, ExerciseType.SIT_UP,
                            ExerciseType.DUMBBELL_ROW, ExerciseType.BICEPS_CURL, ExerciseType.PLANK,
                            ExerciseType.SHOULDER_PRESS, ExerciseType.LATERAL_RAISE, ExerciseType.HAMMER_CURL,
                            ExerciseType.TRICEPS_EXTENSION, ExerciseType.TRICEPS_KICKBACK, ExerciseType.BENT_OVER_ROW,
                            ExerciseType.BENT_OVER_RAISE, ExerciseType.MOUNTAIN_CLIMBER, ExerciseType.RUSSIAN_TWIST,
                            ExerciseType.HEEL_TAP, ExerciseType.BICYCLE_CRUNCH, ExerciseType.REVERSE_CRUNCH,
                            ExerciseType.STRAIGHT_LEG_CRUNCH
                        )
                        implementedExercises.forEach { selectionOption ->
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
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericTextField(
                    value = item.targetValue,
                    onValueChange = { onUpdate(item.copy(targetValue = it)) },
                    label = if (item.isDurationBased) stringResource(R.string.ui_duration_sec) else stringResource(R.string.ui_reps_label),
                    modifier = Modifier.weight(1f).height(64.dp)
                )
                NumericTextField(
                    value = item.sets,
                    onValueChange = { onUpdate(item.copy(sets = it)) },
                    label = stringResource(R.string.ui_set_count),
                    modifier = Modifier.weight(1f).height(64.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericTextField(
                    value = item.restTimeSeconds,
                    onValueChange = { onUpdate(item.copy(restTimeSeconds = it)) },
                    label = stringResource(R.string.ui_rest_time_sec_label),
                    modifier = Modifier.weight(1f).height(64.dp)
                )
                
                // Zorluk Dropdown
                var expandedDiff by remember { mutableStateOf(false) }
                val diffs = listOf(
                    "EASY" to stringResource(R.string.ui_easy), 
                    "MEDIUM" to stringResource(R.string.ui_medium), 
                    "HARD" to stringResource(R.string.ui_hard)
                )
                ExposedDropdownMenuBox(
                    expanded = expandedDiff,
                    onExpandedChange = { expandedDiff = !expandedDiff },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = diffs.find { it.first == item.difficulty }?.second ?: stringResource(R.string.ui_medium),
                        onValueChange = { },
                        label = { Text(stringResource(R.string.ui_difficulty_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDiff) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00C853)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.menuAnchor().fillMaxWidth().height(64.dp)
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
            
            Spacer(modifier = Modifier.height(12.dp))
            // Kategori Dropdown
            var expandedCat by remember { mutableStateOf(false) }
            val cats = listOf(
                "STRENGTH" to stringResource(R.string.ui_strength), 
                "CARDIO" to stringResource(R.string.ui_cardio), 
                "FLEXIBILITY" to stringResource(R.string.ui_flexibility), 
                "BALANCE" to stringResource(R.string.ui_balance), 
                "REHAB" to stringResource(R.string.ui_rehab)
            )
            ExposedDropdownMenuBox(
                expanded = expandedCat,
                onExpandedChange = { expandedCat = !expandedCat },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = cats.find { it.first == item.category }?.second ?: stringResource(R.string.ui_strength),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.ui_category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(14.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
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
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00C853)),
        modifier = modifier.onFocusChanged { 
            if (it.isFocused) {
                textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
            }
        },
        singleLine = true
    )
}
