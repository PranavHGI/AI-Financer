package com.example.aifinancerfree.data.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.aifinancerfree.AIFinancerApplication
import com.example.aifinancerfree.data.local.DatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StandardSmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "StandardSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIncomingBroadcast(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "[SMS RECEIVE ERROR] Error processing incoming SMS broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processIncomingBroadcast(context: Context, intent: Intent) {
        val app = context.applicationContext as? AIFinancerApplication ?: AIFinancerApplication.instance
        val accessManager = app.smsAccessManager

        Log.i(TAG, "==================== [INCOMING SMS BROADCAST RECEIVED] ====================")

        if (!accessManager.isAccessEnabled()) {
            Log.w(TAG, "[INCOMING SMS DROPPED] SMS ingestion is DISABLED (Consent: ${accessManager.isConsentGranted()}, Perms: ${accessManager.hasAllRequiredPermissions()})")
            return
        }

        val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "[INCOMING SMS] No SMS message PDUs found in intent.")
            return
        }

        // Group multipart SMS parts by originating address so multipart messages are concatenated
        val groupedBySender = messages.groupBy { it.originatingAddress ?: "Unknown" }

        val dbHelper = app.databaseHelper
        val db = dbHelper.writableDatabase
        val repository = app.transactionRepository

        for ((sender, parts) in groupedBySender) {
            val fullBody = parts.joinToString(separator = "") { it.messageBody ?: "" }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.i(TAG, "[INCOMING SMS] Processing message from '$sender' (${parts.size} part(s), timestamp=$timestamp)")

            val bodyHash = SmsSyncManager.computeBodyHash(sender, fullBody)

            // Check duplicate in parsed_sms within 120s
            val cur = db.rawQuery(
                "SELECT ${DatabaseHelper.COL_SMS_TIMESTAMP} FROM ${DatabaseHelper.TABLE_PARSED_SMS} WHERE ${DatabaseHelper.COL_SMS_BODY_HASH} = ? OR ${DatabaseHelper.COL_SMS_FINGERPRINT} LIKE ?",
                arrayOf(bodyHash, "${bodyHash}_%")
            )
            var isDuplicate = false
            if (cur.moveToFirst()) {
                val tsCol = cur.getColumnIndex(DatabaseHelper.COL_SMS_TIMESTAMP)
                do {
                    val existingTs = if (tsCol >= 0) cur.getLong(tsCol) else 0L
                    if (Math.abs(existingTs - timestamp) <= 120_000L || existingTs == timestamp) {
                        isDuplicate = true
                        break
                    }
                } while (cur.moveToNext())
            }
            cur.close()

            if (isDuplicate) {
                Log.i(TAG, "[INCOMING SMS DUPLICATE] Hash '$bodyHash' already parsed within duplicate window. Dropping.")
                continue
            }

            val extracted = SmsExtractor.extract(fullBody, sender = sender)
            if (extracted == null) {
                Log.d(TAG, "[INCOMING SMS NON-FINANCIAL] Message from '$sender' was not a financial transaction.")
                continue
            }

            val clientId = java.util.UUID.randomUUID().toString()

            db.beginTransaction()
            try {
                // Record fingerprint
                val smsVal = ContentValues().apply {
                    put(DatabaseHelper.COL_SMS_FINGERPRINT, "${bodyHash}_${timestamp}")
                    put(DatabaseHelper.COL_SMS_BODY_HASH, bodyHash)
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
                    put(DatabaseHelper.COL_TX_CLIENT_ID, clientId)
                    put(DatabaseHelper.COL_TX_TYPE, extracted.type)
                    put(DatabaseHelper.COL_TX_AMOUNT, extracted.amount)
                    put(DatabaseHelper.COL_TX_CATEGORY, extracted.category)
                    put(DatabaseHelper.COL_TX_MERCHANT, extracted.merchant)
                    put(DatabaseHelper.COL_TX_ACCOUNT, extracted.account)
                    put(DatabaseHelper.COL_TX_NOTES, "Parsed from live SMS from $sender")
                    put(DatabaseHelper.COL_TX_SOURCE, "sms")
                    put(DatabaseHelper.COL_TX_DATE, timestamp)
                    put(DatabaseHelper.COL_TX_SYNCED, 0)
                }
                val rowId = db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, txVal)
                db.setTransactionSuccessful()

                Log.i(
                    TAG,
                    "[INCOMING SMS STORED] Local DB Row: $rowId | clientId: $clientId | Merchant: '${extracted.merchant}' | Amount: ₹${extracted.amount}"
                )
            } finally {
                db.endTransaction()
            }

            // Immediately trigger resilient background upload to backend
            try {
                repository.syncPendingTransactions()
            } catch (e: Exception) {
                Log.e(TAG, "[INCOMING SMS SYNC ERROR] Failed immediate upload to backend", e)
            }
        }
    }
}

// Backward-compatible alias for any residual references
typealias SmsReceiver = StandardSmsReceiver
