package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

/**
 * Hammer Curl form değerlendirme ve tekrar sayacı.
 */
class HammerCurlEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.HAMMER_CURL

    private var repState = RepetitionState()
    private var smoothedElbowAngle: Float? = null

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val rawElbowAngle = AngleUtils.dominantElbowAngle(angles, frame) ?: return poorTrackingFeedback()
        smoothedElbowAngle = smoothedElbowAngle?.let {
            AngleUtils.smoothAngle(rawElbowAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawElbowAngle

        val elbowAngle = smoothedElbowAngle!!
        val currentTimeMs = frame.timestampMs
        updateFSM(elbowAngle, currentTimeMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Dirsek Sabitliği (Dirsek öne gidiyor mu?)
        val shoulderAngle = AngleUtils.dominantShoulderAngle(angles, frame)
        if (shoulderAngle != null && shoulderAngle > 25f) {
            errors.add("Dirseklerini sabit tut")
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 2. Vücut Sallanıyor mu? (Momentum)
        val torso = angles.torsoInclination
        if (torso != null && (torso > 12f && torso < 85f)) {
            errors.add("Momentum kullanma")
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        }

        // 3. Bilek Kontrolü (Bilek kırılıyor mu?) - Yatay sapma kontrolü
        val isLeft = (frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)?.visibility ?: 0f) > (frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)?.visibility ?: 0f)
        val elbow = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val wrist = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)
        
        if (elbow != null && wrist != null) {
            val horizontalDev = abs(elbow.x - wrist.x)
            if (horizontalDev > 0.15f && elbowAngle < 150f) {
                errors.add("Bileğini düz tut")
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
            feedbackMessage = if (isCorrect) buildPhaseMessage(repState.phase) else primaryError ?: "Formu düzeltin",
            confidence = 0.85f
        )
    }

    private fun updateFSM(elbowAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 300L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle < 140f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle <= 45f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle > 60f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle >= 155f) {
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

    private fun buildPhaseMessage(phase: RepetitionPhase) = when (phase) {
        RepetitionPhase.IDLE -> "Hammer Curl için hazır"
        RepetitionPhase.BOTTOM -> "Kaldırın"
        RepetitionPhase.GOING_UP -> "Çek..."
        RepetitionPhase.TOP -> "Yavaşça indir"
        RepetitionPhase.GOING_DOWN -> "İniyor..."
        else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = "Takip zayıf", confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState
    override fun reset() {
        repState = RepetitionState()
        smoothedElbowAngle = null
    }
}
