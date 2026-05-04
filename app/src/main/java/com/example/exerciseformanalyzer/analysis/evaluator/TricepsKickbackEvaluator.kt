package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

/**
 * Triceps Kickback form değerlendirici.
 */
class TricepsKickbackEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.TRICEPS_KICKBACK

    private var repState = RepetitionState()
    private var maxExtensionInRep = 0f

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val elbowAngle = AngleUtils.dominantElbowAngle(angles, frame) ?: return poorTrackingFeedback()
        val torso = angles.torsoInclination ?: 0f
        val currentTimeMs = frame.timestampMs
        updateFSM(elbowAngle, currentTimeMs)

        if (repState.phase == RepetitionPhase.GOING_UP) {
            if (elbowAngle > maxExtensionInRep) maxExtensionInRep = elbowAngle
        }

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Üst Kol Sabitliği (Omuz açısı kontrolü)
        val shoulderAngle = AngleUtils.dominantShoulderAngle(angles, frame)
        if (shoulderAngle != null && shoulderAngle > 35f) {
            errors.add("Üst kolunu sabit tut")
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 2. Gövde Eğimi (Öne eğilmeyi koruyor mu?)
        if (torso < 35f) {
            errors.add("Öne eğilmeyi koru")
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        }

        // 3. Eksik Açılma
        if (repState.phase == RepetitionPhase.GOING_DOWN && maxExtensionInRep < 155f) {
            errors.add("Kolunu tamamen aç")
            score -= AnalysisConstants.SCORE_PENALTY_DEPTH
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
                if (elbowAngle > 110f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle >= 165f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle < 150f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle <= 95f) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.BOTTOM, lastPhaseChangeMs = currentTimeMs)
                    maxExtensionInRep = 0f
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
        RepetitionPhase.IDLE -> "Triceps Kickback için hazır"
        RepetitionPhase.BOTTOM -> "Geriye doğru aç"
        RepetitionPhase.GOING_UP -> "İtiyorsun..."
        RepetitionPhase.TOP -> "Kasılmayı hisset ve dön"
        RepetitionPhase.GOING_DOWN -> "Kontrollü dönüş..."
        else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = "Takip zayıf", confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState
    override fun reset() {
        repState = RepetitionState()
        maxExtensionInRep = 0f
    }
}
