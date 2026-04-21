package com.example.exerciseformanalyzer.data.preferences

// UserPreferencesRepository — Basit kullanıcı ayarlarını DataStore'da saklar.
//
// Neden DataStore (Room değil)?
//   - Tema ve dil tercihi ilişkisel veritabanı gerektirmiyor; key-value yapısı yeterli.
//   - DataStore, SharedPreferences'ın tip-güvenli, coroutine uyumlu modern alternatifi.
//   - Room asıl güçlü yapısal/ilişkisel verilerde (Rapor, Plan, Kullanıcı) kullanılıyor.
//   - DataStore Flow dönerek Compose ile reaktif çalışır (collectAsState ile).

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Context extension — DataStore singleton (tek örnek)
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    // Sabit anahtar tanımları
    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val LANGUAGE_KEY = stringPreferencesKey("language")    // "tr" | "en"
        val CURRENT_USER_UID_KEY = stringPreferencesKey("current_uid") // Hızlı erişim için
        val CURRENT_USER_ROLE_KEY = stringPreferencesKey("current_role")
        val REMEMBER_ME_KEY = booleanPreferencesKey("remember_me")
        val LOGIN_TIMESTAMP_KEY = longPreferencesKey("login_timestamp")
    }

    // =========================================================
    // TEMA
    // =========================================================

    /** Karanlık mod tercihi — UI'ın her açılışında bu değeri okuyacak */
    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: false   // Default: açık tema
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    // =========================================================
    // DİL
    // =========================================================

    /** Dil tercihi — "tr" veya "en" */
    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "tr"    // Default: Türkçe
    }

    suspend fun setLanguage(langCode: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = langCode
        }
    }

    // =========================================================
    // KULLANICI BİLGİSİ CACHE'İ (Hızlı erişim için)
    // =========================================================

    /** Giriş yapan kullanıcının UID'si — Giriş sonrası yazılır, çıkışta silinir */
    val currentUserUid: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_USER_UID_KEY] ?: ""
    }

    val currentUserRole: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_USER_ROLE_KEY] ?: ""
    }

    suspend fun saveUserSession(uid: String, role: String, rememberMe: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_USER_UID_KEY] = uid
            prefs[CURRENT_USER_ROLE_KEY] = role
            prefs[REMEMBER_ME_KEY] = rememberMe
            prefs[LOGIN_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    val rememberMe: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[REMEMBER_ME_KEY] ?: false
    }

    val loginTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LOGIN_TIMESTAMP_KEY] ?: 0L
    }

    /** Çıkış yapıldığında UID ve rol'ü temizle */
    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(CURRENT_USER_UID_KEY)
            prefs.remove(CURRENT_USER_ROLE_KEY)
            prefs.remove(REMEMBER_ME_KEY)
            prefs.remove(LOGIN_TIMESTAMP_KEY)
        }
    }
}
