package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*
import com.example.exerciseformanalyzer.util.AnalysisConstants
import com.example.exerciseformanalyzer.util.AngleUtils
import com.example.exerciseformanalyzer.util.StringProvider
import com.example.exerciseformanalyzer.R

/**
 * Plank form değerlendirme aracı.
 * Plank statik bir hareket olduğu için tekrar sayısı artmaz. FSM stringProvider.getString(R.string.msg_top) fazında kilitli kalır.
 *
 * Form kontrolleri:
 * 1. Kalça Sarkması (Hip Sag) - Belin yere doğru bükülmesi
 * 2. Kalça Yükselmesi (Hip Rise) - Kalçanın tepe oluşturacak kadar yükselmesi
 */
class PlankEvaluator(private val stringProvider: StringProvider) : ExerciseEvaluator {

    override val exerciseType = ExerciseType.PLANK

    private var repState = RepetitionState(phase = RepetitionPhase.TOP)
    
    // Doğru formda beklenen toplam süre
    private var totalHoldMs: Long = 0L
    private var lastValidFrameMs: Long = 0L

    override fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback {
        if (trackingQuality == TrackingQuality.LOST || trackingQuality == TrackingQuality.POOR) {
            return poorTrackingFeedback()
        }

        val errors = mutableListOf<String>()
        var score = 100

        // 1. Kalça pozisyonu (vücut hattı düzlüğü) kontrolü
        val hipDeviation = calculateHipDeviation(frame)
        if (hipDeviation != null) {
            // Plank şınavdan çok daha hassastır. Eşik 0.08f yerine 0.04f (sıkı denetim)
            when {
                hipDeviation > 0.04f -> {
                    errors.add(stringProvider.getString(R.string.err_dont_let_waist_sag))
                    score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
                }
                hipDeviation < -0.04f -> {
                    errors.add(stringProvider.getString(R.string.err_hips_straight_line))
                    score -= AnalysisConstants.SCORE_PENALTY_ALIGNMENT
                }
            }
        }

        // 2. Omuz ve Kol Pozisyonu: Dirsek tam omuzun altında mı?
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lElbow = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rElbow = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)
        
        val sLmd = if (lShoulder != null && lElbow != null && lShoulder.visibility > (rShoulder?.visibility ?: 0f)) lShoulder else rShoulder
        val eLmd = if (lElbow != null && lShoulder != null && lShoulder.visibility > (rShoulder?.visibility ?: 0f)) lElbow else rElbow
        
        if (sLmd != null && eLmd != null) {
            val horizontalDiff = Math.abs(sLmd.x - eLmd.x)
            val verticalDiff = Math.abs(sLmd.y - eLmd.y)
            // Eğer yatay mesafe, dikey mesafenin %45'inden fazlaysa dirsek önde veya arkadadır
            if (horizontalDiff > verticalDiff * 0.45f) {
                errors.add(stringProvider.getString(R.string.err_elbows_under_shoulders))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        // 3. Karın ve Kalça Aktivasyonu: Dizler bükülü mü? (Bükülüyorsa kalça/karın gevşektir)
        val kneeAngle = AngleUtils.dominantKneeAngle(angles, frame)
        if (kneeAngle != null && kneeAngle < 160f) {
            errors.add(stringProvider.getString(R.string.err_dont_bend_knees))
            score -= AnalysisConstants.SCORE_PENALTY_JOINT_ALIGNMENT
        }

        // 4. Baş ve Boyun Pozisyonu
        val headPitch = calculateHeadPitch(frame)
        val headDeviation = calculateHeadDeviation(frame)

        if (headPitch != null && headPitch < 0.005f) {
            // Gözler kulak hizasına kadar veya kulağın üstüne çıkarsa (baş yukarı kalkmış demektir)
            errors.add(stringProvider.getString(R.string.err_head_too_high))
            score -= AnalysisConstants.SCORE_PENALTY_MINOR
        } else if (headDeviation != null) {
            if (headDeviation > 0.05f) { 
                errors.add(stringProvider.getString(R.string.err_dont_look_at_chest))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            } else if (headDeviation < -0.04f) {
                // Çok ufak bir kafa kaldırmada bile omurga eğrisini bozmayı engelle 
                errors.add(stringProvider.getString(R.string.err_dont_bend_neck))
                score -= AnalysisConstants.SCORE_PENALTY_MINOR
            }
        }

        val primaryError = errors.firstOrNull()
        val isCorrect = errors.isEmpty()

        // Süre sayacı (Sadece doğru formda beklerken artar)
        val currentTime = frame.timestampMs
        if (isCorrect) {
            if (lastValidFrameMs > 0L && (currentTime - lastValidFrameMs) < 1000L) {
                totalHoldMs += (currentTime - lastValidFrameMs)
            }
            lastValidFrameMs = currentTime
        } else {
            // Form bozulduğunda süreyi durdur, ama sıfırlama!
            lastValidFrameMs = 0L
        }

        val totalHoldSec = (totalHoldMs / 1000L).toInt()
        repState = repState.copy(count = totalHoldSec, phase = RepetitionPhase.TOP)

        return FormFeedback(
            isCorrect = isCorrect,
            score = score.coerceAtLeast(0),
            primaryError = primaryError,
            secondaryErrors = if (errors.size > 1) errors.drop(1) else emptyList(),
            feedbackMessage = if (isCorrect) stringProvider.getString(R.string.msg_great_form_hold) else primaryError!!,
            confidence = if (trackingQuality == TrackingQuality.GOOD) 0.9f else 0.7f
        )
    }

    /**
     * Omuz-kalça-ayak bileği hattından kalça sapmasını hesaplar.
     * Kameraya en iyi dönük olan (visibility puanı en yüksek) taraf kullanılır.
     */
    private fun calculateHipDeviation(frame: PoseFrame): Float? {
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val lAnkle = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)
        val rAnkle = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        val lValid = lShoulder != null && lHip != null && lAnkle != null
        val rValid = rShoulder != null && rHip != null && rAnkle != null
        
        val lVis = if (lValid) lShoulder!!.visibility + lHip!!.visibility + lAnkle!!.visibility else 0f
        val rVis = if (rValid) rShoulder!!.visibility + rHip!!.visibility + rAnkle!!.visibility else 0f

        if (lVis < 1.0f && rVis < 1.0f) return null // Görünürlük çok düşükse sapma ölçülmez

        return if (lVis >= rVis && lValid) {
            AngleUtils.calculateHipDeviation(lShoulder!!, lHip!!, lAnkle!!)
        } else if (rValid) {
            AngleUtils.calculateHipDeviation(rShoulder!!, rHip!!, rAnkle!!)
        } else {
            null
        }
    }

    /**
     * Kulak-Omuz-Kalça hattından başın (boynun) sapmasını hesaplar.
     * Negatif = Baş havada (karşıya bakıyor)
     * Pozitif = Baş düşük (içe, ayaklara doğru bakıyor)
     */
    private fun calculateHeadDeviation(frame: PoseFrame): Float? {
        val lEar = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR)
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val lHip = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_HIP)
        val rEar = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        val rHip = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_HIP)

        val lValid = lEar != null && lShoulder != null && lHip != null
        val rValid = rEar != null && rShoulder != null && rHip != null

        val lVis = if (lValid) lEar!!.visibility + lShoulder!!.visibility else 0f
        val rVis = if (rValid) rEar!!.visibility + rShoulder!!.visibility else 0f

        if (lVis < 0.5f && rVis < 0.5f) return null // Görünürlük düşükse pas geç

        val isLeft = lVis >= rVis && lValid
        
        val ear = if (isLeft) lEar!! else if (rValid) rEar!! else return null
        val shoulder = if (isLeft) lShoulder!! else rShoulder!!
        val hip = if (isLeft) lHip!! else rHip!!

        val dx = hip.x - shoulder.x
        if (Math.abs(dx) < 0.001f) return 0f
        
        // Omuz ve kalça doğrusunu kullanarak kulağın beklenen Y noktasını bul
        val t = (ear.x - shoulder.x) / dx
        val expectedEarY = shoulder.y + t * (hip.y - shoulder.y)
        
        return ear.y - expectedEarY
    }

    /**
     * Kafa rotasyonunu (bakış açısını) göz ile kulak arasındaki Y-eksantrikliğiyle ölçer.
     * Normal plankte yüz yere baktığı için gözler kulaktan fiziksel olarak daha aşağıda (Y-değeri büyük) olmalı.
     * Eğer kişi karşıya bakarsa, gözleri kulağının Y-hizasına yükselir veya kulağı geçer.
     */
    private fun calculateHeadPitch(frame: PoseFrame): Float? {
        val lEar = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EAR)
        val lEye = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_EYE)
        val rEar = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EAR)
        val rEye = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_EYE)

        val lValid = lEar != null && lEye != null
        val rValid = rEar != null && rEye != null

        val lVis = if (lValid) lEar!!.visibility + lEye!!.visibility else 0f
        val rVis = if (rValid) rEar!!.visibility + rEye!!.visibility else 0f

        if (lVis < 0.5f && rVis < 0.5f) return null

        val isLeft = lVis >= rVis && lValid
        
        val ear = if (isLeft) lEar!! else if (rValid) rEar!! else return null
        val eye = if (isLeft) lEye!! else rEye!!

        // Göz ne kadar aşağıdaysa yüz o kadar yere (mata) bakıyordur
        return eye.y - ear.y
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking_full), confidence = 0.2f
    )

    override fun getRepetitionState(): RepetitionState = repState

    override fun reset() {
        repState = RepetitionState(phase = RepetitionPhase.TOP)
        totalHoldMs = 0L
        lastValidFrameMs = 0L
    }
}
