package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Lateral Raise (Yana Açış) form değerlendirici.
 * 
 * Kurallar:
 * 1. Dirsek Eğimi: Kollar tam kilitli olmamalı, hafif bükülü olmalı (130-175 derece).
 * 2. Yönlendiren Dirsektir: Eller hiçbir zaman dirsekten yüksekte olmamalıdır.
 * 3. Skapular Düzlem: Yanlara tam 180 derece açmak yerine vücudun hafif önünde (20-30 derece önde) tutulmalı.
 * 4. Serçe Parmak Kuralı: Bilek tutuşu su dökme açısında hafif içe dönük (serçe parmak yukarıda/hizada) olmalı.
 * 5. Momentum/Salınım: Belden veya dizden yaylanarak ivme kazanılmamalı, gövde sabit durmalı.
 */
class LateralRaiseEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {
    override val exerciseType = ExerciseType.LATERAL_RAISE
    private var repState = RepetitionState()

    private var initialTorsoLeanX: Float? = null

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val profile = AngleUtils.detectBodyProfile(frame)
        val errors = mutableListOf<String>()
        var score = 100

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val lWrist = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST)
        val lPinky = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_PINKY)
        val lIndex = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_INDEX)
        
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val rWrist = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)
        val rPinky = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_PINKY)
        val rIndex = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_INDEX)

        val lVis = (lShoulder?.visibility ?: 0f) + (lElbow?.visibility ?: 0f) + (lWrist?.visibility ?: 0f)
        val rVis = (rShoulder?.visibility ?: 0f) + (rElbow?.visibility ?: 0f) + (rWrist?.visibility ?: 0f)

        val isLeft = lVis >= rVis
        
        val shoulder = if (isLeft) lShoulder else rShoulder
        val elbow = if (isLeft) lElbow else rElbow
        val wrist = if (isLeft) lWrist else rWrist
        val pinky = if (isLeft) lPinky else rPinky
        val index = if (isLeft) lIndex else rIndex

        if (shoulder == null || elbow == null || wrist == null) return poorTrackingFeedback()

        val elbowAngle = if (isLeft) angles.leftElbowAngle else angles.rightElbowAngle
        val shoulderAngle = if (isLeft) angles.leftShoulderAngle else angles.rightShoulderAngle

        if (shoulderAngle == null || elbowAngle == null) return poorTrackingFeedback()

        val currentTimeMs = frame.timestampMs
        updateFSM(shoulderAngle, currentTimeMs)

        // Momentum / Salınım Kontrolü (Gövdenin ileri geri oynaması)
        val hip = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)
        if (hip != null) {
            val currentLeanX = shoulder.x - hip.x
            if (repState.phase == RepetitionPhase.BOTTOM || initialTorsoLeanX == null) {
                initialTorsoLeanX = currentLeanX
            } else if (repState.phase == RepetitionPhase.GOING_UP || repState.phase == RepetitionPhase.TOP) {
                val leanDiff = abs(currentLeanX - initialTorsoLeanX!!)
                if (leanDiff > 0.08f) { // Gövde ileri geri sallanıyor
                    errors.add(stringProvider.getString(R.string.msg_slow_controlled))
                    score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
                }
            }
        }

        // 1. Dirsek Eğimi (Hafif Bükülü Dirsekler)
        if (elbowAngle > 175f) {
            errors.add(stringProvider.getString(R.string.err_dont_lock_arms))
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        } else if (elbowAngle < 120f) {
            // stringProvider.getString(R.string.msg_push_away) kuralına uymuyor, sadece bükerek yukarı kaldırıyor
            errors.add(stringProvider.getString(R.string.err_pull_only_up))
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        }

        // Yukarı harekette ve tepe noktasında kritik kontroller 
        if (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_UP) {
            
            // 2. Dirsekler Önden Gitsin (Bilek dirsekten yüksekte olmamalı)
            // Y koordinatında küçük olan daha yukarıdadır.
                if (shoulderAngle > 105f) {
                    errors.add(stringProvider.getString(R.string.err_dont_pass_shoulder))
                    score -= AnalysisConstants.SCORE_PENALTY_MINOR
                }
                
                if (wrist.y < elbow.y - 0.02f) { 
                    errors.add(stringProvider.getString(R.string.err_lift_from_shoulder))
                    score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
                }

            // 3. Serçe Parmak Kuralı
            if (pinky != null && index != null && pinky.visibility > 0.4f && index.visibility > 0.4f) {
                // Eğer serçe parmak (pinky.y) işaret parmağından (index.y) çok daha aşağıdaysa (büyükse), avuç içi karşıya veya yukarı bakıyordur.
                if (pinky.y > index.y + 0.03f) {
                    errors.add(stringProvider.getString(R.string.err_pinky_up))
                    score -= AnalysisConstants.SCORE_PENALTY_MINOR
                }
                // Aşırı sürahiden su dökme kontrolü
                if (pinky.y < index.y - 0.08f) {
                    errors.add(stringProvider.getString(R.string.err_dont_pour_pitcher))
                    score -= AnalysisConstants.SCORE_PENALTY_MINOR
                }
            }
        }

        // 4. Skapular Düzlem Kontrolü (Sadece yandan bakıyorsak ölçülebilir)
        if (profile != BodyProfile.FRONTAL) {
            val ear = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
            val nose = frame.landmarkOrNull(PoseLandmarkIndex.NOSE)
            
            if (ear != null && nose != null) {
                val facingLeft = nose.x < ear.x
                // Kollar tam gövdenin yanında (180 derece) açılıyorsa x ekseninde omuzla aynıdır.
                // Skapular düzlemde dirsek omuzun bir miktar önünde (yüzün baktığı yönde) bulunmalıdır.
                val inScapularPlane = if (facingLeft) (elbow.x < shoulder.x + 0.02f) else (elbow.x > shoulder.x - 0.02f)
                
                if (!inScapularPlane && (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_UP)) {
                    errors.add(stringProvider.getString(R.string.err_scapular_plane))
                    score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
                }
            }
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

    // Gövde (kalça-omuz ekseni) yanal açış yaparkenki omuz açısına göre FSM
    // Yaklaşık 0-30 derece başlangıç noktası, 75-90 arası tepe noktasıdır.
    private fun updateFSM(shoulderAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 200L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (shoulderAngle > 40f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                // Kollar yaklaşık 75-80 dereceye ulaştıysa tepe noktasına devret (omuz hizası = en az 75 derece)
                if (shoulderAngle >= 75f) {
                    initialTorsoLeanX = null // Salınımı sıfırla ki inişte ölçmesin
                    RepetitionPhase.TOP 
                } else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (shoulderAngle < 65f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (shoulderAngle <= 35f) {
                    val newCount = repState.count + 1
                    repState = RepetitionState(count = newCount, phase = RepetitionPhase.BOTTOM, lastPhaseChangeMs = currentTimeMs)
                    return
                } else repState.phase
            }
            else -> repState.phase
        }

        if (newPhase != repState.phase) {
            repState = repState.copy(phase = newPhase, lastPhaseChangeMs = currentTimeMs)
        }
    }

    private fun buildMessage(isCorrect: Boolean, primaryError: String?, phase: RepetitionPhase): String {
        if (!isCorrect && primaryError != null) return primaryError
        return when (phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_ready_push_away)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_lift)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_feel_peak)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_going_down_controlled)
            else -> ""
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking_full), confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        initialTorsoLeanX = null
    }
}
