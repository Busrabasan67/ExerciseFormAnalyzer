package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

/**
 * Shoulder Press form değerlendirici.
 * Oturarak veya ayakta (Seated/Standing) yapılabilir.
 * 
 * Form Kuralları:
 * 1. Karın/Kalça Aktivasyonu: Bel geriye doğru bükülmemeli (korse gibi sıkı tutulmalı).
 * 2. Skapular Düzlem: Dirsekler tam yanlara 180 derece açılmamalı, hafif önde (30-45 derece) tutulmalı.
 * 3. Hareket Aralığı (ROM): Dambıllar omuz hizasına indirilmeli, çok derine inip batma yapılmamalı. Yukarıda dirsek tam kilitlenmemeli.
 * 4. Bilek Pozisyonu: Ön kol (dirsek ile bilek arası) yere tam dik olmalı.
 */
class ShoulderPressEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.SHOULDER_PRESS

    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val profile = AngleUtils.detectBodyProfile(frame)
        val errors = mutableListOf<String>()
        var score = 100

        // Landmarkları baskın olan tarafa göre seç
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val lWrist = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val rWrist = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)

        val lVis = (lShoulder?.visibility ?: 0f) + (lElbow?.visibility ?: 0f) + (lWrist?.visibility ?: 0f)
        val rVis = (rShoulder?.visibility ?: 0f) + (rElbow?.visibility ?: 0f) + (rWrist?.visibility ?: 0f)
        
        val isLeft = lVis >= rVis
        
        val shoulder = if (isLeft) lShoulder else rShoulder
        val elbow = if (isLeft) lElbow else rElbow
        val wrist = if (isLeft) lWrist else rWrist

        if (shoulder == null || elbow == null || wrist == null) return poorTrackingFeedback()

        val elbowAngle = if (isLeft) angles.leftElbowAngle else angles.rightElbowAngle
        if (elbowAngle == null) return poorTrackingFeedback()

        val currentTimeMs = frame.timestampMs
        updateFSM(elbowAngle, currentTimeMs)

        val hip = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)

        // 1. Karın / Kalça Aktivasyonu (Bel Pozisyonu)
        if (profile != BodyProfile.FRONTAL && hip != null && hip.visibility > 0.5f) {
            val shoulderHipDx = abs(shoulder.x - hip.x)
            if (shoulderHipDx > 0.12f) { // Omuz, kalçaya göre x ekseninde çok sapmışsa (yaylanma)
                errors.add("Belini sabit tut")
                score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            }
        }

        // 2. Skapular Düzlem (Dirsek ve Kol Açısı / Omuz Sağlığı)
        if (profile != BodyProfile.FRONTAL) {
            val ear = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
            val nose = frame.landmarkOrNull(PoseLandmarkIndex.NOSE)
            
            if (ear != null && nose != null) {
                // Profil sol veya sağ olabilir, yüzün baktığı yönü bulalım:
                val facingLeft = nose.x < ear.x
                
                // Dirsek omuzun önüne (yüz yönüne) alınmalı, arkasında olmamalı (T pozisyonu)
                val elbowInFront = if (facingLeft) (elbow.x < shoulder.x + 0.05f) else (elbow.x > shoulder.x - 0.05f)
                
                // Hareketin en dip noktasındayken (dirsekler büklüyken) skapular hizayı denetle
                if (!elbowInFront && elbowAngle < 130f) {
                    errors.add("Dirsek açını daralt")
                    score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
                }
            }
        }

        // 3. Hareket Aralığı (ROM - Tepe Noktası ve Derinlik)
        if (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_DOWN) {
            if (elbowAngle < 160f) {
                errors.add("Kollarını tam uzat")
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }
        
        if (repState.phase == RepetitionPhase.BOTTOM || repState.phase == RepetitionPhase.GOING_UP) {
            val wristShoulderDy = abs(wrist.y - shoulder.y)
            if (elbowAngle > 110f && wrist.y < shoulder.y && wristShoulderDy > 0.15f) {
                errors.add("Ağırlığı kulak/omuz hizasına kadar indir")
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
            if (wrist.y > shoulder.y + 0.03f) { // Bilek omuzdan daha aşağıya düşüyorsa
                errors.add("Ağırlığı çok fazla düşürüp omuzlarında batma yaratma")
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        // 4. Bilek Pozisyonu (Ön Kol Dikliği)
        val forearmLength = Math.sqrt(Math.pow((wrist.x - elbow.x).toDouble(), 2.0) + Math.pow((wrist.y - elbow.y).toDouble(), 2.0)).toFloat()
        val horizontalDeviation = abs(wrist.x - elbow.x)
        if (forearmLength > 0.05f) {
            // Eğer ön kol dikey değilse (yatay sapma fazlaysa)
            if (horizontalDeviation > forearmLength * 0.35f) {
                errors.add("Bileklerini bükme, ön kolunu yere tam dik (dik açılı) tut")
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        val primaryError = errors.firstOrNull()
        val isCorrect = errors.isEmpty()

        return FormFeedback(
            isCorrect = isCorrect,
            score = score.coerceAtLeast(0),
            primaryError = primaryError,
            secondaryErrors = if (errors.size > 1) errors.drop(1) else emptyList(),
            feedbackMessage = buildMessage(isCorrect, primaryError, repState.phase),
            confidence = if (trackingQuality == TrackingQuality.GOOD) 0.9f else 0.7f
        )
    }

    private fun updateFSM(elbowAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 300L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle > 120f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle >= 155f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle < 140f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle <= 95f) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.BOTTOM, lastPhaseChangeMs = currentTimeMs)
                    return
                } else repState.phase
            }
            else -> repState.phase
        }

        if (newPhase != repState.phase) {
            repState = repState.copy(phase = newPhase, lastPhaseChangeMs = currentTimeMs)
        }
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE -> "Omuz presi için hazır"
            RepetitionPhase.BOTTOM -> "Güçlü bir şekilde yukarı it!"
            RepetitionPhase.GOING_UP -> "İtiyorsun..."
            RepetitionPhase.TOP -> "Kontrollü bir şekilde indir"
            RepetitionPhase.GOING_DOWN -> "İniyor..."
            RepetitionPhase.RAISED -> ""
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = "Takip zayıf — tam görünün", confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
    }
}
