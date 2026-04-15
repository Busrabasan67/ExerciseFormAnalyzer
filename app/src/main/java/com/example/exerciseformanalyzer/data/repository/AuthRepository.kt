package com.example.exerciseformanalyzer.data.repository
//Giriş/Kayıt işlemleri
//Uygulamanın geri kalanı (ViewModel, UI ve Yapay Zeka) sadece bu dosyalarla konuşacak.
//Arayüz ile Veritabanı Arasındaki Köprü (Repository)
import com.example.exerciseformanalyzer.data.local.dao.UserDao
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import org.mindrot.jbcrypt.BCrypt // Şifreleme kütüphanesi

class AuthRepository(private val userDao: UserDao) {

    suspend fun registerUser(fullName: String, email: String, plainPassword: String, role: String): Boolean {
        // Şifreyi güvenlik için hashliyoruz
        val hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt())

        val newUser = UserEntity(
            fullName = fullName,
            email = email,
            passwordHash = hashedPassword,
            role = role
        )
        userDao.insertUser(newUser)
        return true // Gelecekte buraya Firebase eklenecek
    }

    suspend fun loginWithEmail(email: String, plainPassword: String): UserEntity? {
        val user = userDao.getUserByEmail(email)
        if (user != null && user.passwordHash != null) {
            // Girilen şifre ile veritabanındaki hash'i karşılaştırıyoruz
            if (BCrypt.checkpw(plainPassword, user.passwordHash)) {
                return user // Giriş başarılı
            }
        }
        return null // Giriş başarısız
    }
}