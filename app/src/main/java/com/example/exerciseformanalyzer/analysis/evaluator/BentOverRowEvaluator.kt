package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Bent Over Row form değerlendirici.
 */
class BentOverRowEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.BENT_OVER_ROW

    private var repState = RepetitionState()

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

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Sırt Düzlüğü (Sırt yuvarlanıyor mu?)
        // Bu 2D pozeda zor ama torso açısı ile hip/shoulder hizasından bir çıkarım yapılabilir.
        // Eğer torso açısı çok dengesiz ise (titreme) veya kalça çok aşağıda ise uyarı verilebilir.
        // Şimdilik torso açısı üzerinden gövde eğimini kontrol edelim.
        if (torso < 30f || torso > 65f) {
            errors.add(stringProvider.getString(R.string.err_lean_flat_back))
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 2. Çekiş Yönü (Dirsekleri geriye çekiyor mu?)
        val shoulder = AngleUtils.dominantLandmark(frame, PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)
        val elbow = AngleUtils.dominantLandmark(frame, PoseLandmarkIndex.LEFT_ELBOW, PoseLandmarkIndex.RIGHT_ELBOW)
        val wrist = AngleUtils.dominantLandmark(frame, PoseLandmarkIndex.LEFT_WRIST, PoseLandmarkIndex.RIGHT_WRIST)
        
        if (shoulder != null && elbow != null && wrist != null) {
            if (repState.phase == RepetitionPhase.GOING_UP || repState.phase == RepetitionPhase.TOP) {
                // Bilek omuzun çok önündeyse (yüz yönüne doğru) aşağı çekiliyordur
                if (abs(wrist.x - shoulder.x) < 0.05f && elbow.y > shoulder.y) {
                    errors.add(stringProvider.getString(R.string.err_pull_elbows_back))
                    score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
                }
            }
        }

        // 3. Boyun Pozisyonu (Başını nötr tutuyor mu?)
        val nose = frame.landmarkOrNull(PoseLandmarkIndex.NOSE)
        if (nose != null && shoulder != null) {
            if (nose.y > shoulder.y + 0.1f) {
                errors.add(stringProvider.getString(R.string.err_head_neutral))
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
            feedbackMessage = if (isCorrect) buildPhaseMessage(repState.phase) else primaryError ?: stringProvider.getString(R.string.err_fix_form),
            confidence = 0.85f
        )
    }

    private fun updateFSM(elbowAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 300L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle < 150f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle <= 100f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle > 115f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle >= 165f) {
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
        RepetitionPhase.IDLE -> stringProvider.getString(R.string.msg_ready_bent_row)
        RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_pull_back)
        RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_pulling)
        RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_squeeze_back)
        RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_release_slowly)
        else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking), confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState
    override fun reset() {
        repState = RepetitionState()
    }
}
