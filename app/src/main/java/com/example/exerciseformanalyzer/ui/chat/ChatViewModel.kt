package com.example.exerciseformanalyzer.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exerciseformanalyzer.data.remote.FirestoreService
import com.example.exerciseformanalyzer.model.firestore.FirestoreChatMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val firestoreService = FirestoreService()
    
    private val _messages = MutableStateFlow<List<FirestoreChatMessage>>(emptyList())
    val messages: StateFlow<List<FirestoreChatMessage>> = _messages.asStateFlow()

    private val _otherUser = MutableStateFlow<com.example.exerciseformanalyzer.model.firestore.FirestoreUser?>(null)
    val otherUser: StateFlow<com.example.exerciseformanalyzer.model.firestore.FirestoreUser?> = _otherUser.asStateFlow()

    private val _currentUser = MutableStateFlow<com.example.exerciseformanalyzer.model.firestore.FirestoreUser?>(null)
    val currentUser: StateFlow<com.example.exerciseformanalyzer.model.firestore.FirestoreUser?> = _currentUser.asStateFlow()

    private var observeJob: kotlinx.coroutines.Job? = null

    fun observeMessages(otherUid: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isEmpty() || otherUid.isEmpty()) return
        
        // Önceki mesajları temizle ki yeni odaya girince eskiler görünmesin
        _messages.value = emptyList()
        _otherUser.value = null

        // Profil bilgilerini çek
        viewModelScope.launch(Dispatchers.IO) {
            val otherProfile = firestoreService.getUserProfile(otherUid)
            _otherUser.value = otherProfile
            
            val currentProfile = firestoreService.getUserProfile(currentUid)
            _currentUser.value = currentProfile
        }

        // Önceki gözlemlemeyi iptal et (farklı bir odaya geçilmiş olabilir)
        observeJob?.cancel()
        
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            firestoreService.observeMessages(currentUid, otherUid).collect { msgList ->
                _messages.value = msgList
                markConversationRead(otherUid)
            }
        }
    }

    fun sendMessage(otherUid: String, text: String, context: android.content.Context? = null) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (text.isBlank() || currentUid.isEmpty() || otherUid.isEmpty()) return

        // İnternet kontrolü (Kullanıcı isteği: Mesaj gönderimi için internet gereksin)
        if (context != null && !com.example.exerciseformanalyzer.util.NetworkUtils.isNetworkAvailable(context)) {
            android.util.Log.e("ChatViewModel", "İnternet yok, mesaj gönderilemedi.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreService.sendMessage(
                    uid1 = currentUid,
                    uid2 = otherUid,
                    messageText = text.trim(),
                    senderId = currentUid
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Mesaj gönderilemedi: ${e.message}")
            }
        }
    }

    fun markConversationRead(otherUid: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isEmpty() || otherUid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { firestoreService.markChatAsRead(currentUid, otherUid) }
        }
    }
    
    fun getCurrentUid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
}
