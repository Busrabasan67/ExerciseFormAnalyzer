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
            ExerciseType.LUNGE            -> 4.0f
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

            ExerciseType.UNKNOWN          -> 3.0f // Varsayılan orta yoğunluk
        }
    }

    /**
     * Oturum sonunda tam özet için kullanılan yardımcı fonksiyon.
     * ExerciseType + kullanıcı ağırlığı + süre verilince kalori hesaplar.
     */
    fun calculateForSession(
        exerciseType: ExerciseType,
        weightKg: Float = 70f,
        durationSeconds: Long,
        customMetValue: Float? = null   // ExerciseEntity'den gelirse override
    ): Float {
        val met = customMetValue ?: defaultMet(exerciseType)
        return calculate(met, weightKg, durationSeconds)
    }
}
