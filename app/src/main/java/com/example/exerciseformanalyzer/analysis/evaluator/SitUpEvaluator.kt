package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

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
class SitUpEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.SIT_UP

    private var repState = RepetitionState()
    private var smoothedTorsoAngle: Float? = null
    private var isCurrentRepValid = true

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        // Kalça açısı yerine gövde eğimini (torso inclination) kullanmak mekik için çok daha stabildir
        // Çünkü dizlerin bükülme açısına bağlı değildir. Sadece sırtın yerden ne kadar kalktığını ölçer.
        val rawTorsoAngle = angles.torsoInclination ?: return poorTrackingFeedback()

        smoothedTorsoAngle = smoothedTorsoAngle?.let {
            AngleUtils.smoothAngle(rawTorsoAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawTorsoAngle

        val torsoAngle = smoothedTorsoAngle!!
        updateFSM(torsoAngle, frame.timestampMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Hareket tamamlanma kontrolü
        if (repState.phase == RepetitionPhase.TOP) {
            if (torsoAngle > AnalysisConstants.SIT_UP_TOP_ANGLE_MAX + 15f) {
                errors.add(stringProvider.getString(R.string.err_complete_movement))
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Boyun gerginliği kontrolü (baş, omuzdan çok yukarıda mı?)
        val neckStrain = detectNeckStrain(frame)
        if (neckStrain) {
            errors.add(stringProvider.getString(R.string.err_dont_pull_neck))
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        }

        val primaryError = errors.firstOrNull()
        val isCorrect = errors.isEmpty()

        // Eğer hareket sırasında form bozulduysa tekrarı geçersiz say
        if (!isCorrect && repState.phase != RepetitionPhase.IDLE && repState.phase != RepetitionPhase.BOTTOM) {
            isCurrentRepValid = false
        }

        return FormFeedback(
            isCorrect = isCorrect,
            score = score.coerceAtLeast(0),
            primaryError = primaryError,
            secondaryErrors = if (errors.size > 1) errors.drop(1) else emptyList(),
            feedbackMessage = buildMessage(isCorrect, primaryError, repState.phase),
            confidence = if (trackingQuality == TrackingQuality.GOOD) 0.85f else 0.65f
        )
    }

    private fun updateFSM(torsoAngle: Float, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < AnalysisConstants.MIN_PHASE_DURATION_MS) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                // Yerde yatarken açı 90'a yakındır. Kalkmaya başlarsa açı küçülür.
                if (torsoAngle < AnalysisConstants.SIT_UP_BOTTOM_ANGLE_MIN - 10f) {
                    isCurrentRepValid = true
                    RepetitionPhase.GOING_UP
                } else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                // Açı küçülerek hedefe ulaştığında TOP olur (Sırt yerden kalktı)
                if (torsoAngle <= AnalysisConstants.SIT_UP_TOP_ANGLE_MAX)
                    RepetitionPhase.TOP
                else repState.phase
            }
            RepetitionPhase.TOP -> {
                // Açı tekrar büyümeye başladığında (Geri yatarken)
                if (torsoAngle > AnalysisConstants.SIT_UP_TOP_ANGLE_MAX + 10f)
                    RepetitionPhase.GOING_DOWN
                else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                // Açı başlangıç değerine (yatay konuma) ulaştığında sayacı artır
                if (torsoAngle >= AnalysisConstants.SIT_UP_BOTTOM_ANGLE_MIN) {
                    val newCount = if (isCurrentRepValid) repState.count + 1 else repState.count
                    repState = RepetitionState(
                        count = newCount,
                        phase = RepetitionPhase.BOTTOM,
                        lastPhaseChangeMs = currentTimeMs
                    )
                    isCurrentRepValid = true
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
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_horizontal_ready)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_rising)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_great_go_down)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_go_down_controlled)
            RepetitionPhase.RAISED -> stringProvider.getString(R.string.msg_you_are_up)
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking_camera), confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        smoothedTorsoAngle = null
        isCurrentRepValid = true
    }
}
