package com.example.aifinancerfree.data.model

import com.google.gson.annotations.SerializedName

data class GoalRequest(
    @SerializedName("title") val title: String,
    @SerializedName("saved_amount") val savedAmount: Double,
    @SerializedName("target_amount") val targetAmount: Double,
    @SerializedName("months_remaining") val monthsRemaining: Int
)

data class GoalResponse(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("saved_amount") val savedAmount: Double,
    @SerializedName("target_amount") val targetAmount: Double,
    @SerializedName("monthly_saving_estimate") val monthlySavingEstimate: Double,
    @SerializedName("months_remaining") val monthsRemaining: Int
)

data class AddSavingsRequest(
    @SerializedName("amount") val amount: Double
)
