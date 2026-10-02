package com.example.aifinancerfree.data.model

data class Transaction(
    val id: String,
    val type: String, // "income" or "expense"
    val amount: String, // e.g. "₹250", "+₹8,000"
    val category: String,
    val merchant: String,
    val timestamp: Long,
    val source: String, // "manual", "sms", "ocr", "voice"
    val description: String = "",
    val maskedAccountRef: String = "",
    val confidence: Float = 1.0f,
    val duplicateFingerprint: String = ""
)
