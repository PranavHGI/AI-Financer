package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.model.ChatMessage
import com.example.aifinancerfree.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvisorViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        // Pre-populate with a dynamic welcoming message
        _messages.value = listOf(
            ChatMessage(
                role = "model",
                content = "Hello! I'm your personalized AI Advisor. I have analyzed your live transaction details and monthly budget limits. Ask me anything about your current spending or how to save money!"
            )
        )
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isSending.value) return

        val userMessage = ChatMessage(role = "user", content = trimmed)
        _messages.value = _messages.value + userMessage
        _isSending.value = true

        viewModelScope.launch {
            try {
                // Drop the last message (which is the current query) to get historical context
                val history = _messages.value.dropLast(1)
                val result = repository.chatWithAdvisor(trimmed, history)
                _messages.value = _messages.value + ChatMessage(role = "model", content = result.response)
            } catch (e: Exception) {
                android.util.Log.e("AdvisorViewModel", "Failed to query advisor", e)
                val errorMsg = when (e) {
                    is java.net.SocketTimeoutException ->
                        "The server is taking time to respond (Render free instances take ~50s to wake up from cold sleep). Please tap again to retry."
                    is retrofit2.HttpException -> {
                        when (e.code()) {
                            401 -> "Session expired. Please log out and log in again."
                            502, 503, 504 -> "AI service is temporarily busy. Please retry in a few moments."
                            else -> "Server error (${e.code()}). Please try again."
                        }
                    }
                    is java.io.IOException ->
                        "Could not connect to the AI Advisor server. Please check your internet connection or verify the server status."
                    else ->
                        "An error occurred: ${e.localizedMessage ?: "Unknown error"}. Please try again."
                }
                _messages.value = _messages.value + ChatMessage(
                    role = "model",
                    content = errorMsg
                )
            } finally {
                _isSending.value = false
            }
        }
    }
}
