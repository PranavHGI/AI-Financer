package com.example.aifinancerfree.data.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.network.ApiClient
import com.example.aifinancerfree.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "SmsReceiver"

        fun processIncomingSms(context: Context, intent: Intent, receiverName: String) {
            val action = intent.action
            Log.i(TAG, "==================== [INCOMING SMS BROADCAST] ====================")
            Log.i(TAG, "[$receiverName] Action: '$action'")

            val consent = ConsentPreferences(context)
            if (!consent.isSmsTrackingEnabled()) {
                Log.w(TAG, "[$receiverName] SMS tracking consent is DISABLED in preferences. Discarding incoming SMS.")
                return
            }

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "[$receiverName] No SMS messages found in intent extras.")
                return
            }

            Log.i(TAG, "[$receiverName] Received ${messages.size} SMS part(s). Processing...")

            val dbHelper = DatabaseHelper(context)
            val db = dbHelper.writableDatabase

            for (sms in messages) {
                val body = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: "Unknown"
                val timestamp = sms.timestampMillis

                Log.d(TAG, "[$receiverName] Inspecting SMS from '$sender' (Timestamp: $timestamp)")

                val extracted = SmsExtractor.extract(body)
                if (extracted == null) {
                    Log.d(TAG, "[$receiverName] Message was not recognized as a financial transaction.")
                    continue
                }

                // Generate unique fingerprint
                val input = "$sender|$body|$timestamp"
                val fingerprint = try {
                    val md = MessageDigest.getInstance("MD5")
                    val digest = md.digest(input.toByteArray())
                    digest.joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    input.hashCode().toString()
                }

                // Check duplicate
                val cursor = db.rawQuery(
                    "SELECT 1 FROM ${DatabaseHelper.TABLE_PARSED_SMS} WHERE ${DatabaseHelper.COL_SMS_FINGERPRINT} = ?",
                    arrayOf(fingerprint)
                )
                val exists = cursor.count > 0
                cursor.close()

                if (exists) {
                    Log.i(TAG, "[$receiverName] SMS fingerprint already recorded. Skipping duplicate.")
                    continue
                }

                db.beginTransaction()
                try {
                    // Record fingerprint
                    val smsVal = ContentValues().apply {
                        put(DatabaseHelper.COL_SMS_FINGERPRINT, fingerprint)
                        put(DatabaseHelper.COL_SMS_TIMESTAMP, timestamp)
                    }
                    db.insertWithOnConflict(
                        DatabaseHelper.TABLE_PARSED_SMS,
                        null,
                        smsVal,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                    )

                    // Insert transaction record
                    val txVal = ContentValues().apply {
                        put(DatabaseHelper.COL_TX_TYPE, extracted.type)
                        put(DatabaseHelper.COL_TX_AMOUNT, extracted.amount)
                        put(DatabaseHelper.COL_TX_CATEGORY, extracted.category)
                        put(DatabaseHelper.COL_TX_MERCHANT, extracted.merchant)
                        put(DatabaseHelper.COL_TX_ACCOUNT, extracted.account)
                        put(DatabaseHelper.COL_TX_NOTES, "Parsed from live SMS from $sender")
                        put(DatabaseHelper.COL_TX_DATE, timestamp)
                        put(DatabaseHelper.COL_TX_SYNCED, 0)
                    }
                    val rowId = db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, txVal)
                    db.setTransactionSuccessful()

                    Log.i(
                        TAG,
                        "[$receiverName] Successfully stored live transaction! Local ID: $rowId | " +
                        "Merchant: '${extracted.merchant}' | Amount: ₹${extracted.amount} | Type: ${extracted.type}"
                    )

                    // Trigger immediate background sync to remote backend
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.i(TAG, "[$receiverName] Triggering instant sync to backend server...")
                            val tokenMgr = TokenManager(context)
                            val apiClient = ApiClient(tokenMgr)
                            val repository = TransactionRepository(apiClient.apiService, dbHelper)
                            repository.syncPendingTransactions()
                        } catch (e: Exception) {
                            Log.e(TAG, "[$receiverName] Instant background sync failed", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[$receiverName] Error inserting live SMS transaction into SQLite", e)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        processIncomingSms(context, intent, "SmsReceiver (Default SMS Role)")
    }
}

class StandardSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SmsReceiver.processIncomingSms(context, intent, "StandardSmsReceiver (Non-Default SMS)")
    }
}
