package com.example.aifinancerfree.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_preferences")
private val themeModeKey = stringPreferencesKey("theme_mode")

class ThemePreferences(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            runCatching { ThemeMode.valueOf(preferences[themeModeKey] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM)
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences -> preferences[themeModeKey] = mode.name }
    }
}
