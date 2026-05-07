package com.example.exerciseformanalyzer.domain.repository

import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.firestore.FirestorePatientRequest
import com.example.exerciseformanalyzer.model.firestore.FirestoreRelationshipNotification
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

    /** Uzmanın gönderdiği bekleyen bir isteği iptal eder. */
    suspend fun cancelConnectionRequest(requestId: String): Result<Unit>

    /** Hastaya gelen bekleyen bağlantı isteklerini çeker. */
    suspend fun getPendingRequests(patientId: String): List<FirestorePatientRequest>
    fun observePendingRequests(patientId: String): Flow<List<FirestorePatientRequest>>

    /** Uzmanın gönderdiği tüm istekleri çeker. */
    suspend fun getSentRequestsByDoctor(doctorId: String): List<FirestorePatientRequest>
    fun observeSentRequestsByDoctor(doctorId: String): Flow<List<FirestorePatientRequest>>

    /** Bağlantı isteğini yanıtlar. ACCEPTED ise bağı kurar. */
    suspend fun respondToConnectionRequest(
        requestId: String,
        status: String,
        patientUid: String,
        expertUid: String,
        patientName: String = "",
        patientEmail: String = ""
    ): Result<Unit>

    /** Hastayı uzmanın listesinden kaldır. */
    suspend fun acceptConnectionRequestWithSingleExpertRule(
        request: FirestorePatientRequest,
        currentExpertId: String?
    ): Result<Unit>

    suspend fun unlinkCurrentExpertByPatient(
        patientId: String,
        oldExpertId: String,
        patientName: String
    ): Result<Unit>

    suspend fun removePatientFromExpert(patientId: String, expertId: String): Result<Unit>

    fun observeRelationshipNotifications(expertId: String): Flow<List<FirestoreRelationshipNotification>>

    suspend fun dismissRelationshipNotification(notificationId: String): Result<Unit>

    /** Doktor-hasta ilişkisi aktif mi? */
    suspend fun isDoctorPatientRelationActive(doctorId: String, patientId: String): Boolean

    /** Uzmanın bağlı hastalarını Firestore'dan çekip Room'a yazar. */
    suspend fun syncPatientsForExpert(expertUid: String)

    /** Uzman profilini Firestore'dan çekip lokal cache'e yazar. */
    suspend fun syncExpertProfileLocally(expertUid: String)

    /** Profil resmini Firebase Storage'a yükler ve URL'i Firestore'a kaydeder. */
    suspend fun uploadProfileImage(uid: String, imageBytes: ByteArray): Result<String>
}
