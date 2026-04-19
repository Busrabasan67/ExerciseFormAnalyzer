package com.example.exerciseformanalyzer.data.local.entity

// Egzersiz kataloğu tablosu.
// Uygulama başladığında Firestore'dan çekilip lokal cache'e yazılır.
// Offline modda bu tablo kullanılır.
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Firestore döküman ID — senkronizasyon için
    val firebaseDocId: String? = null,

    val name: String,
    val category: String,           // "Kardiyo", "Güç", "Core", "Rehabilitasyon"
    val metValue: Float,            // MET değeri — kalori hesabı için (CalorieCalculator)
    val aiRulesJson: String,        // Yapay zeka açı sınırları (JSON)
    val description: String? = null,// Egzersiz açıklaması (ön izleme ekranında gösterilir)

    // Firebase Storage'dan gelen ön izleme içeriği URL'si.
    // Şimdilik null; ileride video/GIF eklenince dolar.
    // TODO: Firebase Storage entegrasyonu tamamlandığında bu URL doldurulacak.
    val previewVideoUrl: String? = null
)