package com.example.exerciseformanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.exerciseformanalyzer.model.*

// ─── Renk Paleti ──────────────────────────────────────────────────────────────

val ColorGoodForm = Color(0xFF00E676)       // Canlı yeşil — doğru form
val ColorBadForm = Color(0xFFFF1744)        // Canlı kırmızı — yanlış form
val ColorWarning = Color(0xFFFFAB00)        // Amber — uyarı
val ColorNeutral = Color(0xFF90CAF9)        // Açık mavi — takip zayıf
val ColorBackground = Color(0xCC000000)     // Yarı saydam siyah — overlay arka planı
val ColorLandmark = Color(0xFFFFEB3B)       // Sarı — landmark nokta
val ColorBone = Color(0xFFFFFFFF)           // Beyaz — iskelet hattı

// ─── Ana Overlay Composable ───────────────────────────────────────────────────

/**
 * Kamera önizlemesinin üzerine bindirilen tüm UI elemanları.
 * Üç bölümden oluşur:
 *   1. Pose skeleton overlay (Canvas)
 *   2. Üst bilgi paneli (egzersiz adı, tracking kalitesi)
 *   3. Alt feedback paneli (form durumu, tekrar sayısı, hata mesajı)
 */
@Composable
fun ExerciseOverlay(
    uiState: ExerciseUiState,
    poseFrame: PoseFrame?,
    sessionDurationSec: Long = 0L,
    isPaused: Boolean = false,
    onPauseToggle: () -> Unit = {},
    onEndWorkout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // 1. Skeleton Overlay
        poseFrame?.let { frame ->
            if (frame.landmarks.isNotEmpty()) {
                PoseSkeletonOverlay(
                    poseFrame = frame,
                    isCorrectForm = (uiState as? ExerciseUiState.Analyzing)?.result?.formFeedback?.isCorrect ?: true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        when (uiState) {
            is ExerciseUiState.Loading -> {
                LoadingOverlay()
            }
            is ExerciseUiState.Ready -> {
                ReadyOverlay()
            }
            is ExerciseUiState.Analyzing -> {
                AnalyzingOverlay(
                    result = uiState.result,
                    sessionDurationSec = sessionDurationSec,
                    isPaused = isPaused,
                    onPauseToggle = onPauseToggle,
                    onEndWorkout = onEndWorkout
                )
            }
            is ExerciseUiState.Error -> {
                ErrorOverlay(message = uiState.message)
            }
        }
    }
}

// ─── Pose Skeleton Canvas ─────────────────────────────────────────────────────

/**
 * MediaPipe 33 landmark'ı kamera önizleme boyutuna ölçekleyerek çizer.
 * İskelet bağlantıları ve landmark noktaları ayrı ayrı çizilir.
 * [isCorrectForm]: true → yeşil iskelet, false → kırmızı iskelet.
 */
@Composable
fun PoseSkeletonOverlay(
    poseFrame: PoseFrame,
    isCorrectForm: Boolean,
    modifier: Modifier = Modifier
) {
    val skeletonColor = if (isCorrectForm) ColorGoodForm else ColorBadForm

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        fun lm(index: Int): Offset? {
            val lm = poseFrame.landmarkOrNull(index) ?: return null
            if (lm.visibility < 0.4f) return null
            return Offset(lm.x * width, lm.y * height)
        }

        // İskelet bağlantıları
        drawSkeletonConnections(this, ::lm, skeletonColor)

        // Landmark noktaları
        poseFrame.landmarks.forEachIndexed { index, landmark ->
            if (landmark.visibility >= 0.4f) {
                val x = landmark.x * width
                val y = landmark.y * height
                drawCircle(
                    color = ColorLandmark,
                    radius = 8f,
                    center = Offset(x, y)
                )
                // Yüksek güven → dış halka
                if (landmark.visibility >= 0.75f) {
                    drawCircle(
                        color = skeletonColor.copy(alpha = 0.5f),
                        radius = 14f,
                        center = Offset(x, y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

/** İskelet bağlantı çizgilerini çizer. */
private fun drawSkeletonConnections(
    scope: DrawScope,
    lm: (Int) -> Offset?,
    color: Color
) {
    val connections = listOf(
        // Yüz
        11 to 12,  // Sol omuz — Sağ omuz
        // Sol kol
        11 to 13, 13 to 15,
        // Sağ kol
        12 to 14, 14 to 16,
        // Gövde
        11 to 23, 12 to 24, 23 to 24,
        // Sol bacak
        23 to 25, 25 to 27, 27 to 29, 27 to 31,
        // Sağ bacak
        24 to 26, 26 to 28, 28 to 30, 28 to 32
    )

    connections.forEach { (a, b) ->
        val start = lm(a) ?: return@forEach
        val end = lm(b) ?: return@forEach
        scope.drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
}

// ─── Durum Overlay'leri ────────────────────────────────────────────────────────

@Composable
private fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            StatusText("Model yükleniyor...")
        }
    }
}

@Composable
private fun ReadyOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        InfoBadge(
            text = "Egzersiz yapmaya başlayın",
            color = ColorWarning,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ErrorOverlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(ColorBackground, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Text("⚠️", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            StatusText(message, color = ColorBadForm)
        }
    }
}

@Composable
private fun AnalyzingOverlay(
    result: AnalysisResult,
    sessionDurationSec: Long,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onEndWorkout: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        // Üst panel — egzersiz adı + tracking kalitesi + Zamanlayıcı
        TopInfoPanel(
            exerciseType = result.exerciseType,
            trackingQuality = result.trackingQuality,
            poseConfidence = result.poseConfidence,
            isInFrame = result.isInFrame,
            sessionDurationSec = sessionDurationSec,
            isPaused = isPaused,
            onPauseToggle = onPauseToggle,
            onEndWorkout = onEndWorkout,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )

        // İşlem duraklatıldıysa ekran karartması
        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("DURAKLATILDI", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Alt panel — form feedback + tekrar sayısı
        BottomFeedbackPanel(
            feedback = result.formFeedback,
            repState = result.repetitionState,
            isPersonVisible = result.isPersonVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        )

        // Sol taraf — form skoru (Eğer aktif analiz yapılıyorsa)
        if (result.trackingQuality != TrackingQuality.LOST && !isPaused) {
            FormScoreIndicator(
                score = result.formFeedback.score,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )
        }
    }
}

// ─── Panel Bileşenleri ─────────────────────────────────────────────────────────

@Composable
private fun TopInfoPanel(
    exerciseType: ExerciseType,
    trackingQuality: TrackingQuality,
    poseConfidence: Float,
    isInFrame: Boolean,
    sessionDurationSec: Long,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onEndWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoBadge(
                text = exerciseType.displayName,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            val mins = sessionDurationSec / 60
            val secs = sessionDurationSec % 60
            InfoBadge(
                text = String.format("%02d:%02d", mins, secs),
                color = if (isPaused) ColorWarning else Color.White
            )
        }

        Spacer(Modifier.height(6.dp))
        Row {
            Button(onClick = onPauseToggle, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                Text(if (isPaused) "Devam Et" else "Duraklat", fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onEndWorkout, colors = ButtonDefaults.buttonColors(containerColor = ColorBadForm)) {
                Text("Bitir", fontSize = 12.sp, color = Color.White)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Takip kalitesi
        val (qualityText, qualityColor) = when (trackingQuality) {
            TrackingQuality.GOOD -> "Takip: Mükemmel" to ColorGoodForm
            TrackingQuality.FAIR -> "Takip: Orta" to ColorWarning
            TrackingQuality.POOR -> "Takip Zayıf" to ColorBadForm
            TrackingQuality.LOST -> "Kişi Bulunamadı" to ColorNeutral
        }
        InfoBadge(text = qualityText, color = qualityColor)

        // Kadraj dışı uyarısı
        if (!isInFrame && trackingQuality != TrackingQuality.LOST) {
            Spacer(Modifier.height(4.dp))
            InfoBadge(text = "⚠️ Kadraj dışına çıkıyorsunuz", color = ColorWarning)
        }
    }
}

@Composable
private fun BottomFeedbackPanel(
    feedback: FormFeedback,
    repState: RepetitionState,
    isPersonVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorBackground, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isPersonVisible) {
            StatusText("Kadraja girin", color = ColorWarning)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Form durumu göstergesi
            FormStatusIndicator(isCorrect = feedback.isCorrect, confidence = feedback.confidence)

            // Tekrar sayısı
            RepCountBadge(count = repState.count)
        }

        // Hata mesajı veya olumlu geri bildirim
        if (feedback.feedbackMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = feedback.feedbackMessage,
                color = if (feedback.isCorrect) ColorGoodForm else Color.White,
                fontSize = 15.sp,
                fontWeight = if (feedback.isCorrect) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // İkincil hatalar
        feedback.secondaryErrors.take(2).forEach { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "• $error",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FormStatusIndicator(isCorrect: Boolean, confidence: Float) {
    val color = when {
        confidence < 0.4f -> ColorNeutral
        isCorrect -> ColorGoodForm
        else -> ColorBadForm
    }
    val label = when {
        confidence < 0.4f -> "Belirsiz"
        isCorrect -> "✓ Doğru Form"
        else -> "✗ Hata Var"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun RepCountBadge(count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "tekrar",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun FormScoreIndicator(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> ColorGoodForm
        score >= 50 -> ColorWarning
        else -> ColorBadForm
    }
    Column(
        modifier = modifier
            .background(ColorBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$score",
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "skor",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun InfoBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(ColorBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatusText(text: String, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}
