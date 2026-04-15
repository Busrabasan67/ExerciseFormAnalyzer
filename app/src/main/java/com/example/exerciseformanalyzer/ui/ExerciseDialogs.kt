package com.example.exerciseformanalyzer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.exerciseformanalyzer.model.ExerciseType

@Composable
fun ExerciseSelectionDialog(
    onExerciseSelected: (ExerciseType) -> Unit,
    onAutoDetectSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Zorunlu seçim için boş */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(text = "Antrenmana Başla", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Lütfen yapacağınız hareketi seçin:")
                Spacer(modifier = Modifier.height(16.dp))
                
                // UNKNOWN hariç hepsini listele
                val exercises = ExerciseType.values().filter { it != ExerciseType.UNKNOWN }
                
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onAutoDetectSelected() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "✨ Otomatik Algılasın",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(exercises) { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onExerciseSelected(exercise) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = exercise.displayName,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ExerciseInfoDialog(
    exercise: ExerciseType,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val meta = exercise.getMetadata()

    AlertDialog(
        onDismissRequest = { /* Zorunlu onay */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(text = exercise.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = meta.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Kamera Açısı:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = "👉 ${meta.preferredAngle.displayName} olacak şekilde hazırlanın.", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Text("DOĞRU FORM:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFF00BFA5))
                meta.correctFormRules.forEach { rule ->
                    Text(text = "✓ $rule", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("YAPILMAMASI GEREKENLER:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFFE53935))
                meta.commonMistakes.forEach { mistake ->
                    Text(text = "✗ $mistake", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onStart) {
                Text("Anladım, Başla")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("İptal")
            }
        }
    )
}
