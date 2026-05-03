package com.example.exerciseformanalyzer.data.local.entity

// Kullanıcı cache tablosu.
// Firestore'daki users/{uid} dökümanının lokal kopyasıdır.
// Giriş yapıldıktan sonra anlık erişim için Room'da tutulur.
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    // Lokal Room ID (otomatik artar)
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firebase Authentication'dan gelen benzersiz kullanıcı kimliği.
    // Firestore'da döküman ID olarak da kullanılır.
    // Google ile girişte bu alan kritiktir.
    val uid: String = "",

    val fullName: String,
    val email: String,

    // Lokal BCrypt hash (opsiyonel — Firebase Auth gelince arka planda kalır)
    val passwordHash: String? = null,

    // "PATIENT" | "EXPERT" | "ADMIN"
    val role: String,

    // Kullanıcı profil bilgileri
    val age: Int? = null,
    val weightKg: Float? = null,
    val heightCm: Float? = null,

    // Yeni Profil Alanları
    val firstName: String? = null,
    val lastName: String? = null,
    val diseaseInfo: String? = null,
    val hasHernia: Boolean = false,
    val hasMeniscus: Boolean = false,

    // Akıllı Öneri Alanları
    val activityLevel: String? = "medium", // low, medium, high
    val goal: String? = "general_health", // lose_weight, gain_muscle, rehab, general_health
    val painAreasJson: String? = "[]", // ["diz", "bel"]
    val exerciseLevel: String? = "beginner", // beginner, intermediate, advanced

    // Hastalık listesi JSON olarak saklanır: ["meniscus", "hernia", "back_pain"]
    // NOT: Room ayrı bir tablo açmak yerine JSON string tercih edildi çünkü
    //      bu liste nadiren sorgulanır ve basit string işlemi yeterlidir.
    val diseasesJson: String? = null,

    val isSmoker: Boolean = false,
    val isDrinker: Boolean = false,

    // Hastanın bağlı olduğu uzman UID'si (sadece PATIENT rolü için)
    val expertUid: String? = null,

    // Senkronizasyon bayrağı: false = Firestore'a henüz yüklenmedi
    val isSynced: Boolean = false,

    // --- OYUNLAŞTIRMA VE SOSYAL (FAZ 4) ---
    val xp: Int = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val lastExerciseDate: String? = null, // "yyyy-MM-dd"
    val badgesJson: String? = null, // Kazanılan rozet ID'leri: ["badge_1", "badge_5"]
    val xpMultiplier: Float = 1.0f, // Doktor/Admin tarafından değiştirilebilir zorluk
    val fcmToken: String? = null,

    // Kullanıcı durumu: "ACTIVE", "PASSIVE", "DELETED"
    val status: String = "ACTIVE",

    val defaultRestSeconds: Int = 90,
    val profileImageUrl: String? = null,
    val gender: String = ""
)