package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

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
 * Kamera açısı algılama:
 *   - FRONTAL   : Kullanıcı kameraya dönük. Diz açısı her iki tarafın ortalamasıyla,
 *                 valgus X koordinatlarıyla, gövde eğimi omuz genişliğiyle ölçülür.
 *   - SIDE      : Kullanıcı yan durumda. Diz açısı görünür taraftan, gövde eğimi
 *                 omuz-kalça vektörüyle ölçülür. Valgus kontrolü devre dışı.
 *
 * Form kontrolleri:
 *   1. Yeterince derine inme (diz açısı)
 *   2. Gövde eğimi (aşırı öne yatma) — görünüme göre uyarlanır
 *   3. Diz valgus (dizlerin içeri kapanması) — yalnızca önden görünümde
 */
class SquatEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.SQUAT

    private var repState = RepetitionState()
    private var smoothedKneeAngle: Float? = null
    private var didReachDepth = false


    // Önden/yandan algılama için son profil
    private var lastBodyProfile: BodyProfile = BodyProfile.FRONTAL

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        // Takip kalitesi yetersizse güvenli belirsiz yanıt döndür
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        // Kamera açısını algıla (önden mi yandan mı?)
        lastBodyProfile = AngleUtils.detectBodyProfile(frame)
        val isFrontal = lastBodyProfile == BodyProfile.FRONTAL

        // Diz açısını al: önden görünümde her iki diz de görünürse ortalamasını kullan
        val rawKneeAngle = resolveKneeAngle(angles, frame, isFrontal)
            ?: return poorTrackingFeedback()

        // EMA yumuşatma — titreme azaltma
        smoothedKneeAngle = smoothedKneeAngle?.let {
            AngleUtils.smoothAngle(rawKneeAngle, it, AnalysisConstants.ANGLE_SMOOTHING_ALPHA)
        } ?: rawKneeAngle

        val kneeAngle = smoothedKneeAngle!!
        val currentTimeMs = frame.timestampMs

        // Form hataları değerlendirme (FSM update'ten önce, current phase'i bilmek için)
        val errors = mutableListOf<String>()
        var score = 100

        // 1. Derinlik kontrolü (sadece BOTTOM veya GOING_UP fazında)
        if (repState.phase == RepetitionPhase.BOTTOM || repState.phase == RepetitionPhase.GOING_UP) {
            if (!didReachDepth) {
                errors.add(stringProvider.getString(R.string.err_go_lower))
                score -= AnalysisConstants.SCORE_PENALTY_DEPTH
            }
        }

        // 2. Gövde eğimi kontrolü — görünüme göre farklı eşik
        checkTorsoLean(frame, angles, isFrontal)?.let { error ->
            errors.add(error)
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // 3. Diz valgus kontrolü — YALNIZCA ÖNDEN görünümde anlamlı
        if (isFrontal) {
            val kneeValgus = detectKneeValgus(frame)
            if (kneeValgus) {
                errors.add(stringProvider.getString(R.string.err_knees_out))
                score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
            }
        }

        // Hareket esnasında sürekli hata yapılıp yapılmadığını takip et
        // (Eski badFormFrames takibi kaldırıldı, skor düşürümü FormFeedback'te kalmaya devam ediyor)

        // FSM güncelleme — Eğer hatalı yapıldıysa tekrar geçerli sayılmaz
        updateFSM(kneeAngle, currentTimeMs)

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

    /**
     * Kamera açısına göre diz açısını belirler.
     * - Frontal: 2D yansıma hatalı olabileceği için Y eksenindeki kalça-diz-bilek dikey
     *            oranını kullanarak gerçek derinliği yansıtan bir psödo-açı hesaplar.
     * - Yan: Görünür olan tarafın 2D diz açısını (AngleUtils) doğrudan kullanır.
     */
    private fun resolveKneeAngle(angles: JointAngles, frame: PoseFrame, isFrontal: Boolean): Float? {
        return if (isFrontal) {
            calculateFrontalDepthAngle(frame) ?: AngleUtils.dominantKneeAngle(angles, frame)
        } else {
            // Yandan: sadece görünür olan tarafı kullan
            AngleUtils.dominantKneeAngle(angles, frame)
        }
    }

    /**
     * Önden squat derinliğini ölçmek için Y ekseni mesafe oranını hesaplar.
     * 2D kamerada derinlik (Z) kaybolduğundan, gerçek açı yerine kalçanın
     * yere/dizlere ne kadar yaklaştığını dikey piksellerden ölçer.
     * 1.0 = tam ayakta, 0.0 = tam çömelmiş (kalça ve diz aynı yatay hizada)
     */
    private fun calculateFrontalDepthAngle(frame: PoseFrame): Float? {
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP) ?: return null
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP) ?: return null
        val lKnee = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE) ?: return null
        val rKnee = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE) ?: return null
        val lAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE) ?: return null
        val rAnkle = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ANKLE) ?: return null

        val hipY = (lHip.y + rHip.y) / 2f
        val kneeY = (lKnee.y + rKnee.y) / 2f
        val ankleY = (lAnkle.y + rAnkle.y) / 2f

        // Kalça ve diz arasındaki dikey mesafe
        val thighDistY = kneeY - hipY
        // Diz ve ayak bileği arasındaki dikey mesafe
        val calfDistY = ankleY - kneeY

        if (calfDistY <= 0.01f) return 180f // 0'a bölme koruması

        // thightDistY / calfDistY: ayaktayken ~1.0, çömelince ~0'a yaklaşır
        val ratio = (thighDistY / calfDistY).coerceIn(-0.5f, 1.5f)

        // FSM'e uyumlu pseudo-angle: ratio 1.0 -> 180°, ratio 0.0 -> 90°
        return 90f + (ratio * 90f)
    }

    /**
     * Gövde eğimini kamera açısına göre kontrol eder:
     * - Yandan: torsoInclination direkt kullanılır (yan profilde güvenilir).
     * - Önden: Omuz genişliğinin kalça genişliğine oranıyla eğim hissedilir;
     *           ancak 2D önden görünümde torsoInclination çok güvenilir değildir,
     *           bu yüzden eşiği biraz gevşetiriz.
     * Hata mesajı döner, yoksa null.
     */
    private fun checkTorsoLean(frame: PoseFrame, angles: JointAngles, isFrontal: Boolean): String? {
        val torso = angles.torsoInclination ?: return null
        val threshold = if (isFrontal) {
            // Önden görünümde omuz genişliği farkına bakarak torso dönümünü sınırla
            // Eşiği biraz artır çünkü 2D projeksiyon hatası olabilir
            AnalysisConstants.SQUAT_MAX_TORSO_LEAN + 10f
        } else {
            AnalysisConstants.SQUAT_MAX_TORSO_LEAN
        }
        return if (torso > threshold) stringProvider.getString(R.string.err_chest_up) else null
    }

    private fun updateFSM(kneeAngle: Float, currentTimeMs: Long) {
        val minDuration = AnalysisConstants.MIN_PHASE_DURATION_MS
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs

        if (timeSinceLastChange < minDuration) return  // Çok hızlı geçişi engelle

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.TOP -> {
                if (kneeAngle < AnalysisConstants.SQUAT_KNEE_ANGLE_DOWN_MAX + 20f) {
                    RepetitionPhase.GOING_DOWN
                } else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                when {
                    kneeAngle <= AnalysisConstants.SQUAT_KNEE_ANGLE_DOWN_MAX -> {
                        didReachDepth = true
                        RepetitionPhase.BOTTOM
                    }
                    kneeAngle >= AnalysisConstants.SQUAT_KNEE_ANGLE_UP_MIN -> RepetitionPhase.TOP // Eksik tekrar
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
                    // Tekrar tamamlandı — Her zaman sayılır
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
            RepetitionPhase.IDLE -> stringProvider.getString(R.string.msg_start_squat)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_ready_position)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_going_down_you)
            RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_very_good_up)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_great_form)
            RepetitionPhase.RAISED -> stringProvider.getString(R.string.msg_you_are_up)
        }
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false,
        score = 0,
        primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking_camera),
        confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState()
        smoothedKneeAngle = null
        didReachDepth = false
        lastBodyProfile = BodyProfile.FRONTAL
    }
}
