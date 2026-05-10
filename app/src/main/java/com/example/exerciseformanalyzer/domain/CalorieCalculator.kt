package com.example.exerciseformanalyzer.domain

// CalorieCalculator — MET bazlı kalori hesaplama servisi
//
// Formül: Kalori = MET × ağırlık(kg) × süre(saat)
// Kaynak: ACSM (American College of Sports Medicine) standartları
//
// MET (Metabolic Equivalent of Task): Bir aktivitenin dinlenme
// metabolizmasına kıyasla ne kadar enerji harcattığını gösterir.
// Örn: MET 4.0 = dinlenirken 4 kat daha fazla kalori harcar.

import com.example.exerciseformanalyzer.model.ExerciseType

object CalorieCalculator {

    /**
     * MET değeri ve kullanıcı verisiyle kalori hesaplar.
     *
     * @param metValue   Egzersizin MET değeri (ExerciseEntity.metValue veya defaultMet())
     * @param weightKg   Kullanıcının ağırlığı (kg). Bilinmiyorsa 70kg varsayılan.
     * @param durationSeconds  Egzersiz süresi (saniye)
     * @return Yakılan kalori (kcal), Float olarak
     */
    fun calculate(
        metValue: Float,
        weightKg: Float = 70f,    // Kullanıcı profilinde ağırlık yoksa default
        durationSeconds: Long
    ): Float {
        if (durationSeconds <= 0 || metValue <= 0f) return 0f
        val durationHours = durationSeconds / 3600f
        return metValue * weightKg * durationHours
    }

    /**
     * ExerciseType'a göre standart MET değerini döner.
     * Bu değerler ExerciseEntity.metValue ile override edilebilir;
     * veritabanında kayıtlı değer önceliklidir.
     *
     * Kaynak: Compendium of Physical Activities (Ainsworth et al., 2011)
     */
    fun defaultMet(exerciseType: ExerciseType): Float {
        return when (exerciseType) {
            // Alt Vücut
            ExerciseType.SQUAT            -> 5.0f
            ExerciseType.HALF_SQUAT       -> 3.5f
            ExerciseType.JUMP_SQUAT       -> 8.0f
            ExerciseType.REVERSE_LUNGE    -> 4.0f
            ExerciseType.BULGARIAN_SPLIT_SQUAT -> 5.0f
            ExerciseType.CALF_RAISE       -> 2.5f
            ExerciseType.GLUTE_BRIDGE     -> 3.0f

            // Üst Vücut / Kol
            ExerciseType.BICEPS_CURL      -> 3.0f
            ExerciseType.HAMMER_CURL      -> 3.0f
            ExerciseType.SHOULDER_PRESS   -> 4.0f
            ExerciseType.LATERAL_RAISE    -> 3.0f
            ExerciseType.FRONT_RAISE      -> 3.0f
            ExerciseType.BENT_OVER_ROW    -> 4.5f
            ExerciseType.DUMBBELL_ROW     -> 4.5f
            ExerciseType.TRICEPS_EXTENSION-> 3.0f
            ExerciseType.UPRIGHT_ROW      -> 4.0f

            // Vücut Ağırlığı / Core
            ExerciseType.PUSH_UP          -> 3.8f
            ExerciseType.KNEE_PUSH_UP     -> 2.5f
            ExerciseType.PLANK            -> 4.0f
            ExerciseType.MOUNTAIN_CLIMBER -> 8.0f
            ExerciseType.SIT_UP           -> 3.5f
            ExerciseType.CRUNCH           -> 2.8f
            ExerciseType.RUSSIAN_TWIST    -> 4.0f
            ExerciseType.BURPEE           -> 8.0f
            ExerciseType.TRICEPS_KICKBACK -> 3.0f
            ExerciseType.BENT_OVER_RAISE  -> 4.0f
            
            ExerciseType.HEEL_TAP         -> 3.0f
            ExerciseType.BICYCLE_CRUNCH   -> 5.0f
            ExerciseType.REVERSE_CRUNCH   -> 4.5f
            ExerciseType.STRAIGHT_LEG_CRUNCH -> 4.5f

            ExerciseType.UNKNOWN          -> 3.0f // Varsayılan orta yoğunluk
        }
    }

    /**
     * Oturum sonunda tam özet için kullanılan yardımcı fonksiyon.
     * ExerciseType + kullanıcı ağırlığı + süre/tekrar verilince kalori hesaplar.
     *
     * Formül: Kalori = MET × ağırlık(kg) × süre(saat)
     * Tekrar bazlı egzersizlerde: tekrar başına ortalama 6 saniyelik süre varsayılır.
     *
     * @param reps Tekrar sayısı — 0 ise süre bazlı hesap yapılır
     * @param customMetValue Veritabanından gelen özel MET değeri; null ise defaultMet kullanılır
     */
    fun calculateForSession(
        exerciseType: ExerciseType,
        weightKg: Float = 70f,
        durationSeconds: Long,
        reps: Int = 0,
        customMetValue: Float? = null
    ): Float {
        val metValue = customMetValue ?: defaultMet(exerciseType)

        // Tekrar bazlı: her tekrar ≈ 6 saniye hareket + 3 saniye dinlenme = 9 sn
        val effectiveDuration = if (reps > 0 && durationSeconds <= 0) {
            reps * 9L
        } else {
            durationSeconds
        }

        return calculate(metValue, weightKg, effectiveDuration)
    }
}
