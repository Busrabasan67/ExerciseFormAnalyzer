package com.example.exerciseformanalyzer.util

// NetworkUtils — Cihazın anlık internet bağlantısını kontrol eder.
// SyncWorker WorkManager tarafından otomatik yönlendirilir ancak
// UI üzerinden manuel "Yenile" veya senkronizasyon tetiklemelerinde kullanışlıdır.

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    /**
     * Cihazın şu an internete bağlı olup olmadığını kontrol eder.
     * @param context Application context
     * @return Bağlantı varsa true, yoksa false
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
