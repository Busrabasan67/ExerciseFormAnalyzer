package com.example.exerciseformanalyzer.analysis.evaluator

import com.example.exerciseformanalyzer.model.*

/**
 * Tüm egzersiz değerlendiricilerinin uygulaması gereken ortak arayüz.
 * Her evaluator:
 *   1. Bir PoseFrame + JointAngles alır
 *   2. FormFeedback (form değerlendirmesi) döndürür
 *   3. Kendi RepetitionState FSM'ini yönetir
 *
 * Bu arayüz, yeni egzersiz eklemesini kolaylaştırır (Open/Closed prensibi).
 */
interface ExerciseEvaluator {

    /** Bu evaluator'ın hangi egzersizi değerlendirdiğini döndürür. */
    val exerciseType: ExerciseType

    /**
     * Verilen kare için form değerlendirmesi yapar.
     * [frame]: Ham pose karesi
     * [angles]: Hesaplanmış eklem açıları
     * [trackingQuality]: Takip kalitesi — POOR/LOST ise güvenli "belirsiz" yanıt döndür
     */
    fun evaluate(
        frame: PoseFrame,
        angles: JointAngles,
        trackingQuality: TrackingQuality
    ): FormFeedback

    /**
     * Tekrar sayacının anlık durumunu döndürür.
     * [evaluate] çağrısının yan etkisi olarak güncellenir.
     */
    fun getRepetitionState(): RepetitionState

    /**
     * Evaluator durumunu sıfırlar (egzersiz değiştiğinde veya yeniden başlandığında).
     */
    fun reset()
}
