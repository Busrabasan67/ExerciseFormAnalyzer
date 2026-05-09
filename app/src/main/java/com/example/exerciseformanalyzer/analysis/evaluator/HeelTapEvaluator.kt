package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs

class HeelTapEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.HEEL_TAP
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
        val rightShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)

        if (leftShoulder == null || rightShoulder == null) {
            return poorTrackingFeedback("Omuzlar görünmüyor")
        }
        
        val isPoorTracking = trackingQuality == TrackingQuality.POOR

        val secondaryErrors = mutableListOf<String>()
        var score = 100

        updateRepetitionState(frame, frame.timestampMs)

        return FormFeedback(
            isCorrect = score > 70,
            score = score.coerceIn(0, 100),
            primaryError = secondaryErrors.firstOrNull(),
            secondaryErrors = secondaryErrors,
            feedbackMessage = if (secondaryErrors.isEmpty()) "Oblikleri sıkıştırın!" else secondaryErrors.first(),
            confidence = 0.8f
        )
    }

    private fun updateRepetitionState(frame: PoseFrame, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < 250L) return

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)

        if (lShoulder == null || rShoulder == null || lHip == null || rHip == null) return

        // Sol ve sağ tarafın "sıkışma" miktarını ölç (Omuz-Kalça mesafesi)
        val lSideDist = Math.sqrt(Math.pow((lShoulder.x - lHip.x).toDouble(), 2.0) + Math.pow((lShoulder.y - lHip.y).toDouble(), 2.0))
        val rSideDist = Math.sqrt(Math.pow((rShoulder.x - rHip.x).toDouble(), 2.0) + Math.pow((rShoulder.y - rHip.y).toDouble(), 2.0))
        
        // İki yan arasındaki oran (Yanlara eğilmeyi en iyi bu oran verir)
        val sideRatio = lSideDist / rSideDist

        when (repState.phase) {
            RepetitionPhase.IDLE -> {
                // Belirgin bir yana eğilme (Oran %15'ten fazla saptığında)
                if (sideRatio < 0.82f || sideRatio > 1.22f) {
                    repState = repState.copy(phase = RepetitionPhase.GOING_UP, lastPhaseChangeMs = currentTimeMs)
                }
            }
            RepetitionPhase.GOING_UP -> {
                // Tekrar merkeze dönüldüğünde (Oran normale döndüğünde)
                if (sideRatio in 0.92f..1.08f) {
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
