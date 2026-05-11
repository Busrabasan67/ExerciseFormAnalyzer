package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Triceps Extension (Overhead) form değerlendirici.
 */
class TricepsExtensionEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.TRICEPS_EXTENSION

    private var repState = RepetitionState()
    private var maxElbowAngleInRep = 0f

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val elbowAngle = AngleUtils.dominantElbowAngle(angles, frame) ?: return poorTrackingFeedback()
        val currentTimeMs = frame.timestampMs
        updateFSM(elbowAngle, currentTimeMs)

        if (repState.phase == RepetitionPhase.GOING_UP || repState.phase == RepetitionPhase.TOP) {
            if (elbowAngle > maxElbowAngleInRep) maxElbowAngleInRep = elbowAngle
        }

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Dirsek Sabitliği (Kollar başın yanında sabit mi?)
        val shoulderAngle = AngleUtils.dominantShoulderAngle(angles, frame)
        if (shoulderAngle != null && shoulderAngle < 150f) {
            errors.add(stringProvider.getString(R.string.err_keep_elbows_stable))
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 2. Boyun Pozisyonu (Boyun öne gidiyor mu?)
        val nose = frame.landmarkOrNull(PoseLandmarkIndex.NOSE)
        val shoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER) // Veya orta nokta
        if (nose != null && shoulder != null) {
            if (nose.y > shoulder.y + 0.05f) {
                errors.add(stringProvider.getString(R.string.err_head_up))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        // 3. Yarım Tekrar Kontrolü
        if (repState.phase == RepetitionPhase.GOING_DOWN && maxElbowAngleInRep < 150f && maxElbowAngleInRep > 0f) {
            errors.add(stringProvider.getString(R.string.err_full_extension))
            score -= AnalysisConstants.SCORE_PENALTY_DEPTH
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
                if (elbowAngle > 60f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle >= 160f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle < 140f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle <= 50f) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.BOTTOM, lastPhaseChangeMs = currentTimeMs)
                    maxElbowAngleInRep = 0f
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
        RepetitionPhase.IDLE -> stringProvider.getString(R.string.msg_ready_triceps_ext)
        RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_push_up)
        RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_opening)
        RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_lower_controlled)
        RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_going_down)
        else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking), confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState
    override fun reset() {
        repState = RepetitionState()
        maxElbowAngleInRep = 0f
    }
}
