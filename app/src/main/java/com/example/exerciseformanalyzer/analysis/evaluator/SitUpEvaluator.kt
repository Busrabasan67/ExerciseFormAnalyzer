package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils

/**
 * Sit-up / Crunch (Mekik) form değerlendirme ve tekrar sayacı.
 *
 * Yatar pozisyondan gövdenin omuz-kalça açısını izler.
 *
 * FSM Durumları:
 *   BOTTOM (yerde yatar) → GOING_UP → TOP (oturmuş) → GOING_DOWN → BOTTOM (tekrar sayılır)
 *
 * Form kontrolleri:
 *   1. Hareketin tamamlanması (yeterince yukarı çıkma)
 *   2. Boyun gerginliği (baş ve omuz hizası)
 *   3. Kontrollü inme
 */
class SitUpEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.SIT_UP

    private var repState = RepetitionState()
    private var smoothedHipAngle: Float? = null

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        // Sol veya sağ kalça açısını kullan
        val rawHipAngle = angles.leftHipAngle ?: angles.rightHipAngle
            ?: return poorTrackingFeedback()

        smoothedHipAngle = smoothedHipAngle?.let {
            AngleUtils.smoothAngle(rawHipAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawHipAngle

        val hipAngle = smoothedHipAngle!!
        updateFSM(hipAngle, frame.timestampMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Hareket tamamlanma kontrolü
        if (repState.phase == RepetitionPhase.TOP) {
            if (hipAngle > AnalysisConstants.SIT_UP_TOP_ANGLE_MAX + 20f) {
                errors.add("Hareketi tamamla")
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Boyun gerginliği kontrolü (baş, omuzdan çok yukarıda mı?)
        val neckStrain = detectNeckStrain(frame)
        if (neckStrain) {
            errors.add("Boynunu çekme")
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
            confidence = if (trackingQuality == TrackingQuality.GOOD) 0.85f else 0.65f
        )
    }

    private fun updateFSM(hipAngle: Float, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < AnalysisConstants.MIN_PHASE_DURATION_MS) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (hipAngle < AnalysisConstants.SIT_UP_BOTTOM_ANGLE_MIN - 20f)
                    RepetitionPhase.GOING_UP
                else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (hipAngle <= AnalysisConstants.SIT_UP_TOP_ANGLE_MAX)
                    RepetitionPhase.TOP
                else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (hipAngle > AnalysisConstants.SIT_UP_TOP_ANGLE_MAX + 20f)
                    RepetitionPhase.GOING_DOWN
                else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (hipAngle >= AnalysisConstants.SIT_UP_BOTTOM_ANGLE_MIN) {
                    repState = RepetitionState(
                        count = repState.count + 1,
                        phase = RepetitionPhase.BOTTOM,
                        lastPhaseChangeMs = currentTimeMs
                    )
                    return
                } else repState.phase
            }
            RepetitionPhase.RAISED -> repState.phase
        }

        if (newPhase != repState.phase) {
            repState = repState.copy(phase = newPhase, lastPhaseChangeMs = currentTimeMs)
        }
    }

    /**
     * Boyun gerginliğini tespit eder.
     * Burun (head) y koordinatı, sol-sağ omuz orta noktasının çok üstündeyse boyun zorlanıyor.
     * Normalize koordinatlarda daha küçük y = daha yukarı.
     */
    private fun detectNeckStrain(frame: PoseFrame): Boolean {
        val nose = frame.landmarkOrNull(PoseLandmarkIndex.NOSE) ?: return false
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER) ?: return false
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER) ?: return false
        val shoulderMidY = (lShoulder.y + rShoulder.y) / 2f
        // Burnun omuzdan çok daha yukarıda olması boyun çekişine işaret eder (sit-up'ta)
        // 0.10 normalize fark eşiği — ampirik değer
        return (shoulderMidY - nose.y) > 0.30f
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> "Yatay pozisyon — hazır"
            RepetitionPhase.GOING_UP -> "Kalkıyorsunuz..."
            RepetitionPhase.TOP -> "Harika! İnin"
            RepetitionPhase.GOING_DOWN -> "Kontrollü inin"
            RepetitionPhase.RAISED -> "Yukarıdasınız"
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = "Takip zayıf — kameraya tam görünün", confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        smoothedHipAngle = null
    }
}
