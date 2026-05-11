package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Push-up (Şınav) form değerlendirme ve tekrar sayacı.
 *
 * FSM Durumları:
 *   TOP (kollar uzun) → GOING_DOWN → BOTTOM (göğüs yere yakın) → GOING_UP → TOP (tekrar sayılır)
 *
 * Form kontrolleri:
 *   1. Yeterince aşağı inme (dirsek açısı)
 *   2. Vücut düzlüğü (kalça sarkma / yükselme)
 *   3. Tam ROM - kolların tam uzaması
 */
class PushUpEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.PUSH_UP

    private var repState = RepetitionState()
    private var smoothedElbowAngle: Float? = null
    private var isCurrentRepValid = true

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

        // 1. Derinlik kontrolü (Range of Motion)
        if (repState.phase == RepetitionPhase.BOTTOM || repState.phase == RepetitionPhase.GOING_UP) {
            if (elbowAngle > AnalysisConstants.PUSH_UP_ELBOW_ANGLE_DOWN_MAX + 15f) {
                errors.add(stringProvider.getString(R.string.err_go_deeper))
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Vücut Hattı (Kalça sarkma / yükselme)
        val hipDeviation = calculateHipDeviation(frame)
        if (hipDeviation != null) {
            when {
                hipDeviation > AnalysisConstants.PUSH_UP_HIP_SAG_THRESHOLD -> {
                    errors.add(stringProvider.getString(R.string.err_flat_back_tight_core))
                    score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
                }
                hipDeviation < -AnalysisConstants.PUSH_UP_HIP_RISE_THRESHOLD -> {
                    errors.add(stringProvider.getString(R.string.err_hips_too_high))
                    score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
                }
            }
        }

        // 3. Dirsek Açısı (Omuz Abduksiyonu) — Arrowhead Shape
        // En iyi önden veya açılı görünümlerde fark edilir.
        val shoulderAngle = AngleUtils.dominantShoulderAngle(angles, frame)
        if (shoulderAngle != null && shoulderAngle > 75f) {
            errors.add(stringProvider.getString(R.string.err_elbows_close))
            score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
        }

        val primaryError = errors.firstOrNull()
        val isCorrect = errors.isEmpty()

        // Eğer tekrar sırasında hata yapıldıysa, bu tekrarı geçersiz say
        if (!isCorrect && repState.phase != RepetitionPhase.IDLE && repState.phase != RepetitionPhase.TOP) {
            isCurrentRepValid = false
        }

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
        if (currentTimeMs - repState.lastPhaseChangeMs < minDuration) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.TOP -> {
                if (elbowAngle < AnalysisConstants.PUSH_UP_ELBOW_ANGLE_UP_MIN - 20f) {
                    isCurrentRepValid = true // Yeni tekrara başlarken sıfırla
                    RepetitionPhase.GOING_DOWN
                } else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle <= AnalysisConstants.PUSH_UP_ELBOW_ANGLE_DOWN_MAX)
                    RepetitionPhase.BOTTOM
                else repState.phase
            }
            RepetitionPhase.BOTTOM -> {
                if (elbowAngle > AnalysisConstants.PUSH_UP_ELBOW_ANGLE_DOWN_MAX + 10f)
                    RepetitionPhase.GOING_UP
                else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                if (elbowAngle >= AnalysisConstants.PUSH_UP_ELBOW_ANGLE_UP_MIN) {
                    val newCount = if (isCurrentRepValid) repState.count + 1 else repState.count
                    repState = RepetitionState(
                        count = newCount,
                        phase = RepetitionPhase.TOP,
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
     * Omuz-kalça-ayak bileği hattından kalça sapmasını hesaplar.
     * Dominant (daha görünür) taraf seçilir.
     * Pozitif değer: kalça aşağı (sarkma), Negatif: kalça yukarı (yükselme).
     */
    private fun calculateHipDeviation(frame: PoseFrame): Float? {
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val lAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)
        val rAnkle = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        return if (lShoulder != null && lHip != null && lAnkle != null)
            AngleUtils.calculateHipDeviation(lShoulder, lHip, lAnkle)
        else if (rShoulder != null && rHip != null && rAnkle != null)
            AngleUtils.calculateHipDeviation(rShoulder, rHip, rAnkle)
        else null
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE -> stringProvider.getString(R.string.msg_pushup_position)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_ready)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_going_down_you)
            RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_great_go_up)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_super_form)
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
        smoothedElbowAngle = null
        isCurrentRepValid = true
    }
}
