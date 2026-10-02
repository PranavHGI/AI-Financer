package com.example.aifinancerfree.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.model.OcrResponse
import com.example.aifinancerfree.data.repository.LocalBudget
import com.example.aifinancerfree.data.repository.LocalTransaction
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.data.model.InsightResponse
import com.example.aifinancerfree.data.sms.SmsSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.util.Calendar

class FinanceViewModel(
    private val repository: TransactionRepository,
    private val dbHelper: DatabaseHelper
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<LocalTransaction>>(emptyList())
    val transactions: StateFlow<List<LocalTransaction>> = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<LocalBudget>>(emptyList())
    val budgets: StateFlow<List<LocalBudget>> = _budgets.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Computed Stats
    private val _totalIncome = MutableStateFlow(0.0)
    val totalIncome: StateFlow<Double> = _totalIncome.asStateFlow()

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent.asStateFlow()

    private val _accountBalances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val accountBalances: StateFlow<Map<String, Double>> = _accountBalances.asStateFlow()

    private val _insights = MutableStateFlow<InsightResponse?>(null)
    val insights: StateFlow<InsightResponse?> = _insights.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDefaultBudgets()
            loadData()
            syncRemote()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val txs = repository.getLocalTransactions()
            val bdgts = repository.getLocalBudgets()

            _transactions.value = txs
            _budgets.value = bdgts

            calculateStats(txs)
            loadInsights()
        }
    }

    fun loadInsights() {
        viewModelScope.launch {
            try {
                _insights.value = repository.getInsights()
            } catch (e: Exception) {
                Log.e("FinanceViewModel", "Failed to fetch insights", e)
            }
        }
    }

    private fun calculateStats(txs: List<LocalTransaction>) {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val cal = Calendar.getInstance()

        var incomeSum = 0.0
        var spentSum = 0.0
        val balances = mutableMapOf<String, Double>()

        for (tx in txs) {
            cal.timeInMillis = tx.date
            val txMonth = cal.get(Calendar.MONTH)
            val txYear = cal.get(Calendar.YEAR)

            val isCurrentMonth = txMonth == currentMonth && txYear == currentYear

            // Monthly progress calculations
            if (isCurrentMonth) {
                if (tx.type == "income") {
                    incomeSum += tx.amount
                } else {
                    spentSum += tx.amount
                }
            }

            // Account balances sums (all time)
            val account = tx.account
            val currentBal = balances.getOrDefault(account, 0.0)
            if (tx.type == "income") {
                balances[account] = currentBal + tx.amount
            } else {
                balances[account] = currentBal - tx.amount
            }
        }

        _totalIncome.value = incomeSum
        _totalSpent.value = spentSum
        _accountBalances.value = balances
    }

    fun addTransaction(
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        account: String,
        notes: String?,
        date: Long
    ) {
        viewModelScope.launch {
            repository.addTransaction(type, amount, category, merchant, account, notes, date)
            loadData()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteLocalTransaction(id)
            loadData()
        }
    }

    fun correctTransactionCategory(
        transactionId: Long,
        remoteId: String?,
        originalCategory: String,
        correctedCategory: String
    ) {
        viewModelScope.launch {
            repository.submitFeedback(
                transactionId.toString(),
                remoteId,
                originalCategory,
                correctedCategory
            )
            loadData()
        }
    }

    fun updateBudgetLimit(category: String, limit: Double) {
        viewModelScope.launch {
            repository.updateBudgetLimit(category, limit)
            loadData()
        }
    }

    fun syncRemote() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncPendingTransactions()
                repository.fetchFromRemote()
                loadData()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun scanSmsInbox(context: Context, onSyncComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val syncManager = SmsSyncManager(context, dbHelper)
                val newCount = syncManager.syncInbox()
                
                if (newCount > 0) {
                    repository.syncPendingTransactions()
                    loadData()
                }
                onSyncComplete(newCount)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun uploadReceipt(context: Context, uri: android.net.Uri, onComplete: (OcrResponse?, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to open image stream")
                val bytes = inputStream.readBytes()
                inputStream.close()

                val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                val body = MultipartBody.Part.createFormData("file", "receipt.jpg", requestFile)

                val response = repository.uploadReceipt(body)
                onComplete(response, null)
            } catch (e: Exception) {
                Log.e("FinanceViewModel", "Failed to upload receipt", e)
                onComplete(null, e.localizedMessage ?: "Failed to upload receipt")
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
