package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
    data object NetworkError : AuthUiState
    data object DuplicateUser : AuthUiState
    data object InvalidCredentials : AuthUiState
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Fields cannot be empty")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.login(username, password)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState.Success
                }
                is AuthResult.InvalidCredentials -> {
                    _uiState.value = AuthUiState.InvalidCredentials
                }
                is AuthResult.NetworkError -> {
                    _uiState.value = AuthUiState.NetworkError
                }
                is AuthResult.DuplicateUser -> {
                    _uiState.value = AuthUiState.DuplicateUser
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Fields cannot be empty")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error("Invalid email format")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.register(username, email, password)) {
                is AuthResult.Success -> {
                    login(username, password)
                }
                is AuthResult.DuplicateUser -> {
                    _uiState.value = AuthUiState.DuplicateUser
                }
                is AuthResult.NetworkError -> {
                    _uiState.value = AuthUiState.NetworkError
                }
                is AuthResult.InvalidCredentials -> {
                    _uiState.value = AuthUiState.InvalidCredentials
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
        }
    }
}
