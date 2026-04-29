package com.example.exerciseformanalyzer.domain.model

/**
 * Kimlik doğrulama işlemleri için sonuç sarmalayıcı.
 *
 * Not: Daha önce data/repository katmanında AuthResult olarak tanımlanmıştı.
 * Domain katmanına taşınarak katman bağımlılığı tersine çevrilmesi önlenmiştir.
 * ViewModel ve Use Case'ler bu türü kullanır; Data katmanı impl'ları da bunu döner.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
}
