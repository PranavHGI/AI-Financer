package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.model.GoalResponse
import com.example.aifinancerfree.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GoalsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _goals = MutableStateFlow<List<GoalResponse>>(emptyList())
    val goals: StateFlow<List<GoalResponse>> = _goals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun fetchGoals() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _goals.value = repository.getGoals()
            } catch (e: Exception) {
                _goals.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createGoal(title: String, targetAmount: Double, savedAmount: Double, monthsRemaining: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createGoal(title, targetAmount, savedAmount, monthsRemaining)
                fetchGoals()
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun addSavings(goalId: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addGoalSavings(goalId, amount)
                fetchGoals()
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteGoal(goalId)
                fetchGoals()
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }
}
