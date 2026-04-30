package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreUser
import kotlinx.coroutines.flow.Flow

/**
 * Kullanıcı profil yönetimi için domain-layer sözleşmesi.
 */
interface IUserRepository {

    /** Kullanıcı profilini Room'dan reaktif dinler. */
    fun observeCurrentUser(uid: String): Flow<UserEntity?>

    /** Uzmanın bağlı hastalarını reaktif dinler. */
    fun observePatients(expertUid: String): Flow<List<UserEntity>>

    /** Profil bilgisini günceller (Firestore + Room). */
    suspend fun updateProfile(uid: String, updatedUser: UserEntity): Result<Unit>

    /** Hastayı e-posta ile Firestore'da arar (sadece PATIENT rolü). */
    suspend fun findPatientByEmail(email: String): FirestoreUser?

    /** Canlı e-posta araması (autocomplete için). */
    suspend fun searchPatientsByEmail(query: String): List<FirestoreUser>

    /** Uzman, hastayı kendi listesine ekler. */
    suspend fun linkPatientToExpert(patientUid: String, expertUid: String): Result<Unit>

    /** Uzmandan hastaya bağlantı isteği gönderir. */
    suspend fun sendConnectionRequest(patient: FirestoreUser, doctor: UserEntity): Result<Unit>

    /** Hastaya gelen bekleyen bağlantı isteklerini çeker. */
    suspend fun getPendingRequests(patientId: String): List<FirestorePatientRequest>

    /** Uzmanın gönderdiği tüm istekleri çeker. */
    suspend fun getSentRequestsByDoctor(doctorId: String): List<FirestorePatientRequest>

    /** Bağlantı isteğini yanıtlar. ACCEPTED ise bağı kurar. */
    suspend fun respondToConnectionRequest(
        requestId: String,
        status: String,
        patientUid: String,
        expertUid: String
    ): Result<Unit>

    /** Uzmanın bağlı hastalarını Firestore'dan çekip Room'a yazar. */
    suspend fun syncPatientsForExpert(expertUid: String)

    /** Uzman profilini Firestore'dan çekip lokal cache'e yazar. */
    suspend fun syncExpertProfileLocally(expertUid: String)
}
