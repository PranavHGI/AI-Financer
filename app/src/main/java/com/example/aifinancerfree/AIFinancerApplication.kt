package com.example.aifinancerfree

import android.app.Application
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.network.ApiClient
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.data.sms.SmsAccessManager

class AIFinancerApplication : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var consentPreferences: ConsentPreferences
        private set

    lateinit var databaseHelper: DatabaseHelper
        private set

    lateinit var transactionRepository: TransactionRepository
        private set

    lateinit var smsAccessManager: SmsAccessManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenManager = TokenManager(this)
        apiClient = ApiClient(tokenManager)
        authRepository = AuthRepository(apiClient.apiService, tokenManager)
        consentPreferences = ConsentPreferences(this)
        databaseHelper = DatabaseHelper(this)
        transactionRepository = TransactionRepository(apiClient.apiService, databaseHelper, tokenManager)
        smsAccessManager = SmsAccessManager(this, consentPreferences)
    }

    companion object {
        lateinit var instance: AIFinancerApplication
            private set
    }
}
