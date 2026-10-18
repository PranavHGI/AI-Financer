package com.example.aifinancerfree.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aifinancerfree.data.local.ConsentPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for SMS ingestion access:
 * enabled = consentFlag (user agreement) && READ_SMS granted && RECEIVE_SMS granted.
 */
class SmsAccessManager(
    private val context: Context,
    private val consentPreferences: ConsentPreferences
) {
    private val _isAccessEnabled = MutableStateFlow(isAccessEnabled())
    val isAccessEnabled: StateFlow<Boolean> = _isAccessEnabled.asStateFlow()

    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReceiveSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllRequiredPermissions(): Boolean {
        return hasReadSmsPermission() && hasReceiveSmsPermission()
    }

    fun isConsentGranted(): Boolean {
        return consentPreferences.isSmsTrackingEnabled()
    }

    fun isAccessEnabled(): Boolean {
        return isConsentGranted() && hasAllRequiredPermissions()
    }

    fun setConsentEnabled(enabled: Boolean) {
        consentPreferences.setSmsTrackingEnabled(enabled)
        refresh()
    }

    fun clearConsent() {
        consentPreferences.clearConsent()
        refresh()
    }

    fun refresh() {
        _isAccessEnabled.value = isAccessEnabled()
    }
}
