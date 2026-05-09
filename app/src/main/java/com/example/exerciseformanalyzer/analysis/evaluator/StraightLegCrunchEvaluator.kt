package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

class StraightLegCrunchEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.STRAIGHT_LEG_CRUNCH
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
        val leftHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val leftKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)

        if (leftHip == null || leftKnee == null || leftShoulder == null) {
            return poorTrackingFeedback("Vücut net görünmüyor")
        }

        val secondaryErrors = mutableListOf<String>()
        var score = 100
        val isPoorTracking = trackingQuality == TrackingQuality.POOR

        val legAngle = AngleUtils.calculateAngle(leftHip, leftKnee, Landmark(leftHip.x + 1f, leftHip.y, 0f))
        
        if (legAngle < 70f || legAngle > 110f) {
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
            secondaryErrors.add("Bacaklarınızı 90 derece dik tutun")
        }
        updateRepetitionState(frame, frame.timestampMs)

        return FormFeedback(
            isCorrect = score > 70,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = if (secondaryErrors.isEmpty()) "Harika yükseliş!" else secondaryErrors.first(),
            confidence = if (isPoorTracking) 0.45f else 0.85f
        )
    }

    private fun updateRepetitionState(frame: PoseFrame, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 400L) return

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)

        if (lShoulder == null || rShoulder == null || lHip == null || rHip == null) return

        // Omuz ve kalça merkez noktaları
        val shoulderY = (lShoulder.y + rShoulder.y) / 2f
        val hipY = (lHip.y + rHip.y) / 2f
        
        // Gerçek gövde uzunluğu (Diyagonal mesafe)
        val torsoLength = Math.sqrt(
            Math.pow((lShoulder.x - lHip.x).toDouble(), 2.0) + 
            Math.pow((lShoulder.y - lHip.y).toDouble(), 2.0)
        )
        
        // Dikey yükselme miktarı (Mekik hareketi)
        // Sırt üstü yatarken omuz ve kalça Y koordinatları birbirine yakındır (Oran ≈ 0)
        // Kalktığınızda omuz Y değeri azalır (yukarı gider), fark artar.
        val liftAmount = Math.abs(hipY - shoulderY)
        val liftRatio = liftAmount / torsoLength

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Omuzlar yerden belirgin şekilde kalktığında (Oran > 0.12)
                if (liftRatio > 0.12f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Tekrar yere yatıldığında (Oran < 0.05)
                if (liftRatio < 0.05f) {
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
