package com.example.aifinancerfree.data.repository

import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.model.BudgetRequest
import com.example.aifinancerfree.data.model.TransactionRequest
import com.example.aifinancerfree.data.model.TransactionResponse
import com.example.aifinancerfree.data.model.OcrResponse
import com.example.aifinancerfree.data.model.ChatMessage
import com.example.aifinancerfree.data.model.AdvisorChatRequest
import com.example.aifinancerfree.data.model.AdvisorChatResponse
import com.example.aifinancerfree.data.model.FeedbackRequest
import com.example.aifinancerfree.data.model.GoalRequest
import com.example.aifinancerfree.data.model.GoalResponse
import com.example.aifinancerfree.data.model.AddSavingsRequest
import com.example.aifinancerfree.data.model.InsightResponse
import com.example.aifinancerfree.data.network.ApiService
import okhttp3.MultipartBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalTransaction(
    val id: Long,
    val remoteId: String?,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val account: String,
    val notes: String?,
    val date: Long,
    val synced: Boolean
)

data class LocalBudget(
    val category: String,
    val limitAmount: Double
)

class TransactionRepository(
    private val apiService: ApiService,
    private val dbHelper: DatabaseHelper
) {
    private val syncMutex = Mutex()
    companion object {
        private const val TAG = "TransactionRepository"
        val DEFAULT_CATEGORIES = listOf("Food", "Travel", "Shopping", "Utilities", "Entertainment", "Health", "Others")
    }

    suspend fun initializeDefaultBudgets() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        for (category in DEFAULT_CATEGORIES) {
            val cursor = db.rawQuery("SELECT 1 FROM ${DatabaseHelper.TABLE_BUDGETS} WHERE ${DatabaseHelper.COL_BUDGET_CATEGORY} = ?", arrayOf(category))
            if (cursor.count == 0) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_BUDGET_CATEGORY, category)
                    put(DatabaseHelper.COL_BUDGET_LIMIT, 5000.0) // Default limit is 5000
                }
                db.insert(DatabaseHelper.TABLE_BUDGETS, null, values)
            }
            cursor.close()
        }
    }

    suspend fun getLocalTransactions(): List<LocalTransaction> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalTransaction>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_TRANSACTIONS} ORDER BY ${DatabaseHelper.COL_TX_DATE} DESC", null)
        
        if (cursor.moveToFirst()) {
            val idCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ID)
            val remoteCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_REMOTE_ID)
            val typeCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_TYPE)
            val amountCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_AMOUNT)
            val catCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_CATEGORY)
            val merchCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_MERCHANT)
            val acctCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ACCOUNT)
            val notesCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_NOTES)
            val dateCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_DATE)
            val syncCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_SYNCED)

            do {
                list.add(
                    LocalTransaction(
                        id = cursor.getLong(idCol),
                        remoteId = cursor.getString(remoteCol),
                        type = cursor.getString(typeCol),
                        amount = cursor.getDouble(amountCol),
                        category = cursor.getString(catCol),
                        merchant = cursor.getString(merchCol),
                        account = cursor.getString(acctCol),
                        notes = cursor.getString(notesCol),
                        date = cursor.getLong(dateCol),
                        synced = cursor.getInt(syncCol) == 1
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return@withContext list
    }

    suspend fun addTransaction(
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        account: String,
        notes: String?,
        date: Long
    ): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TX_TYPE, type)
            put(DatabaseHelper.COL_TX_AMOUNT, amount)
            put(DatabaseHelper.COL_TX_CATEGORY, category)
            put(DatabaseHelper.COL_TX_MERCHANT, merchant)
            put(DatabaseHelper.COL_TX_ACCOUNT, account)
            put(DatabaseHelper.COL_TX_NOTES, notes)
            put(DatabaseHelper.COL_TX_DATE, date)
            put(DatabaseHelper.COL_TX_SYNCED, 0)
        }
        val localId = db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, values)
        
        // Attempt network sync immediately
        syncPendingTransactions()
        return@withContext localId
    }

    suspend fun deleteLocalTransaction(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // Retrieve remote_id first to delete on the server if it exists
        var remoteId: String? = null
        val cursor = db.rawQuery("SELECT ${DatabaseHelper.COL_TX_REMOTE_ID} FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_ID} = ?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) {
            remoteId = cursor.getString(0)
        }
        cursor.close()

        // Delete locally
        db.delete(DatabaseHelper.TABLE_TRANSACTIONS, "${DatabaseHelper.COL_TX_ID} = ?", arrayOf(id.toString()))

        // Sync deletion
        if (!remoteId.isNullOrEmpty()) {
            try {
                apiService.deleteTransaction(remoteId)
                Log.d(TAG, "Successfully deleted remote transaction: $remoteId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete remote transaction", e)
            }
        }
    }

    suspend fun getLocalBudgets(): List<LocalBudget> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalBudget>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_BUDGETS}", null)
        if (cursor.moveToFirst()) {
            val catCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_BUDGET_CATEGORY)
            val limitCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_BUDGET_LIMIT)
            do {
                list.add(
                    LocalBudget(
                        category = cursor.getString(catCol),
                        limitAmount = cursor.getDouble(limitCol)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return@withContext list
    }

    suspend fun updateBudgetLimit(category: String, limit: Double) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_BUDGET_CATEGORY, category)
            put(DatabaseHelper.COL_BUDGET_LIMIT, limit)
        }
        db.insertWithOnConflict(
            DatabaseHelper.TABLE_BUDGETS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )

        // Sync budget to the server
        try {
            apiService.createBudget(BudgetRequest(category, limit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync budget to backend", e)
        }
    }

    /**
     * Uploads all unsynced local transaction records to the server database.
     */
    suspend fun syncPendingTransactions() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val db = dbHelper.writableDatabase
            val cursor = db.rawQuery("SELECT * FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_SYNCED} = 0", null)
            
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ID)
                val typeCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_TYPE)
                val amountCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_AMOUNT)
                val catCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_CATEGORY)
                val merchCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_MERCHANT)
                val acctCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ACCOUNT)
                val notesCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_NOTES)
                val dateCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_DATE)

                do {
                    val localId = cursor.getLong(idCol)
                    val type = cursor.getString(typeCol)
                    val amount = cursor.getDouble(amountCol)
                    val category = cursor.getString(catCol)
                    val merchant = cursor.getString(merchCol)
                    val account = cursor.getString(acctCol)
                    val notes = cursor.getString(notesCol)
                    val date = cursor.getLong(dateCol)

                    val request = TransactionRequest(
                        type = type,
                        amount = "₹${amount}",
                        category = category,
                        merchant = merchant,
                        timestamp = date / 1000.0,
                        source = if (notes?.contains("Parsed") == true) "sms" else "manual",
                        description = notes ?: "",
                        maskedAccountRef = account ?: ""
                    )

                    try {
                        val response = apiService.createTransaction(request)
                        val updateValues = ContentValues().apply {
                            put(DatabaseHelper.COL_TX_REMOTE_ID, response.id)
                            put(DatabaseHelper.COL_TX_SYNCED, 1)
                        }
                        db.update(DatabaseHelper.TABLE_TRANSACTIONS, updateValues, "${DatabaseHelper.COL_TX_ID} = ?", arrayOf(localId.toString()))
                        Log.d(TAG, "Synced transaction local_id=$localId to backend as remote_id=${response.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync transaction local_id=$localId to backend", e)
                        break // Stop sync loop if we hit a network issue
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
    }

    /**
     * Downloads recent transaction records from the server and caches them locally, avoiding duplicates.
     */
    suspend fun fetchFromRemote() = withContext(Dispatchers.IO) {
        try {
            val remoteTxs = apiService.getTransactions()
            val db = dbHelper.writableDatabase

            for (tx in remoteTxs) {
                // Check if this remote transaction is already cached
                val cursor = db.rawQuery("SELECT 1 FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_REMOTE_ID} = ?", arrayOf(tx.id))
                val alreadyExists = cursor.count > 0
                cursor.close()

                if (!alreadyExists) {
                    val values = ContentValues().apply {
                        put(DatabaseHelper.COL_TX_REMOTE_ID, tx.id)
                        put(DatabaseHelper.COL_TX_TYPE, tx.type)
                        
                        val cleanedAmount = tx.amount.replace("₹", "").replace("+", "").replace("-", "").replace(",", "").trim()
                        val amtDouble = cleanedAmount.toDoubleOrNull() ?: 0.0
                        put(DatabaseHelper.COL_TX_AMOUNT, amtDouble)

                        put(DatabaseHelper.COL_TX_CATEGORY, tx.category)
                        put(DatabaseHelper.COL_TX_MERCHANT, tx.merchant)
                        put(DatabaseHelper.COL_TX_ACCOUNT, if (tx.maskedAccountRef.isEmpty()) "Main Account" else tx.maskedAccountRef)
                        put(DatabaseHelper.COL_TX_NOTES, tx.description)
                        put(DatabaseHelper.COL_TX_DATE, (tx.timestamp * 1000).toLong())
                        put(DatabaseHelper.COL_TX_SYNCED, 1)
                    }
                    db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, values)
                }
            }

            // Sync budgets from backend as well
            val remoteBudgets = apiService.getBudgets()
            for (b in remoteBudgets) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_BUDGET_CATEGORY, b.category)
                    put(DatabaseHelper.COL_BUDGET_LIMIT, b.amount)
                }
                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_BUDGETS,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch transactions from remote backend", e)
        }
    }

    suspend fun uploadReceipt(file: MultipartBody.Part): OcrResponse {
        return apiService.uploadReceipt(file)
    }

    suspend fun chatWithAdvisor(message: String, history: List<ChatMessage>): AdvisorChatResponse {
        return apiService.chatWithAdvisor(AdvisorChatRequest(message, history))
    }

    suspend fun submitFeedback(
        transactionId: String,
        remoteId: String?,
        originalCategory: String,
        correctedCategory: String
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TX_CATEGORY, correctedCategory)
        }
        db.update(
            DatabaseHelper.TABLE_TRANSACTIONS,
            values,
            "${DatabaseHelper.COL_TX_REMOTE_ID} = ? OR ${DatabaseHelper.COL_TX_ID} = ?",
            arrayOf(remoteId ?: "", transactionId)
        )

        if (!remoteId.isNullOrBlank()) {
            try {
                apiService.submitFeedback(FeedbackRequest(remoteId, originalCategory, correctedCategory))
                apiService.triggerRetrain()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload category feedback to backend", e)
            }
        }
    }

    suspend fun getGoals(): List<GoalResponse> {
        return apiService.getGoals()
    }

    suspend fun createGoal(title: String, targetAmount: Double, savedAmount: Double, monthsRemaining: Int): GoalResponse {
        return apiService.createGoal(GoalRequest(title, savedAmount, targetAmount, monthsRemaining))
    }

    suspend fun addGoalSavings(goalId: String, amount: Double): GoalResponse {
        return apiService.addGoalSavings(goalId, AddSavingsRequest(amount))
    }

    suspend fun deleteGoal(goalId: String): Map<String, Any> {
        return apiService.deleteGoal(goalId)
    }

    suspend fun getInsights(): InsightResponse {
        return apiService.getInsights()
    }
}
