package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import kotlin.math.abs
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Dumbbell Row form değerlendirici.
 *
 * Kurallar:
 * 1. Sırt Pozisyonu (Masa Kuralı): Gövde yere olabildiğince paralel olmalı. Dik durulmamalı.
 * 2. Çekiş Tekniği (Hook Grip & Kalçaya Çekiş): Ağırlık göğse değil, kavis çizerek kalçaya doğru (geriye) çekilmeli.
 * 3. Skapula ve ROM: Alt noktada omuz serbest bırakılarak esnetilmeli, üst noktada dirsek sırttan yukarı taşmalı.
 * 4. Boyun/Bakış: Aynaya bakılıp boyun kırılmamalı, baş omurga hizasında (nötr) yere doğru bakmalı.
 */
class DumbbellRowEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.DUMBBELL_ROW

    private var repState = RepetitionState()

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val lWrist = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_WRIST)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        val rWrist = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_WRIST)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)

        val lVis = (lShoulder?.visibility ?: 0f) + (lElbow?.visibility ?: 0f) + (lWrist?.visibility ?: 0f)
        val rVis = (rShoulder?.visibility ?: 0f) + (rElbow?.visibility ?: 0f) + (rWrist?.visibility ?: 0f)
        
        val isLeft = lVis >= rVis
        
        val shoulder = if (isLeft) lShoulder else rShoulder
        val elbow = if (isLeft) lElbow else rElbow
        val wrist = if (isLeft) lWrist else rWrist
        val hip = if (isLeft) lHip else rHip
        
        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return poorTrackingFeedback()
        }

        val elbowAngle = if (isLeft) angles.leftElbowAngle else angles.rightElbowAngle
        if (elbowAngle == null) return poorTrackingFeedback()

        val currentTimeMs = frame.timestampMs
        updateFSM(elbowAngle, currentTimeMs)

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Sırtın Pozisyonu (Gövde Paralelliği / Masa Kuralı)
        val dy = abs(shoulder.y - hip.y)
        val dx = abs(shoulder.x - hip.x).coerceAtLeast(0.001f)
        val torsoAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
        
        // Masa kuralı: İdeal olarak yere paralel (yakın 0 açı)
        // Eğer Y farkı (dy) X farkına (dx) göre büyükse dik duruyordur.
        // torsoAngleDeg > 40-45 ise uyarı ver
        if (torsoAngleDeg > 45.0) { 
            errors.add(stringProvider.getString(R.string.err_torso_parallel))
            score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
        }

        // Yukarı çekiş tepesinde ve çekiş eyleminde teknik analizler
        if (repState.phase == RepetitionPhase.TOP || repState.phase == RepetitionPhase.GOING_UP) {
            
            // 2. Çekiş Tekniği (Kalçaya Doğru Çekiş)
            // Çekişin göğse mi (omuza yakın) yoksa kalçaya mı (kalçaya yakın) yapıldığını x ekseninde ölçüyoruz.
            val distToShoulderX = abs(wrist.x - shoulder.x)
            val distToHipX = abs(wrist.x - hip.x)
            
            // Eğer bilek yatay düzlemde (x) omza çok daha yakınsa ve dikeyde de kısaysa, dik çekiyordur
            if (distToShoulderX < (dx * 0.35f) && wrist.y < shoulder.y + 0.15f) {
                errors.add(stringProvider.getString(R.string.err_pull_to_chest))
                score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
            }

            // 3. Omuz ve Kürek Kemiği Sıkıştırma (Tepe Noktası)
            if (repState.phase == RepetitionPhase.TOP) {
                // Dirsek omuzun y koordinatını geçmeli (y daha küçük olmalı)
                if (elbow.y > shoulder.y + 0.05f) {
                    errors.add(stringProvider.getString(R.string.err_squeeze_back_harder))
                    score -= AnalysisConstants.SCORE_PENALTY_DEPTH
                }
            }
        }

        // Esneme kontrolü (En alt nokta)
        if (repState.phase == RepetitionPhase.BOTTOM) {
            if (elbowAngle < 160f) {
                errors.add(stringProvider.getString(R.string.err_let_shoulder_hang))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        // 4. Boyun ve Bakış Açısı
        val ear = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
        val eye = if (isLeft) frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EYE) else frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EYE)
        
        // Eğer yüz hatları görünürse bakış açısını test et (ayada kendini izleme hatası)
        if (ear != null && eye != null && ear.visibility > 0.4f && eye.visibility > 0.4f) {
            // Yüz yere bakıyorsa göz kulaktan y ekseninde aşağıdadır (y değeri büyük)
            // Eğer göz kulak seviyesinin oldukça üstündeyse (y değeri küçükse) kafa havaya kalkmıştır
            if (eye.y < ear.y - 0.03f) {
                errors.add(stringProvider.getString(R.string.err_neck_alignment))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
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

    private fun updateFSM(elbowAngle: Float, currentTimeMs: Long) {
        val timeSinceLastChange = currentTimeMs - repState.lastPhaseChangeMs
        if (timeSinceLastChange < 300L) return

        val newPhase = when (repState.phase) {
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> {
                if (elbowAngle < 150f) RepetitionPhase.GOING_UP else repState.phase
            }
            RepetitionPhase.GOING_UP -> {
                // Çekiş noktasının tepe olduğunu dar dirsek açısı belirler.
                if (elbowAngle <= 95f) RepetitionPhase.TOP else repState.phase
            }
            RepetitionPhase.TOP -> {
                if (elbowAngle > 110f) RepetitionPhase.GOING_DOWN else repState.phase
            }
            RepetitionPhase.GOING_DOWN -> {
                if (elbowAngle >= 160f) {
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
            RepetitionPhase.IDLE, RepetitionPhase.BOTTOM -> stringProvider.getString(R.string.msg_ready_pull_elbow)
            RepetitionPhase.GOING_UP -> stringProvider.getString(R.string.msg_pulling)
            RepetitionPhase.TOP -> stringProvider.getString(R.string.msg_squeeze_lats)
            RepetitionPhase.GOING_DOWN -> stringProvider.getString(R.string.msg_stretch_slowly)
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
    }
}
