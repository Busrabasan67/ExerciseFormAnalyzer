package com.example.exerciseformanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.exerciseformanalyzer.model.WorkoutSummary

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆 Antrenman Özeti",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary.exercise.displayName,
                    fontSize = 18.sp,
                    color = Color(0xFF00BFA5),
                    fontWeight = FontWeight.Medium
                )
                
                Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStatCard("Tekrar", "${summary.totalReps}", Color(0xFFFFB300))
                    val mins = summary.durationSeconds / 60
                    val secs = summary.durationSeconds % 60
                    SummaryStatCard("Süre", String.format("%02d:%02d", mins, secs), Color(0xFF42A5F5))
                    SummaryStatCard("Doğruluk", "%${summary.accuracyPercentage}", if (summary.accuracyPercentage > 80) Color(0xFF66BB6A) else Color(0xFFEF5350))
                }
                
                if (summary.mostCommonError != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF5350)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 Gelişim Alanı:", fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                            Spacer(Modifier.height(4.dp))
                            Text(text = "En çok yaptığınız hata: \"${summary.mostCommonError}\"", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Yeni Antrenmana Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, fontSize = 14.sp, color = Color.LightGray)
    }
}
