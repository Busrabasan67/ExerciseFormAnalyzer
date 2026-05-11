package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

class ReverseCrunchEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.REVERSE_CRUNCH
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
        val leftKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)

        if (leftHip == null || leftKnee == null || leftShoulder == null) {
            return poorTrackingFeedback(stringProvider.getString(R.string.err_body_not_clear))
        }

        val secondaryErrors = mutableListOf<String>()
        var score = 100
        val isPoorTracking = trackingQuality == TrackingQuality.POOR

        // Tekrar Sayma Mantığı (Diz-Omuz Oranı Bazlı)
        updateRepetitionState(frame, frame.timestampMs)

        return FormFeedback(
            isCorrect = score > 70,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = if (secondaryErrors.isEmpty()) stringProvider.getString(R.string.msg_lower_abs_active) else secondaryErrors.first(),
            confidence = if (isPoorTracking) 0.45f else 0.85f
        )
    }

    private fun updateRepetitionState(frame: PoseFrame, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 400L) return

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val lKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)
        
        if (lShoulder == null || lHip == null || lKnee == null) return

        // Omuz-Kalça mesafesi (Gövde uzunluğu referansı)
        val torsoDist = Math.sqrt(Math.pow((lShoulder.x - lHip.x).toDouble(), 2.0) + Math.pow((lShoulder.y - lHip.y).toDouble(), 2.0))
        
        // Diz-Omuz mesafesi (Dizler göğse yaklaştıkça bu mesafe kısalır)
        val kneeToShoulderDist = Math.sqrt(Math.pow((lShoulder.x - lKnee.x).toDouble(), 2.0) + Math.pow((lShoulder.y - lKnee.y).toDouble(), 2.0))
        
        // Oran (Dizlerin gövdeye göre ne kadar çekildiğini gösterir)
        val crunchRatio = kneeToShoulderDist / torsoDist

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Dizler göğse çekildiğinde (Oran %65'in altına düştüğünde)
                if (crunchRatio < 0.65f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Bacaklar tekrar indirildiğinde (Oran %100'ün üzerine çıktığında)
                if (crunchRatio > 1.0f) {
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
