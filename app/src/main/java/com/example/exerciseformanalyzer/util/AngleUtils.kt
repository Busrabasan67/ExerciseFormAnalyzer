package com.example.exerciseformanalyzer.util

import com.example.exerciseformanalyzer.model.Landmark
import com.example.exerciseformanalyzer.model.JointAngles
import com.example.exerciseformanalyzer.model.PoseFrame
import com.example.exerciseformanalyzer.model.PoseLandmarkIndex
import com.example.exerciseformanalyzer.model.TrackingQuality
import com.example.exerciseformanalyzer.model.BodyProfile
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pose landmarklarından geometrik hesaplamalar yapan yardımcı fonksiyonlar.
 * Tüm açı hesaplamaları derece cinsinden döner.
 * Thread-safe ve pure function — yan etki yok.
 */
object AngleUtils {

    /**
     * Üç nokta arasındaki açıyı hesaplar.
     * [pointA] ve [pointC] kenar uçları, [pointB] köşe noktasıdır.
     * Vektör dot product ile çalışır; sonuç 0°-180° arasında olur.
     */
    fun calculateAngle(pointA: Landmark, pointB: Landmark, pointC: Landmark): Float {
        val vectorBAx = pointA.x - pointB.x
        val vectorBAy = pointA.y - pointB.y
        val vectorBCx = pointC.x - pointB.x
        val vectorBCy = pointC.y - pointB.y

        val dotProduct = vectorBAx * vectorBCx + vectorBAy * vectorBCy
        val magnitudeBA = sqrt(vectorBAx * vectorBAx + vectorBAy * vectorBAy)
        val magnitudeBC = sqrt(vectorBCx * vectorBCx + vectorBCy * vectorBCy)

        if (magnitudeBA < 1e-6f || magnitudeBC < 1e-6f) return 0f

        val cosAngle = (dotProduct / (magnitudeBA * magnitudeBC)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosAngle.toDouble()).toDouble()).toFloat()
    }

    /**
     * İki nokta arasındaki açıyı yatay eksenden hesaplar (derece).
     * Pozitif Y aşağıya doğru olduğu için Android koordinat sistemine göre çalışır.
     */
    fun calculateHorizontalAngle(pointA: Landmark, pointB: Landmark): Float {
        val dx = pointB.x - pointA.x
        val dy = pointB.y - pointA.y
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toDouble()).toFloat()
    }

    /**
     * Gövde eğimini hesaplar — omuz orta noktası ile kalça orta noktası arasındaki
     * vektörün dikeyle yaptığı açıyı döndürür.
     * 0° = tamamen dik, 90° = tamamen yatay.
     */
    fun calculateTorsoInclination(
        leftShoulder: Landmark,
        rightShoulder: Landmark,
        leftHip: Landmark,
        rightHip: Landmark
    ): Float {
        val shoulderMidX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderMidY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipMidX = (leftHip.x + rightHip.x) / 2f
        val hipMidY = (leftHip.y + rightHip.y) / 2f

        val dx = shoulderMidX - hipMidX
        val dy = shoulderMidY - hipMidY

        // Dikeyle açı: atan2(|dx|, |dy|)
        return Math.toDegrees(atan2(Math.abs(dx.toDouble()), Math.abs(dy.toDouble()))).toFloat()
    }

    /**
     * Push-up'ta vücudun düzlüğünü değerlendirir.
     * Omuz, kalça ve ayak bileği arasındaki hizayı analiz eder.
     * Kalçanın referans çizgisinden (omuz-ayak bileği hattından) ne kadar saptığını döndürür (normalize).
     * Pozitif = kalça belirtilen çizginin üstünde (yükselmiş), Negatif = kalça çizginin altında (sarkmış).
     */
    fun calculateHipDeviation(
        shoulder: Landmark,
        hip: Landmark,
        ankle: Landmark
    ): Float {
        // Omuz-ayak bileği hattı üzerinde kalçanın beklenen y konumu
        val t = (hip.x - shoulder.x) / (ankle.x - shoulder.x).let { if (it == 0f) 1f else it }
        val expectedHipY = shoulder.y + t * (ankle.y - shoulder.y)
        return hip.y - expectedHipY  // Negatif = yukarı sapma, Pozitif = aşağı sapma
    }

    /**
     * Verilen PoseFrame'den tam JointAngles nesnesini hesaplar.
     * Visibility değeri düşük olan landmarklar için null döner.
     */
    fun computeJointAngles(frame: PoseFrame): JointAngles {
        val lm = frame.landmarks
        val idx = PoseLandmarkIndex

        fun landmark(i: Int): Landmark? =
            frame.landmarkOrNull(i)?.takeIf { it.visibility >= AnalysisConstants.MIN_LANDMARK_VISIBILITY }

        val lShoulder = landmark(idx.LEFT_SHOULDER)
        val rShoulder = landmark(idx.RIGHT_SHOULDER)
        val lElbow = landmark(idx.LEFT_ELBOW)
        val rElbow = landmark(idx.RIGHT_ELBOW)
        val lWrist = landmark(idx.LEFT_WRIST)
        val rWrist = landmark(idx.RIGHT_WRIST)
        val lHip = landmark(idx.LEFT_HIP)
        val rHip = landmark(idx.RIGHT_HIP)
        val lKnee = landmark(idx.LEFT_KNEE)
        val rKnee = landmark(idx.RIGHT_KNEE)
        val lAnkle = landmark(idx.LEFT_ANKLE)
        val rAnkle = landmark(idx.RIGHT_ANKLE)

        return JointAngles(
            leftKneeAngle = if (lHip != null && lKnee != null && lAnkle != null)
                calculateAngle(lHip, lKnee, lAnkle) else null,

            rightKneeAngle = if (rHip != null && rKnee != null && rAnkle != null)
                calculateAngle(rHip, rKnee, rAnkle) else null,

            leftHipAngle = if (lShoulder != null && lHip != null && lKnee != null)
                calculateAngle(lShoulder, lHip, lKnee) else null,

            rightHipAngle = if (rShoulder != null && rHip != null && rKnee != null)
                calculateAngle(rShoulder, rHip, rKnee) else null,

            leftElbowAngle = if (lShoulder != null && lElbow != null && lWrist != null)
                calculateAngle(lShoulder, lElbow, lWrist) else null,

            rightElbowAngle = if (rShoulder != null && rElbow != null && rWrist != null)
                calculateAngle(rShoulder, rElbow, rWrist) else null,

            leftShoulderAngle = if (lElbow != null && lShoulder != null && lHip != null)
                calculateAngle(lElbow, lShoulder, lHip) else null,

            rightShoulderAngle = if (rElbow != null && rShoulder != null && rHip != null)
                calculateAngle(rElbow, rShoulder, rHip) else null,

            torsoInclination = if (lShoulder != null && rShoulder != null && lHip != null && rHip != null)
                calculateTorsoInclination(lShoulder, rShoulder, lHip, rHip) else null,

            shoulderLevelDiff = if (lShoulder != null && rShoulder != null)
                Math.abs(lShoulder.y - rShoulder.y) else null,

            hipLevelDiff = if (lHip != null && rHip != null)
                Math.abs(lHip.y - rHip.y) else null
        )
    }

    /**
     * Diz açısından "dominant taraf"ı seçer.
     * Daha düşük görünürlüğe sahip taraf elenir; ikisi de görünürse ortalama alınır.
     * Null: her iki taraf da görünür değil.
     */
    fun dominantKneeAngle(angles: JointAngles, frame: PoseFrame): Float? {
        val lVis = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_KNEE)?.visibility ?: 0f
        val rVis = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_KNEE)?.visibility ?: 0f
        return when {
            angles.leftKneeAngle != null && angles.rightKneeAngle != null ->
                (angles.leftKneeAngle + angles.rightKneeAngle) / 2f
            angles.leftKneeAngle != null -> angles.leftKneeAngle
            angles.rightKneeAngle != null -> angles.rightKneeAngle
            else -> null
        }
    }

    /**
     * Dominant dirsek açısını seçer — daha yüksek görünürlüğe sahip tarafı tercih eder.
     */
    fun dominantElbowAngle(angles: JointAngles, frame: PoseFrame): Float? {
        val lVis = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_ELBOW)?.visibility ?: 0f
        val rVis = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_ELBOW)?.visibility ?: 0f
        return when {
            lVis >= rVis && angles.leftElbowAngle != null -> angles.leftElbowAngle
            angles.rightElbowAngle != null -> angles.rightElbowAngle
            else -> angles.leftElbowAngle
        }
    }

    /**
     * Bir PoseFrame'deki tüm kritik landmarkların ortalama visibility'sini hesaplar.
     */
    fun averageVisibility(frame: PoseFrame, criticalIndices: Set<Int>): Float {
        if (criticalIndices.isEmpty()) return 0f
        val total = criticalIndices.sumOf { idx ->
            (frame.landmarkOrNull(idx)?.visibility ?: 0f).toDouble()
        }
        return (total / criticalIndices.size).toFloat()
    }

    /**
     * Landmarkların ne kadarının MIN_LANDMARK_VISIBILITY eşiğinin üstünde olduğunu hesaplar.
     */
    fun visibleRatio(frame: PoseFrame, indices: Set<Int>): Float {
        if (indices.isEmpty()) return 0f
        val visible = indices.count { idx ->
            (frame.landmarkOrNull(idx)?.visibility ?: 0f) >= AnalysisConstants.MIN_LANDMARK_VISIBILITY
        }
        return visible.toFloat() / indices.size
    }

    /**
     * Genel takip kalitesini belirler.
     */
    fun evaluateTrackingQuality(frame: PoseFrame, criticalIndices: Set<Int>): TrackingQuality {
        val ratio = visibleRatio(frame, criticalIndices)
        val avgVis = averageVisibility(frame, criticalIndices)
        return when {
            ratio >= AnalysisConstants.MIN_CRITICAL_LANDMARK_RATIO
                    && avgVis >= AnalysisConstants.MIN_GOOD_TRACKING_VISIBILITY -> TrackingQuality.GOOD
            ratio >= AnalysisConstants.MIN_CRITICAL_LANDMARK_RATIO
                    && avgVis >= AnalysisConstants.MIN_FAIR_TRACKING_VISIBILITY -> TrackingQuality.FAIR
            ratio > 0f && avgVis >= AnalysisConstants.MIN_FAIR_TRACKING_VISIBILITY -> TrackingQuality.POOR
            else -> TrackingQuality.LOST
        }
    }

    /**
     * Exponential Moving Average (EMA) ile açı yumuşatma.
     * [current] anlık değer, [previous] önceki yumuşatılmış değer.
     * [alpha] = AnalysisConstants.ANGLE_SMOOTHING_ALPHA kullanılmalı.
     */
    fun smoothAngle(current: Float, previous: Float, alpha: Float): Float =
        alpha * current + (1f - alpha) * previous

    /**
     * Vücut duruş profilini analiz eder (Önden mi yoksa yandan mı bakıyor).
     * Omuz landmarklarının Z (derinlik) ve görünürlüğüne dayanarak çalışır.
     */
    fun detectBodyProfile(frame: PoseFrame): BodyProfile {
        val lShoulder = frame.landmarkOrNull(PoseLandmarkIndex.LEFT_SHOULDER)
        val rShoulder = frame.landmarkOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)
        
        if (lShoulder == null || rShoulder == null) return BodyProfile.FRONTAL

        // Kamera açısından dolayı biri çok daha öndeyse (z küçüktür) ve kameraya daha net görünüyorsa:
        // Ancak en garantili yöntem görünürlükteki ciddi farktır (occlusion).
        val lVis = lShoulder.visibility
        val rVis = rShoulder.visibility

        // Eğer görünürlükler arasında büyük uçurum varsa zıt taraf bloklanmış demektir (Yan profil)
        if (lVis > 0.8f && rVis < 0.4f) return BodyProfile.LEFT_SIDE_VISIBLE
        if (rVis > 0.8f && lVis < 0.4f) return BodyProfile.RIGHT_SIDE_VISIBLE

        // Aksi takdirde frontal veya açılı duruş kabul edilir
        return BodyProfile.FRONTAL
    }
}
