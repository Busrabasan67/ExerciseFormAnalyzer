package com.example.exerciseformanalyzer.domain.model

/**
 * Google sign-in can authenticate a Firebase account before the app profile
 * exists. Keep that distinction explicit so UI can ask for the role first.
 */
sealed class GoogleAuthResult {
    data class ExistingUser(val uid: String, val role: String) : GoogleAuthResult()
    data class RequiresRoleSelection(
        val uid: String,
        val fullName: String,
        val email: String
    ) : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}
