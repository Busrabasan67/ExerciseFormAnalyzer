package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

class BicycleCrunchEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.BICYCLE_CRUNCH
    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST) {
            return poorTrackingFeedback("Kişi bulunamadı")
        }

        val leftHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rightHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)
        val leftKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)
        val rightKnee = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE)

        if (leftHip == null || rightHip == null) {
            return poorTrackingFeedback("Vücut net görünmüyor")
        }

        val secondaryErrors = mutableListOf<String>()
        var score = 100

        val leftAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val legAngle = if (leftAnkle != null) {
            AngleUtils.calculateAngle(leftHip, leftAnkle, Landmark(leftAnkle.x + 1f, leftAnkle.y, 0f))
        } else 180f
        
        if (legAngle < AnalysisConstants.BICYCLE_CRUNCH_LEG_ANGLE_MIN) {
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
            secondaryErrors.add("Bacağınız yere çok yakın")
        } else if (legAngle > AnalysisConstants.BICYCLE_CRUNCH_LEG_ANGLE_MAX) {
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
            secondaryErrors.add("Bacağınızı daha aşağı indirin")
        }

        updateRepetitionState(angles, frame.timestampMs)

        return FormFeedback(
            isCorrect = score > 70,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = if (secondaryErrors.isEmpty()) "Harika ritim!" else secondaryErrors.first(),
            confidence = 0.8f
        )
    }

    private fun updateRepetitionState(angles: JointAngles, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 200L) return

        val leftHipAngle = angles.leftHipAngle ?: 120f
        val rightHipAngle = angles.rightHipAngle ?: 120f
        
        // İki bacak arasındaki açı farkı (Makas hareketi tespiti)
        val angleDiff = leftHipAngle - rightHipAngle

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Sol bacak çekili, sağ bacak uzatılmış (Fark negatif ve büyük)
                if (angleDiff < -40f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Bacaklar yer değiştirdiğinde (Fark pozitif ve büyük)
                if (angleDiff > 40f) {
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
