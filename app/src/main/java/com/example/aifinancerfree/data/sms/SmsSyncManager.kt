package com.example.aifinancerfree.data.sms

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.example.aifinancerfree.data.local.DatabaseHelper
import java.security.MessageDigest

class SmsSyncManager(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    companion object {
        private const val TAG = "SmsSyncManager"
    }

    /**
     * Scan the system SMS inbox, extracts transactions, registers fingerprints to prevent duplicates,
     * and saves transaction records to the local database.
     * 
     * @return Number of new transactions successfully imported.
     */
    fun syncInbox(): Int {
        var newTxCount = 0
        val contentResolver = context.contentResolver
        val inboxUri = Uri.parse("content://sms/inbox")

        val projection = arrayOf("_id", "address", "body", "date")
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(inboxUri, projection, null, null, "date DESC")
            if (cursor != null && cursor.moveToFirst()) {
                val addressCol = cursor.getColumnIndexOrThrow("address")
                val bodyCol = cursor.getColumnIndexOrThrow("body")
                val dateCol = cursor.getColumnIndexOrThrow("date")

                val localDb = dbHelper.writableDatabase

                do {
                    val address = cursor.getString(addressCol) ?: ""
                    val body = cursor.getString(bodyCol) ?: ""
                    val dateMs = cursor.getLong(dateCol)

                    // Generate a unique fingerprint for duplicate avoidance
                    val fingerprint = generateFingerprint(address, body, dateMs)

                    // Check if fingerprint is already registered in parsed_sms
                    if (isSmsAlreadyParsed(fingerprint)) {
                        continue
                    }

                    // Extract transaction data
                    val extracted = SmsExtractor.extract(body)
                    if (extracted != null) {
                        localDb.beginTransaction()
                        try {
                            // Register SMS fingerprint
                            val smsValues = ContentValues().apply {
                                put(DatabaseHelper.COL_SMS_FINGERPRINT, fingerprint)
                                put(DatabaseHelper.COL_SMS_TIMESTAMP, dateMs)
                            }
                            localDb.insertWithOnConflict(
                                DatabaseHelper.TABLE_PARSED_SMS,
                                null,
                                smsValues,
                                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                            )

                            // Save as local transaction record (synced = 0)
                            val txValues = ContentValues().apply {
                                put(DatabaseHelper.COL_TX_TYPE, extracted.type)
                                put(DatabaseHelper.COL_TX_AMOUNT, extracted.amount)
                                put(DatabaseHelper.COL_TX_CATEGORY, extracted.category)
                                put(DatabaseHelper.COL_TX_MERCHANT, extracted.merchant)
                                put(DatabaseHelper.COL_TX_ACCOUNT, extracted.account)
                                put(DatabaseHelper.COL_TX_NOTES, "Parsed from SMS from $address")
                                put(DatabaseHelper.COL_TX_DATE, dateMs)
                                put(DatabaseHelper.COL_TX_SYNCED, 0)
                            }
                            localDb.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, txValues)

                            localDb.setTransactionSuccessful()
                            newTxCount++
                            Log.d(TAG, "Successfully parsed transaction from SMS. Merchant: ${extracted.merchant}, Amount: ${extracted.amount}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing transaction to local DB", e)
                        } finally {
                            localDb.endTransaction()
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning system SMS inbox", e)
        } finally {
            cursor?.close()
        }

        return newTxCount
    }

    private fun isSmsAlreadyParsed(fingerprint: String): Boolean {
        val db = dbHelper.readableDatabase
        val query = "SELECT 1 FROM ${DatabaseHelper.TABLE_PARSED_SMS} WHERE ${DatabaseHelper.COL_SMS_FINGERPRINT} = ?"
        val cursor = db.rawQuery(query, arrayOf(fingerprint))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun generateFingerprint(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to simple hashcode string if MD5 is unavailable
            input.hashCode().toString()
        }
    }
}
