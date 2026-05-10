package com.example.exerciseformanalyzer.util

/**
 * Tüm analiz eşik değerleri bu dosyada merkezi olarak tutulur.
 * Magic number kullanımını engeller; değerlerin anlamı yorum satırlarıyla açıklanır.
 * İleride bir Config/Settings ekranıyla kullanıcıya sunulabilir.
 */
object AnalysisConstants {

    // ─── Genel Takip Eşikleri ────────────────────────────────────────────────

    /** Bir landmarkın "görünür" sayılması için minimum visibility skoru */
    const val MIN_LANDMARK_VISIBILITY = 0.5f

    /** Bir landmarkın "güvenilir" sayılması için yüksek eşik */
    const val HIGH_LANDMARK_VISIBILITY = 0.75f

    /** Pose'un genel olarak "iyi takip" sayılması için minimum ortalama visibility */
    const val MIN_GOOD_TRACKING_VISIBILITY = 0.7f

    /** Pose'un "adil takip" sayılması için minimum ortalama visibility */
    const val MIN_FAIR_TRACKING_VISIBILITY = 0.5f

    /** Kritik landmarkların kaçta kaçı (oran) görünür olmalı — aksi halde POOR */
    const val MIN_CRITICAL_LANDMARK_RATIO = 0.75f

    // ─── Smoothing (Zaman Yumuşatma) ─────────────────────────────────────────

    /** Açıların yumuşatılmasında kullanılan EMA (Exponential Moving Average) faktörü.
     *  0.0 = saf geçmiş, 1.0 = saf anlık — 0.3 dengeli bir yumuşatma sağlar */
    const val ANGLE_SMOOTHING_ALPHA = 0.3f

    /** Form kararı için değerlendirme geçmişi pencere boyutu (frame sayısı) */
    const val FEEDBACK_HISTORY_SIZE = 10

    /** Feedback stabilizasyonu için "çoğunluk eşiği" — 10 karenin kaçında hata olmalı */
    const val FEEDBACK_MAJORITY_THRESHOLD = 6

    // ─── Egzersiz Tanıma Eşikleri ─────────────────────────────────────────────

    /** Egzersiz sınıflandırıcının geçmiş frame pencere boyutu */
    const val CLASSIFIER_HISTORY_SIZE = 30

    /** Bir egzersizin "kesin" sayılması için minimum çoğunluk oranı */
    const val CLASSIFIER_CONFIDENCE_THRESHOLD = 0.6f

    // ─── Squat Eşikleri ──────────────────────────────────────────────────────

    /** Squatın "aşağı" fazında kabul edilen maksimum diz açısı (derece)
     *  İnsan anatomisinde tam squat ~70°, paralel squat ~90° */
    const val SQUAT_KNEE_ANGLE_DOWN_MAX = 110f

    /** Squatın "yukarı" fazında beklenen minimum diz açısı (derece)
     *  Tam duruşta diz açısı ~160-170° */
    const val SQUAT_KNEE_ANGLE_UP_MIN = 145f

    /** Geçerli bir squat teki için asgari diz fleksiyonu (derece) — yetersiz derine inmeyi tespit eder */
    const val SQUAT_MIN_DEPTH_ANGLE = 110f

    /** Squatta gövde eğiminin maksimum kabul edilebilir değeri (derece, yataydan)
     *  Bu değerin altı gövdenin aşırı öne eğildiğini gösterir */
    const val SQUAT_MAX_TORSO_LEAN = 45f

    /** Squatta diz valgusunu (içeri kapanma) tespit etmek için
     *  Diz x koordinatı ile ayak x koordinatı arasındaki maksimum sapma oranı */
    const val SQUAT_KNEE_VALGUS_THRESHOLD = 0.05f

    // ─── Push-up Eşikleri ────────────────────────────────────────────────────

    /** Şınavın "aşağı" fazında kabul edilen maksimum dirsek açısı (derece) */
    const val PUSH_UP_ELBOW_ANGLE_DOWN_MAX = 110f

    /** Şınavın "yukarı" fazında beklenen minimum dirsek açısı (derece) */
    const val PUSH_UP_ELBOW_ANGLE_UP_MIN = 145f

    /** Vücudun düz hattının bozulduğunu gösteren maksimum kalça sapması
     *  (omuz-kalça-ayak bileği hizasından kalçanın sapma oranı) */
    const val PUSH_UP_HIP_SAG_THRESHOLD = 0.08f

    /** Vücudun düz hattını bozan kalça yükselmesi eşiği */
    const val PUSH_UP_HIP_RISE_THRESHOLD = 0.08f

    // ─── Sit-up Eşikleri ─────────────────────────────────────────────────────

    /** Sit-up'ın "yukarı" fazında gövde-bacak açısının maksimum değeri (derece)
     *  Tam oturma pozisyonunda bu açı ~45-60° olur */
    const val SIT_UP_TOP_ANGLE_MAX = 50f

    /** Sit-up'ın "aşağı" fazında gövde-bacak açısının minimum değeri (derece) */
    const val SIT_UP_BOTTOM_ANGLE_MIN = 75f

    // ─── Dumbbell Row Eşikleri ───────────────────────────────────────────────

    /** Row'da dirseğin "çekilmiş" sayılması için maksimum dirsek açısı (derece) */
    const val ROW_ELBOW_PULLED_MAX = 90f

    /** Row'da omuzun kulağa yükselmesini tespit eden eşik
     *  (omuz y koordinatı ile kulak y koordinatı arasındaki normalize fark) */
    const val ROW_SHOULDER_SHRUG_THRESHOLD = 0.05f

    /** Row'da gövde rotasyonunun aşıldığını gösteren omuz hizası farkı eşiği */
    const val ROW_TORSO_ROTATION_THRESHOLD = 0.07f

    // ─── Biceps Curl Eşikleri ────────────────────────────────────────────────

    /** Biceps Curl'de curl pozisyonunun zirvesi için maksimum dirsek açısı */
    const val BICEPS_CURL_ELBOW_ANGLE_TOP_MAX = 60f

    /** Biceps Curl'de düz kol pozisyonu için beklenen minimum dirsek açısı */
    const val BICEPS_CURL_ELBOW_ANGLE_BOTTOM_MIN = 145f

    /** Momentum kullanımını tespit etmek için omuz açısı toleransı */
    const val BICEPS_CURL_MAX_SHOULDER_SWING = 30f

    // ─── Tekrar Sayacı Eşikleri ───────────────────────────────────────────────

    /** İki faz değişikliği arasında geçmesi gereken minimum süre (ms) — çok hızlı titremeleri önler */
    const val MIN_PHASE_DURATION_MS = 300L

    // ─── Form Puanı Ağırlıkları ──────────────────────────────────────────────

    /** Derinlik/yeterli ROM (Range of Motion) hatası için puan cezası */
    const val SCORE_PENALTY_DEPTH = 25

    /** Gövde hizası/eğimi hatası için puan cezası */
    const val SCORE_PENALTY_ALIGNMENT = 20

    /** Eklem hizası hatası (örn. diz valgus) için puan cezası */
    const val SCORE_PENALTY_JOINT_ALIGNMENT = 15

    /** Küçük form bozuklukları için puan cezası */
    const val SCORE_PENALTY_MINOR = 10

    // ─── Mountain Climber Eşikleri ───────────────────────────────────────────
    const val MOUNTAIN_CLIMBER_HIP_MAX_SAG = 0.12f
    const val MOUNTAIN_CLIMBER_HIP_MAX_RISE = 0.12f
    
    // ─── Russian Twist Eşikleri ──────────────────────────────────────────────
    const val RUSSIAN_TWIST_MIN_ROTATION = 35f // Minimum omuz rotasyon açısı
    const val RUSSIAN_TWIST_MAX_BACK_LEAN = 60f // Sırtın geriye eğim açısı
    const val RUSSIAN_TWIST_MIN_BACK_ANGLE = 20f // Sırtın diklik açısı
    
    // ─── Heel Tap Eşikleri ───────────────────────────────────────────────────
    const val HEEL_TAP_LATERAL_REACH_THRESHOLD = 0.05f
    
    // ─── Bicycle Crunch Eşikleri ─────────────────────────────────────────────
    const val BICYCLE_CRUNCH_LEG_ANGLE_MIN = 15f
    const val BICYCLE_CRUNCH_LEG_ANGLE_MAX = 45f // 30 ideal ama 45'e kadar tolerans
    
    // ─── Reverse Crunch Eşikleri ─────────────────────────────────────────────
    const val REVERSE_CRUNCH_HIP_LIFT_MIN = 0.05f
    
    // ─── Leg Extension Eşikleri ──────────────────────────────────────────────
    const val LEG_EXTENSION_KNEE_ANGLE_MAX = 170f
    
    // ─── Straight Leg Crunch Eşikleri ────────────────────────────────────────
    const val STRAIGHT_LEG_CRUNCH_LEG_ANGLE_MIN = 75f
    const val STRAIGHT_LEG_CRUNCH_LEG_ANGLE_MAX = 105f
}
