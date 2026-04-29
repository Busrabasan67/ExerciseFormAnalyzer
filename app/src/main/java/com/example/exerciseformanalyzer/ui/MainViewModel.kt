package com.example.exerciseformanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.MainApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Ana ekranın ViewModel'i — Tema ve Dil tercihleri gibi uygulama geneli durumları yönetir.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = (application as MainApplication).userPreferencesRepository

    val isDarkMode: StateFlow<Boolean> = preferencesRepository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentLanguage: StateFlow<String> = preferencesRepository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "tr")

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    fun setLanguage(langCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setLanguage(langCode)
        }
        val newLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(newLocale)
    }
}