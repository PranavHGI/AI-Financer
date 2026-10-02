package com.example.aifinancerfree.data.model

import com.google.gson.annotations.SerializedName

data class RecurringBill(
    @SerializedName("merchant") val merchant: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("frequency_days") val frequencyDays: Int
)

data class AnomalyAlert(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("merchant") val merchant: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("category") val category: String,
    @SerializedName("timestamp") val timestamp: Double
)

data class DuplicateAlert(
    @SerializedName("merchant") val merchant: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("count") val count: Int
)

data class InsightResponse(
    @SerializedName("top_category") val topCategory: String,
    @SerializedName("monthly_change_percent") val monthlyChangePercent: Double,
    @SerializedName("next_month_prediction") val nextMonthPrediction: Double,
    @SerializedName("recurring_bills") val recurringBills: List<RecurringBill>,
    @SerializedName("duplicates") val duplicates: List<DuplicateAlert>,
    @SerializedName("anomalies") val anomalies: List<AnomalyAlert>,
    @SerializedName("disclaimer") val disclaimer: String
)
