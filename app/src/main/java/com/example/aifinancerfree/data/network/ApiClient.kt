package com.example.aifinancerfree.data.network

import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(private val tokenManager: TokenManager) {
    // When deployed on Render, update this to your Render URL: e.g., "https://aifinancer-backend.onrender.com/"
    private val BASE_URL = "http://10.34.201.140:8085/"

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        
        tokenManager.getAccessToken()?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        chain.proceed(requestBuilder.build())
    }

    private val authenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) >= 2) {
                return null
            }

            val refreshToken = tokenManager.getRefreshToken() ?: return null

            synchronized(this) {
                val currentToken = tokenManager.getAccessToken()
                val originalRequestToken = response.request.header("Authorization")?.replace("Bearer ", "")
                
                var isTokenRefreshed = currentToken != originalRequestToken

                if (!isTokenRefreshed) {
                    try {
                        val rawRetrofit = Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        val service = rawRetrofit.create(ApiService::class.java)
                        
                        val refreshResponse = runBlocking {
                            service.refresh(RefreshRequest(refreshToken))
                        }
                        
                        tokenManager.saveTokens(refreshResponse.accessToken, refreshResponse.refreshToken)
                        isTokenRefreshed = true
                    } catch (e: Exception) {
                        tokenManager.clearTokens()
                        isTokenRefreshed = false
                    }
                }

                return if (isTokenRefreshed) {
                    val newAccessToken = tokenManager.getAccessToken()
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }
        return result
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(authenticator)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
