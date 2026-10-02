package com.example.aifinancerfree.data.model

import com.google.gson.annotations.SerializedName

data class OcrResponse(
    @SerializedName("amount") val amount: Double,
    @SerializedName("merchant") val merchant: String,
    @SerializedName("category") val category: String,
    @SerializedName("date") val date: Long
)
