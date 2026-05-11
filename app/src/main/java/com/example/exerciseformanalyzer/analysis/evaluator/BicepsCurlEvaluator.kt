package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Biceps Curl form değerlendirme ve tekrar sayacı.
 *
 * FSM Durumları:
 *   IDLE → BOTTOM (Kollar aşağıda)
 *   BOTTOM → GOING_UP (Dirsek bükülmeye başlıyor)
 *   GOING_UP → TOP (Ağırlık omuza yaklaşıyor)
 *   TOP → GOING_DOWN (Ağırlık iniyor)
 *   GOING_DOWN → BOTTOM (Tekrar sayılır)
 *
 * Form kontrolleri:
 *   1. Omuz ve üst kol sabitliği (Üst kolun vücuda yakın kalması, dirseğin öne arkaya çok oynamaması)
 *   2. Tam hareket açıklığı (Aşağıda kolların yeterince açılması)
 */
class BicepsCurlEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.BICEPS_CURL

    private var repState = RepetitionState()
    private var smoothedElbowAngle: Float? = null
    private var didReachTop = false

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        // Dominant dirsek açısını alalım
        val rawElbowAngle = AngleUtils.dominantElbowAngle(angles, frame) ?: return poorTrackingFeedback()

        smoothedElbowAngle = smoothedElbowAngle?.let {
            AngleUtils.smoothAngle(rawElbowAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawElbowAngle

        val elbowAngle = smoothedElbowAngle!!
        val currentTimeMs = frame.timestampMs

        updateFSM(elbowAngle, currentTimeMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Üst Hareket Açıklığı (Top ROM)
        if (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_DOWN) {
            if (!didReachTop) {
                errors.add(stringProvider.getString(R.string.err_pull_dumbbell_to_shoulder))
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Üst kol stabilizasyonu kontrolü
        // (Omuz açısı çok büyükse dirsek öne veya arkaya fazla kaçmış demektir)
        val shoulderAngle = if (frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)?.visibility ?: 0f > frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)?.visibility ?: 0f) {
            angles.leftShoulderAngle
        } else {
            angles.rightShoulderAngle
        } ?: angles.leftShoulderAngle ?: angles.rightShoulderAngle

        if (shoulderAngle != null && shoulderAngle > AnalysisConstants.BICEPS_CURL_MAX_SHOULDER_SWING) {
            errors.add(stringProvider.getString(R.string.err_lock_elbows))
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 3. Gövde salınımı kontrolü
        val torso = angles.torsoInclination
        if (torso != null && torso > 15f && torso < 80f) {
            // Eğer kişi çok öne ya da arkaya eğiliyorsa hile (momentum) kullanıyordur
            errors.add(stringProvider.getString(R.string.err_no_momentum_back))
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
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
        val minDuration = AnalysisConstants.MIN_PHASE_DURATION_MS
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < minDuration) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle < AnalysisConstants.BICEPS_CURL_ELBOW_ANGLE_BOTTOM_MIN - 15f)
                    RepetitionPhase.GOING_UP
                else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                when {
                    elbowAngle <= AnalysisConstants.BICEPS_CURL_ELBOW_ANGLE_TOP_MAX -> {
                        didReachTop = true
                        RepetitionPhase.TOP
                    }
                    elbowAngle >= AnalysisConstants.BICEPS_CURL_ELBOW_ANGLE_BOTTOM_MIN -> RepetitionPhase.BOTTOM
                    else -> repState.phase
                }
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle > AnalysisConstants.BICEPS_CURL_ELBOW_ANGLE_TOP_MAX + 15f)
                    RepetitionPhase.GOING_DOWN
                else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle >= AnalysisConstants.BICEPS_CURL_ELBOW_ANGLE_BOTTOM_MIN) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.BOTTOM, lastPhaseChangeMs = currentTimeMs)
                    didReachTop = false
                    return
                } else repState.phase
            }
            RepetitionPhase.RAISED -> repState.phase
        }

        if (newPhase != repState.phase) {
            repState = repState.copy(phase = newPhase, lastPhaseChangeMs = currentTimeMs)
        }
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE -> stringProvider.getString(R.string.msg_extend_arms)
            RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_ready_lift)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_pulling_you)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_good_contraction)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_controlled_descent)
            RepetitionPhase.RAISED -> stringProvider.getString(R.string.msg_you_are_up)
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false,
        score = 0,
        primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_arms_not_visible),
        confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        smoothedElbowAngle = null
        didReachTop = false
    }
}
