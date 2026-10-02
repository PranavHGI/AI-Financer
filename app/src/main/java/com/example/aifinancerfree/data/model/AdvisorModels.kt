package com.example.aifinancerfree.data.model

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String
)

data class AdvisorChatRequest(
    val message: String,
    val history: List<ChatMessage>
)

data class AdvisorChatResponse(
    val response: String
)
