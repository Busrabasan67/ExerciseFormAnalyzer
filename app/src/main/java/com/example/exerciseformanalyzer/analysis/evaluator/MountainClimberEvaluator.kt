package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

class MountainClimberEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.MOUNTAIN_CLIMBER
    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST) {
            return poorTrackingFeedback(stringProvider.getString(R.string.err_person_not_found))
        }

        val leftShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val leftHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val leftAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rightKnee = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE)
        val leftKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)

        if (leftShoulder == null || leftHip == null) {
            return poorTrackingFeedback(stringProvider.getString(R.string.err_body_not_clear))
        }

        val secondaryErrors = mutableListOf<String>()
        var score = 100
        val isPoorTracking = trackingQuality == TrackingQuality.POOR

        val hipDeviation = if (leftAnkle != null) {
            AngleUtils.calculateHipDeviation(
                shoulder = leftShoulder,
                hip = leftHip,
                ankle = leftAnkle
            )
        } else 0f

        if (hipDeviation < -AnalysisConstants.MOUNTAIN_CLIMBER_HIP_MAX_RISE) {
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            secondaryErrors.add(stringProvider.getString(R.string.err_dont_raise_hips))
        } else if (hipDeviation > AnalysisConstants.MOUNTAIN_CLIMBER_HIP_MAX_SAG) {
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            secondaryErrors.add(stringProvider.getString(R.string.err_dont_drop_waist))
        }

        updateRepetitionState(frame, angles, frame.timestampMs)

        return FormFeedback(
            isCorrect = score > 75,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = if (secondaryErrors.isEmpty()) stringProvider.getString(R.string.msg_form_correct) else secondaryErrors.first(),
            confidence = if (isPoorTracking) 0.45f else 0.85f
        )
    }

    private fun updateRepetitionState(frame: PoseFrame, angles: JointAngles, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 100L) return

        // Hem sol hem sağ kalça açısını kontrol et
        val leftHipAngle = angles.leftHipAngle ?: 180f
        val rightHipAngle = angles.rightHipAngle ?: 180f
        
        // En dar açı (çekilen bacak) bizim için belirleyici
        val minHipAngle = minOf(leftHipAngle, rightHipAngle)

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Diz göğse iyice yaklaştığında (Açı < 85 derece)
                if (minHipAngle < 85f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Bacak tekrar uzatıldığında (Açı > 130 derece)
                if (minHipAngle > 130f) {
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
