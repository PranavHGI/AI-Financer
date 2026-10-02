package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.model.UserResponse
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(val user: UserResponse) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
    data object Unauthenticated : ProfileUiState
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(tokenManager.getAccessToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        fetchProfile()
    }

    fun fetchProfile() {
        if (tokenManager.getAccessToken() == null) {
            _uiState.value = ProfileUiState.Unauthenticated
            _isLoggedIn.value = false
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            when (val result = authRepository.getProfile()) {
                is AuthResult.Success -> {
                    _uiState.value = ProfileUiState.Success(result.data)
                    _isLoggedIn.value = true
                }
                is AuthResult.InvalidCredentials -> {
                    _uiState.value = ProfileUiState.Unauthenticated
                    _isLoggedIn.value = false
                }
                is AuthResult.NetworkError -> {
                    _uiState.value = ProfileUiState.Error("Network error. Please try again.")
                }
                else -> {
                    _uiState.value = ProfileUiState.Error("Could not retrieve profile info.")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            authRepository.logout()
            _isLoggedIn.value = false
            _uiState.value = ProfileUiState.Unauthenticated
        }
    }
}
