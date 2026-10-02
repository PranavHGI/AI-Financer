package com.example.aifinancerfree.data.local

import android.content.Context
import android.content.SharedPreferences

class ConsentPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("consent_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val SMS_TRACKING_ENABLED_KEY = "sms_tracking_enabled"
        private const val CONSENT_TIMESTAMP_KEY = "consent_timestamp"
    }

    fun setSmsTrackingEnabled(enabled: Boolean) {
        prefs.edit().apply {
            putBoolean(SMS_TRACKING_ENABLED_KEY, enabled)
            if (enabled) {
                putLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis())
            } else {
                remove(CONSENT_TIMESTAMP_KEY)
            }
            apply()
        }
    }

    fun isSmsTrackingEnabled(): Boolean {
        return prefs.getBoolean(SMS_TRACKING_ENABLED_KEY, false)
    }

    fun getConsentTimestamp(): Long {
        return prefs.getLong(CONSENT_TIMESTAMP_KEY, 0L)
    }

    fun clearConsent() {
        prefs.edit().clear().apply()
    }
}
