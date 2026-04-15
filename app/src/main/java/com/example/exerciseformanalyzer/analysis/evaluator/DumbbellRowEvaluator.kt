package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils

/**
 * Dumbbell Row form değerlendirme ve tekrar sayacı.
 *
 * Tek kol sırt hareketi — dominant taraf (daha görünür dirsek) analiz edilir.
 *
 * FSM Durumları:
 *   TOP (kol uzanmış) → GOING_DOWN (kol aşağıya) →
 *   BOTTOM (kol tam uzamış) → GOING_UP (dirsek geri çekiliyor) →
 *   TOP (tekrar sayılır)
 *
 * Not: "TOP" bu egzersizde dirseğin en geriye çekildiği noktadır.
 *
 * Form kontrolleri:
 *   1. Dirseğin yeterince geri çekilmesi
 *   2. Omuzun kulağa yükselmemesi (shrug)
 *   3. Gövde rotasyonu (sırt sabit mi?)
 */
class DumbbellRowEvaluator : ExerciseEvaluator {

    override val exerciseType = ExerciseType.DUMBBELL_ROW

    private var repState = RepetitionState()
    private var smoothedElbowAngle: Float? = null

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val rawElbow = AngleUtils.dominantElbowAngle(angles, frame)
            ?: return poorTrackingFeedback()

        smoothedElbowAngle = smoothedElbowAngle?.let {
            AngleUtils.smoothAngle(rawElbow, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawElbow

        val elbowAngle = smoothedElbowAngle!!
        updateFSM(elbowAngle, frame.timestampMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Dirsek ROM kontrolü (TOP fazında — çekilmiş pozisyon)
        if (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_UP) {
            if (elbowAngle > AnalysisConstants.ROW_ELBOW_PULLED_MAX) {
                errors.add("Dirseğini daha geriye çek")
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Omuz shrug kontrolü
        val shoulderShrug = detectShoulderShrug(frame)
        if (shoulderShrug) {
            errors.add("Omzunu yukarı kaldırma")
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 3. Gövde rotasyon kontrolü
        val torsoRotation = detectTorsoRotation(frame)
        if (torsoRotation) {
            errors.add("Sırtını sabit tut")
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
            confidence = if (trackingQuality == TrackingQuality.GOOD) 0.85f else 0.65f
        )
    }

    private fun updateFSM(elbowAngle: Float, currentTimeMs: Long) {
        if (currentTimeMs - repState.lastPhaseChangeMs < AnalysisConstants.MIN_PHASE_DURATION_MS) return

        // Row'da TOP = dirsek geri çekilmiş (küçük açı), BOTTOM = kol uzanmış (büyük açı)
        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle < 150f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle <= AnalysisConstants.ROW_ELBOW_PULLED_MAX + 10f)
                    RepetitionPhase.TOP
                else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle > AnalysisConstants.ROW_ELBOW_PULLED_MAX + 20f)
                    RepetitionPhase.GOING_DOWN
                else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle >= 160f) {
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
     * Omuz shrug tespiti: Kulak ile omuz arasındaki y mesafesi çok küçükse omuz yukarı gitmiştir.
     * Normalize koordinatlarda küçük y farkı = omuz kulağa yakın.
     */
    private fun detectShoulderShrug(frame: PoseFrame): Boolean {
        // Dominant taraf — daha görünür olan
        val lEar = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR)
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val rEar = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)

        val leftShrug = if (lEar != null && lShoulder != null)
            (lShoulder.y - lEar.y) < AnalysisConstants.ROW_SHOULDER_SHRUG_THRESHOLD
        else false

        val rightShrug = if (rEar != null && rShoulder != null)
            (rShoulder.y - rEar.y) < AnalysisConstants.ROW_SHOULDER_SHRUG_THRESHOLD
        else false

        return leftShrug || rightShrug
    }

    /**
     * Gövde rotasyonu tespiti: Sol ve sağ omuzun y farkı fazlaysa gövde dönmüştür.
     */
    private fun detectTorsoRotation(frame: PoseFrame): Boolean {
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER) ?: return false
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER) ?: return false
        return Math.abs(lShoulder.y - rShoulder.y) > AnalysisConstants.ROW_TORSO_ROTATION_THRESHOLD
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> "Çekiş pozisyonu hazır"
            RepetitionPhase.GOING_UP -> "Çekiyorsunuz..."
            RepetitionPhase.TOP -> "Harika çekiş! İnin"
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
        smoothedElbowAngle = null
    }
}
