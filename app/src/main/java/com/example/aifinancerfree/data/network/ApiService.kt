package com.example.aifinancerfree.data.network

import com.example.aifinancerfree.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    @POST("auth/register")
    suspend fun register(
        @Body body: Map<String, String>
    ): UserResponse

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest
    ): TokenResponse

    @POST("auth/logout")
    suspend fun logout(
        @Body body: RefreshRequest
    ): Map<String, String>

    @GET("auth/me")
    suspend fun getProfile(): UserResponse

    @GET("transactions")
    suspend fun getTransactions(): List<TransactionResponse>

    @POST("transactions")
    suspend fun createTransaction(
        @Body body: TransactionRequest
    ): TransactionResponse

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") id: String
    ): Map<String, Any>

    @POST("sms/import")
    suspend fun importSms(
        @Body body: SmsImportRequest
    ): SmsImportResponse

    @GET("budgets")
    suspend fun getBudgets(): List<BudgetResponse>

    @POST("budgets")
    suspend fun createBudget(
        @Body body: BudgetRequest
    ): BudgetResponse

    @Multipart
    @POST("transactions/ocr")
    suspend fun uploadReceipt(
        @Part file: MultipartBody.Part
    ): OcrResponse

    @POST("advisor/chat")
    suspend fun chatWithAdvisor(
        @Body request: AdvisorChatRequest
    ): AdvisorChatResponse

    @POST("ml/feedback")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest
    ): Map<String, Any>

    @POST("ml/retrain")
    suspend fun triggerRetrain(): RetrainResponse

    @GET("goals")
    suspend fun getGoals(): List<GoalResponse>

    @POST("goals")
    suspend fun createGoal(@Body request: GoalRequest): GoalResponse

    @POST("goals/{id}/save")
    suspend fun addGoalSavings(@Path("id") id: String, @Body request: AddSavingsRequest): GoalResponse

    @DELETE("goals/{id}")
    suspend fun deleteGoal(@Path("id") id: String): Map<String, Any>

    @GET("insights/summary")
    suspend fun getInsights(): InsightResponse
}
