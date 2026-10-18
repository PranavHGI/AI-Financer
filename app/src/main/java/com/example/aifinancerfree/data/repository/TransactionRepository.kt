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
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalTransaction(
    val id: Long,
    val remoteId: String?,
    val clientId: String? = null,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val account: String,
    val notes: String?,
    val source: String = "manual",
    val date: Long,
    val synced: Boolean
)

data class LocalBudget(
    val category: String,
    val limitAmount: Double
)

class TransactionRepository(
    private val apiService: ApiService,
    private val dbHelper: DatabaseHelper,
    private val tokenManager: com.example.aifinancerfree.data.local.TokenManager? = null
) {
    private val syncMutex = Mutex()
    companion object {
        private const val TAG = "TransactionRepository"
        val DEFAULT_CATEGORIES = listOf("Food & dining", "Travel", "Shopping", "Education", "Bills", "Entertainment", "Health", "Salary", "Freelance", "Transfer", "Other")
    }

    private fun getActiveUserId(): String {
        return tokenManager?.getUserId() ?: tokenManager?.getUsername() ?: ""
    }

    suspend fun initializeDefaultBudgets() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val activeUser = getActiveUserId()
        if (activeUser.isEmpty()) return@withContext

        for (category in DEFAULT_CATEGORIES) {
            val cursor = db.rawQuery(
                "SELECT 1 FROM ${DatabaseHelper.TABLE_BUDGETS} WHERE ${DatabaseHelper.COL_BUDGET_CATEGORY} = ? AND ${DatabaseHelper.COL_BUDGET_USER_ID} = ?",
                arrayOf(category, activeUser)
            )
            if (cursor.count == 0) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_BUDGET_USER_ID, activeUser)
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
        val activeUser = getActiveUserId()
        if (activeUser.isEmpty()) return@withContext emptyList()

        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? ORDER BY ${DatabaseHelper.COL_TX_DATE} DESC",
            arrayOf(activeUser)
        )
        
        if (cursor.moveToFirst()) {
            val idCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ID)
            val remoteCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_REMOTE_ID)
            val clientCol = cursor.getColumnIndex(DatabaseHelper.COL_TX_CLIENT_ID)
            val typeCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_TYPE)
            val amountCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_AMOUNT)
            val catCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_CATEGORY)
            val merchCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_MERCHANT)
            val acctCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ACCOUNT)
            val notesCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_NOTES)
            val sourceCol = cursor.getColumnIndex(DatabaseHelper.COL_TX_SOURCE)
            val dateCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_DATE)
            val syncCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_SYNCED)

            do {
                val clientId = if (clientCol >= 0) cursor.getString(clientCol) else null
                val source = if (sourceCol >= 0) cursor.getString(sourceCol) ?: "manual" else "manual"
                list.add(
                    LocalTransaction(
                        id = cursor.getLong(idCol),
                        remoteId = cursor.getString(remoteCol),
                        clientId = clientId,
                        type = cursor.getString(typeCol),
                        amount = cursor.getDouble(amountCol),
                        category = cursor.getString(catCol),
                        merchant = cursor.getString(merchCol),
                        account = cursor.getString(acctCol),
                        notes = cursor.getString(notesCol),
                        source = source,
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
        date: Long,
        source: String = "manual"
    ): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val clientId = java.util.UUID.randomUUID().toString()
        val activeUser = getActiveUserId()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TX_USER_ID, activeUser)
            put(DatabaseHelper.COL_TX_CLIENT_ID, clientId)
            put(DatabaseHelper.COL_TX_TYPE, type)
            put(DatabaseHelper.COL_TX_AMOUNT, amount)
            put(DatabaseHelper.COL_TX_CATEGORY, category)
            put(DatabaseHelper.COL_TX_MERCHANT, merchant)
            put(DatabaseHelper.COL_TX_ACCOUNT, account)
            put(DatabaseHelper.COL_TX_NOTES, notes)
            put(DatabaseHelper.COL_TX_SOURCE, source)
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
        val activeUser = getActiveUserId()
        if (activeUser.isEmpty()) return@withContext emptyList()

        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_BUDGETS} WHERE ${DatabaseHelper.COL_BUDGET_USER_ID} = ?",
            arrayOf(activeUser)
        )
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
        val activeUser = getActiveUserId()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_BUDGET_USER_ID, activeUser)
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
     * Returns Pair(successfullyUploadedCount, totalPendingCount).
     */
    suspend fun syncPendingTransactions(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val db = dbHelper.writableDatabase
            val activeUser = getActiveUserId()
            if (activeUser.isEmpty()) return@withLock Pair(0, 0)

            val cursor = db.rawQuery(
                "SELECT * FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_SYNCED} = 0 AND ${DatabaseHelper.COL_TX_USER_ID} = ?",
                arrayOf(activeUser)
            )
            val pendingCount = cursor.count

            Log.i(TAG, "==================== [PENDING TX SYNC START] ====================")
            Log.i(TAG, "[SYNC PENDING] Found $pendingCount unsynced transaction(s) in local SQLite.")

            var syncSuccessCount = 0

            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ID)
                val clientCol = cursor.getColumnIndex(DatabaseHelper.COL_TX_CLIENT_ID)
                val typeCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_TYPE)
                val amountCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_AMOUNT)
                val catCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_CATEGORY)
                val merchCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_MERCHANT)
                val acctCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_ACCOUNT)
                val notesCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_NOTES)
                val sourceCol = cursor.getColumnIndex(DatabaseHelper.COL_TX_SOURCE)
                val dateCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TX_DATE)

                do {
                    val localId = cursor.getLong(idCol)
                    var clientId = if (clientCol >= 0) cursor.getString(clientCol) else null
                    if (clientId.isNullOrEmpty()) {
                        clientId = java.util.UUID.randomUUID().toString()
                        val updateClientId = ContentValues().apply {
                            put(DatabaseHelper.COL_TX_CLIENT_ID, clientId)
                        }
                        db.update(DatabaseHelper.TABLE_TRANSACTIONS, updateClientId, "${DatabaseHelper.COL_TX_ID} = ?", arrayOf(localId.toString()))
                    }

                    val type = cursor.getString(typeCol)
                    val amount = cursor.getDouble(amountCol)
                    val category = cursor.getString(catCol)
                    val merchant = cursor.getString(merchCol)
                    val account = cursor.getString(acctCol)
                    val notes = cursor.getString(notesCol)
                    val date = cursor.getLong(dateCol)
                    val source = if (sourceCol >= 0 && cursor.getString(sourceCol) != null) {
                        cursor.getString(sourceCol)
                    } else if (notes?.contains("Parsed") == true) {
                        "sms"
                    } else {
                        "manual"
                    }

                    val formattedAmount = "₹${amount}"
                    val request = TransactionRequest(
                        clientId = clientId,
                        type = type,
                        amount = formattedAmount,
                        category = category,
                        merchant = merchant,
                        timestamp = date / 1000.0,
                        source = source,
                        description = notes ?: "",
                        maskedAccountRef = account ?: ""
                    )

                    Log.i(
                        TAG,
                        "--> [CLIENT SENDING TX TO SERVER] local_id=$localId | clientId=$clientId | " +
                        "Payload: type='$type', amount='$formattedAmount', category='$category', merchant='$merchant', " +
                        "source='$source', timestamp=${date / 1000.0}, account='$account'"
                    )

                    try {
                        val response = apiService.createTransaction(request)

                        val updateValues = ContentValues().apply {
                            put(DatabaseHelper.COL_TX_REMOTE_ID, response.id)
                            put(DatabaseHelper.COL_TX_SYNCED, 1)
                        }
                        db.update(DatabaseHelper.TABLE_TRANSACTIONS, updateValues, "${DatabaseHelper.COL_TX_ID} = ?", arrayOf(localId.toString()))
                        syncSuccessCount++
                    } catch (e: HttpException) {
                        val errorBody = e.response()?.errorBody()?.string() ?: "none"
                        Log.e(
                            TAG,
                            "[HTTP SYNC ERROR] local_id=$localId failed with HTTP ${e.code()}: ${e.message()} | " +
                            "Error Body: $errorBody",
                            e
                        )
                        if (e.code() == 401) {
                            // Auth token invalid/expired, abort remaining batch
                            break
                        } else {
                            // Non-401 4xx: client error with this specific transaction, skip and continue batch
                            continue
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[NETWORK SYNC ERROR] local_id=$localId failed: ${e.localizedMessage}", e)
                        // Network error / server down, abort batch
                        break
                    }
                } while (cursor.moveToNext())

                Log.i(
                    TAG,
                    "==================== [PENDING TX SYNC COMPLETE] ====================\n" +
                    "Total Unsynced: $pendingCount | Successfully Uploaded: $syncSuccessCount\n" +
                    "===================================================================="
                )
            }
            cursor.close()
            return@withLock Pair(syncSuccessCount, pendingCount)
        }
    }

    /**
     * Downloads recent transaction records from the server and caches them locally, avoiding duplicates.
     */
    suspend fun fetchFromRemote() = withContext(Dispatchers.IO) {
        Log.i(TAG, "[REMOTE FETCH] Requesting GET /transactions from backend...")
        try {
            val remoteTxs = apiService.getTransactions()
            val db = dbHelper.writableDatabase
            val activeUser = getActiveUserId()
            if (activeUser.isEmpty()) return@withContext

            Log.i(TAG, "[REMOTE FETCH] Received ${remoteTxs.size} transactions from backend.")

            var newCachedCount = 0
            for (tx in remoteTxs) {
                // Check if this remote transaction is already cached by remote_id or client_id for this active user
                val query = if (!tx.clientId.isNullOrEmpty()) {
                    "SELECT 1 FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE (${DatabaseHelper.COL_TX_REMOTE_ID} = ? OR ${DatabaseHelper.COL_TX_CLIENT_ID} = ?) AND ${DatabaseHelper.COL_TX_USER_ID} = ?"
                } else {
                    "SELECT 1 FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_REMOTE_ID} = ? AND ${DatabaseHelper.COL_TX_USER_ID} = ?"
                }
                val args = if (!tx.clientId.isNullOrEmpty()) arrayOf(tx.id, tx.clientId, activeUser) else arrayOf(tx.id, activeUser)
                val cursor = db.rawQuery(query, args)
                val alreadyExists = cursor.count > 0
                cursor.close()

                if (!alreadyExists) {
                    val values = ContentValues().apply {
                        put(DatabaseHelper.COL_TX_USER_ID, activeUser)
                        put(DatabaseHelper.COL_TX_REMOTE_ID, tx.id)
                        if (!tx.clientId.isNullOrEmpty()) {
                            put(DatabaseHelper.COL_TX_CLIENT_ID, tx.clientId)
                        }
                        put(DatabaseHelper.COL_TX_TYPE, tx.type)
                        
                        val cleanedAmount = tx.amount.replace("₹", "").replace("+", "").replace("-", "").replace(",", "").trim()
                        val amtDouble = cleanedAmount.toDoubleOrNull() ?: 0.0
                        put(DatabaseHelper.COL_TX_AMOUNT, amtDouble)

                        put(DatabaseHelper.COL_TX_CATEGORY, tx.category)
                        put(DatabaseHelper.COL_TX_MERCHANT, tx.merchant)
                        put(DatabaseHelper.COL_TX_ACCOUNT, if (tx.maskedAccountRef.isEmpty()) "Main Account" else tx.maskedAccountRef)
                        put(DatabaseHelper.COL_TX_NOTES, tx.description)
                        put(DatabaseHelper.COL_TX_SOURCE, tx.source)
                        put(DatabaseHelper.COL_TX_DATE, (tx.timestamp * 1000).toLong())
                        put(DatabaseHelper.COL_TX_SYNCED, 1)
                    }
                    db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, values)
                    newCachedCount++
                }
            }

            Log.i(TAG, "[REMOTE FETCH SUMMARY] Total remote: ${remoteTxs.size} | Newly inserted to SQLite: $newCachedCount")

            // Sync budgets from backend as well
            val remoteBudgets = apiService.getBudgets()
            Log.i(TAG, "[REMOTE FETCH] Received ${remoteBudgets.size} budgets from backend.")
            for (b in remoteBudgets) {
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_BUDGET_USER_ID, activeUser)
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
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "none"
            Log.e(TAG, "[REMOTE FETCH HTTP ERROR] HTTP ${e.code()}: ${e.message()} | Error Body: $errorBody", e)
        } catch (e: Exception) {
            Log.e(TAG, "[REMOTE FETCH ERROR] Failed to fetch transactions from remote backend", e)
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
