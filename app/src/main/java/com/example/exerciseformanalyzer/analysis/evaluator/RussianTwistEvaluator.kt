package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

/**
 * Russian Twist analizi yapan sınıf.
 * Odak noktaları: Gövde rotasyonu, sırt dikliği ve denge.
 */
class RussianTwistEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.RUSSIAN_TWIST
    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST) {
            return poorTrackingFeedback("Kişi bulunamadı")
        }

        val leftShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val rightShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)

        if (leftShoulder == null || rightShoulder == null) {
            return poorTrackingFeedback("Omuzlar görünmüyor")
        }

        val secondaryErrors = mutableListOf<String>()
        var score = 100
        val isPoorTracking = trackingQuality == TrackingQuality.POOR

        // 1. Sırt Dikliği Kontrolü (Torso Inclination)
        val torsoAngle = angles.torsoInclination
        if (torsoAngle != null && torsoAngle > AnalysisConstants.RUSSIAN_TWIST_MAX_BACK_LEAN) {
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            secondaryErrors.add("Sırt çok geride")
        } else if (torsoAngle != null && torsoAngle < AnalysisConstants.RUSSIAN_TWIST_MIN_BACK_ANGLE) {
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            secondaryErrors.add("Sırtınız kambur duruyor, dikleşin")
        }

        // 2. Rotasyon Kontrolü (Normalleştirilmiş Oran)
        // Omuzlar arası X mesafesinin, gerçek 2D mesafeye oranı
        val shoulderWidth2D = Math.sqrt(
            Math.pow((leftShoulder.x - rightShoulder.x).toDouble(), 2.0) +
            Math.pow((leftShoulder.y - rightShoulder.y).toDouble(), 2.0)
        ).toFloat().coerceAtLeast(0.01f)
        
        val rotationRatio = abs(leftShoulder.x - rightShoulder.x) / shoulderWidth2D
        
        // 3. Tekrar Sayma Mantığı
        updateRepetitionState(rotationRatio, frame.timestampMs)

        val feedbackMessage = if (secondaryErrors.isEmpty()) "Harika rotasyon!" else secondaryErrors.first()

        return FormFeedback(
            isCorrect = score > 70,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = feedbackMessage,
            confidence = if (isPoorTracking) 0.45f else 0.85f
        )
    }

    private fun updateRepetitionState(rotationRatio: Float, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 300L) return

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Oran düştükçe (0.6'nın altı) vücut yana dönmüş demektir
                if (rotationRatio < 0.6f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Oran tekrar yükseldiğinde (0.85'in üstü) vücut merkeze dönmüş demektir
                if (rotationRatio > 0.85f) {
                    repState = repState.copy(
                        count = repState.count + 1,
                        phase = RepetitionPhase.IDLE,
                        lastPhaseChangeMs = currentTimeMs
                    )
                }
            }
            else -> repState = repState.copy(phase = RepetitionPhase.IDLE)
        }
    }

    private fun poorTrackingFeedback(msg: String) = FormFeedback(
        isCorrect = false,
        score = 0,
        primaryError = null,
        feedbackMessage = msg,
        confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
    }
}
