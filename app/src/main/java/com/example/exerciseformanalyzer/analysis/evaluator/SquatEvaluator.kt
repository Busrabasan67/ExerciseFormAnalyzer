package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils

/**
 * Squat form değerlendirme ve tekrar sayacı.
 *
 * FSM Durumları:
 *   IDLE → TOP (kullanıcı ayağa kalktığında)
 *   TOP → GOING_DOWN (diz bükülmeye başladığında)
 *   GOING_DOWN → BOTTOM (diz yeterince büküldüğünde)
 *   BOTTOM → GOING_UP (diz açılmaya başladığında)
 *   GOING_UP → TOP (tekrar tam ayakta durulduğunda → tekrar sayılır)
 *
 * Form kontrolleri:
 *   1. Yeterince derine inme (diz açısı)
 *   2. Gövde eğimi (aşırı öne yatma)
 *   3. Diz valgus (dizlerin içeri kapanması)
 */
class SquatEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.SQUAT

    private var repState = RepetitionState()
    private var smoothedKneeAngle: Float? = null
    private var didReachDepth = false

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        // Takip kalitesi yetersizse güvenli belirsiz yanıt döndür
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val rawKneeAngle = AngleUtils.dominantKneeAngle(angles, frame)
            ?: return poorTrackingFeedback()

        // EMA yumuşatma — titreme azaltma
        smoothedKneeAngle = smoothedKneeAngle?.let {
            AngleUtils.smoothAngle(rawKneeAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawKneeAngle

        val kneeAngle = smoothedKneeAngle!!
        val currentTimeMs = frame.timestampMs

        // FSM güncelleme
        updateFSM(kneeAngle, currentTimeMs)

        // Form hataları değerlendirme
        val errors = mutableListOf<String>()
        var score = 100

        // 1. Derinlik kontrolü (sadece BOTTOM veya GOING_UP fazında)
        if (repState.phase == RepetitionPhase.BOTTOM || repState.phase == RepetitionPhase.GOING_UP) {
            if (!didReachDepth) {
                errors.add("Daha aşağı in")
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Gövde eğimi kontrolü
        val torso = angles.torsoInclination
        if (torso != null && torso > AnalysisConstants.SQUAT_MAX_TORSO_LEAN) {
            errors.add("Göğsünü daha dik tut")
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 3. Diz valgus kontrolü (dizlerin içeri kapanması)
        val kneeValgus = detectKneeValgus(frame)
        if (kneeValgus) {
            errors.add("Dizlerini dışarı doğru yönlendir")
            score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
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

    private fun updateFSM(kneeAngle: Float, currentTimeMs: Long) {
        val minDuration = AnalysisConstants.MIN_PHASE_DURATION_MS
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs

        if (timeSinceLastChange < minDuration) return  // Çok hızlı geçişi engelle

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.TOP -> {
                if (kneeAngle < AnalysisConstants.SQUAT_KNEE_ANGLE_DOWN_MAX + 20f)
                    RepetitionPhase.GOING_DOWN
                else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                when {
                    kneeAngle <= AnalysisConstants.SQUAT_KNEE_ANGLE_DOWN_MAX -> {
                        didReachDepth = true
                        RepetitionPhase.BOTTOM
                    }
                    kneeAngle >= AnalysisConstants.SQUAT_KNEE_ANGLE_UP_MIN -> RepetitionPhase.TOP
                    else -> repState.phase
                }
            }
            RepetitionPhase.BOTTOM -> {
                if (kneeAngle > AnalysisConstants.SQUAT_KNEE_ANGLE_DOWN_MAX + 10f)
                    RepetitionPhase.GOING_UP
                else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (kneeAngle >= AnalysisConstants.SQUAT_KNEE_ANGLE_UP_MIN) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.TOP, lastPhaseChangeMs = currentTimeMs)
                    didReachDepth = false
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
     * Diz valgus tespiti: Diz x koordinatı, ayak bileği x koordinatından içerideyse valgus var.
     * Normalize koordinatlar kullanıldığı için piksel dönüşümü gereksiz.
     */
    private fun detectKneeValgus(frame: PoseFrame): Boolean {
        val lKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)
        val lAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rKnee = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE)
        val rAnkle = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        val leftValgus = if (lKnee != null && lAnkle != null)
            (lKnee.x - lAnkle.x) > AnalysisConstants.SQUAT_KNEE_VALGUS_THRESHOLD else false
        val rightValgus = if (rKnee != null && rAnkle != null)
            (rAnkle.x - rKnee.x) > AnalysisConstants.SQUAT_KNEE_VALGUS_THRESHOLD else false

        return leftValgus || rightValgus
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE -> "Squat yapmaya başlayın"
            RepetitionPhase.TOP -> "Hazır pozisyon"
            RepetitionPhase.GOING_DOWN -> "İniyorsunuz..."
            RepetitionPhase.BOTTOM -> "Çok iyi! Çıkın"
            RepetitionPhase.GOING_UP -> "Harika form!"
            RepetitionPhase.RAISED -> "Yukarıdasınız"
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false,
        score = 0,
        primaryError = null,
        feedbackMessage = "Takip zayıf — kameraya tam görünün",
        confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        smoothedKneeAngle = null
        didReachDepth = false
    }
}
