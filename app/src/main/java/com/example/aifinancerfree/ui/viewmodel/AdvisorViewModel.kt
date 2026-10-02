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
                _messages.value = _messages.value + ChatMessage(
                    role = "model",
                    content = "Could not reach the AI Advisor server. Please make sure your server is online and port forwarding is configured."
                )
            } finally {
                _isSending.value = false
            }
        }
    }
}
