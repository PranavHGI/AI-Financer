package com.example.aifinancerfree.data.model

import com.google.gson.annotations.SerializedName

data class TransactionRequest(
    @SerializedName("type") val type: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("category") val category: String,
    @SerializedName("merchant") val merchant: String,
    @SerializedName("timestamp") val timestamp: Double,
    @SerializedName("source") val source: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("masked_account_ref") val maskedAccountRef: String = ""
)

data class TransactionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("type") val type: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("category") val category: String,
    @SerializedName("merchant") val merchant: String,
    @SerializedName("timestamp") val timestamp: Double,
    @SerializedName("source") val source: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("masked_account_ref") val maskedAccountRef: String = "",
    @SerializedName("confidence") val confidence: Double
)

data class SmsImportItem(
    @SerializedName("sender") val sender: String,
    @SerializedName("body") val body: String,
    @SerializedName("timestamp") val timestamp: Long
)

data class SmsImportRequest(
    @SerializedName("sms_list") val smsList: List<SmsImportItem>
)

data class SmsImportResponse(
    @SerializedName("imported_count") val importedCount: Int,
    @SerializedName("skipped_count") val skippedCount: Int
)

data class BudgetRequest(
    @SerializedName("category") val category: String,
    @SerializedName("amount") val amount: Double
)

data class BudgetResponse(
    @SerializedName("category") val category: String,
    @SerializedName("amount") val amount: Double
)

data class FeedbackRequest(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("original_category") val originalCategory: String,
    @SerializedName("corrected_category") val correctedCategory: String
)

data class RetrainResponse(
    @SerializedName("status") val status: String,
    @SerializedName("accuracy") val accuracy: Double,
    @SerializedName("samples_count") val samplesCount: Int
)
