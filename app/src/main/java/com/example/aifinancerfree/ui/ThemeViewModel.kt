package com.example.aifinancerfree.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.ThemeMode
import com.example.aifinancerfree.data.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = ThemePreferences(application)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM
    )

    fun selectTheme(mode: ThemeMode) = viewModelScope.launch {
        preferences.setThemeMode(mode)
    }
}
