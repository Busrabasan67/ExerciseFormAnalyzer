package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

/**
 * Bent Over Raise (Rear Delt Fly) form değerlendirici.
 */
class BentOverRaiseEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.BENT_OVER_RAISE

    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val shoulderAngle = AngleUtils.dominantShoulderAngle(angles, frame) ?: return poorTrackingFeedback()
        val torso = angles.torsoInclination ?: 0f
        val currentTimeMs = frame.timestampMs
        updateFSM(shoulderAngle, currentTimeMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Sırt Bozuluyor mu? (Pozisyonunu koruyor mu?)
        if (torso < 35f || torso > 70f) {
            errors.add("Pozisyonunu koru")
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 2. Momentum (Yavaş kaldırıyor mu?)
        // Bu genelde zaman bazlı kontrol edilebilir ama basitçe omuz açısının aşırı hızlı artışına bakılabilir.
        // Şimdilik genel mesaj olarak veriyoruz.

        // 3. Omuz yerine kol kullanımı (Dirsek açısı çok bükülü mü?)
        val elbowAngle = AngleUtils.dominantElbowAngle(angles, frame)
        if (elbowAngle != null && elbowAngle < 130f) {
            errors.add("Omuzdan aç, dirseklerini çok bükme")
            score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
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

    private fun updateFSM(shoulderAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 300L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (shoulderAngle > 30f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (shoulderAngle >= 75f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (shoulderAngle < 65f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (shoulderAngle <= 25f) {
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
        RepetitionPhase.IDLE -> "Bent Over Raise için hazır"
        RepetitionPhase.BOTTOM -> "Yana aç"
        RepetitionPhase.GOING_UP -> "Açılıyor..."
        RepetitionPhase.TOP -> "Kürek kemiklerini sık"
        RepetitionPhase.GOING_DOWN -> "Yavaşça indir"
        else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = "Takip zayıf", confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState
    override fun reset() {
        repState = RepetitionState()
    }
}
