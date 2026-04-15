package com.example.exerciseformanalyzer.data.local.entity
//Egzersiz Havuzu
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // "Kardiyo", "Güç" vb.
    val metValue: Float, // Kalori formülü için
    val aiRulesJson: String // Yapay zeka açı sınırları
)