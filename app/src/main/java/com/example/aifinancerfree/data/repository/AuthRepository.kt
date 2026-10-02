package com.example.aifinancerfree.data.repository

import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.model.RefreshRequest
import com.example.aifinancerfree.data.model.TokenResponse
import com.example.aifinancerfree.data.model.UserResponse
import com.example.aifinancerfree.data.network.ApiService
import retrofit2.HttpException
import java.io.IOException

sealed interface AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>
    data class Error(val message: String) : AuthResult<Nothing>
    data object NetworkError : AuthResult<Nothing>
    data object DuplicateUser : AuthResult<Nothing>
    data object InvalidCredentials : AuthResult<Nothing>
}

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun register(username: String, email: String, password: String): AuthResult<UserResponse> {
        return try {
            val body = mapOf("username" to username, "email" to email, "password" to password)
            val response = apiService.register(body)
            AuthResult.Success(response)
        } catch (e: HttpException) {
            val errorMsg = e.response()?.errorBody()?.string() ?: ""
            if (e.code() == 400 && (errorMsg.contains("Username already registered") || errorMsg.contains("Email already registered"))) {
                AuthResult.DuplicateUser
            } else {
                AuthResult.Error(parseErrorMessage(errorMsg))
            }
        } catch (e: IOException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown registration error")
        }
    }

    suspend fun login(username: String, password: String): AuthResult<TokenResponse> {
        return try {
            val response = apiService.login(username, password)
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            AuthResult.Success(response)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                AuthResult.InvalidCredentials
            } else {
                val errorMsg = e.response()?.errorBody()?.string() ?: ""
                AuthResult.Error(parseErrorMessage(errorMsg))
            }
        } catch (e: IOException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown login error")
        }
    }

    suspend fun getProfile(): AuthResult<UserResponse> {
        return try {
            val response = apiService.getProfile()
            AuthResult.Success(response)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                AuthResult.InvalidCredentials
            } else {
                val errorMsg = e.response()?.errorBody()?.string() ?: ""
                AuthResult.Error(parseErrorMessage(errorMsg))
            }
        } catch (e: IOException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Error retrieving profile")
        }
    }

    suspend fun logout(): AuthResult<Unit> {
        val refreshToken = tokenManager.getRefreshToken()
        return try {
            if (refreshToken != null) {
                apiService.logout(RefreshRequest(refreshToken))
            }
            tokenManager.clearTokens()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            tokenManager.clearTokens()
            AuthResult.Success(Unit)
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val regex = """"detail"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(errorBody)?.groupValues?.get(1) ?: "An error occurred"
        } catch (e: Exception) {
            "An error occurred"
        }
    }
}
