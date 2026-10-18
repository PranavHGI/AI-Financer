package com.example.aifinancerfree.data.sms

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.example.aifinancerfree.data.local.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ScanResult(
    val examined: Int,
    val alreadyImported: Int,
    val nonFinancial: Int,
    val imported: Int,
    var uploaded: Int = 0,
    var failedUpload: Int = 0
)

class SmsSyncManager(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    companion object {
        private const val TAG = "SmsSyncManager"

        fun computeBodyHash(sender: String, body: String): String {
            val normSender = sender.trim().uppercase()
            val trimmedBody = body.trim()
            val input = "$normSender|$trimmedBody"
            return try {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(input.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                input.hashCode().toString()
            }
        }
    }

    /**
     * Scan the system SMS inbox on Dispatchers.IO, extracts transactions, registers fingerprints to prevent duplicates,
     * and saves transaction records to the local database in a single SQLite transaction.
     * 
     * @return ScanResult with detailed breakdown of examined, skipped, and new transactions.
     */
    suspend fun syncInbox(): ScanResult = withContext(Dispatchers.IO) {
        var newTxCount = 0
        var totalExamined = 0
        var alreadyParsedCount = 0
        var nonFinancialCount = 0

        val contentResolver = context.contentResolver
        val inboxUri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        var cursor: Cursor? = null

        Log.i(TAG, "==================== [SMS INBOX SCAN START] ====================")

        // 1. Preload all existing fingerprints into memory for O(1) duplicate checks
        val existingFingerprints = mutableMapOf<String, MutableList<Long>>()
        val readableDb = dbHelper.readableDatabase
        val cur = readableDb.rawQuery(
            "SELECT ${DatabaseHelper.COL_SMS_FINGERPRINT}, ${DatabaseHelper.COL_SMS_BODY_HASH}, ${DatabaseHelper.COL_SMS_TIMESTAMP} FROM ${DatabaseHelper.TABLE_PARSED_SMS}",
            null
        )
        if (cur.moveToFirst()) {
            val fpCol = cur.getColumnIndex(DatabaseHelper.COL_SMS_FINGERPRINT)
            val hashCol = cur.getColumnIndex(DatabaseHelper.COL_SMS_BODY_HASH)
            val tsCol = cur.getColumnIndex(DatabaseHelper.COL_SMS_TIMESTAMP)
            do {
                val hash = if (hashCol >= 0 && !cur.isNull(hashCol)) {
                    cur.getString(hashCol)
                } else if (fpCol >= 0 && !cur.isNull(fpCol)) {
                    cur.getString(fpCol)
                } else ""
                val ts = if (tsCol >= 0) cur.getLong(tsCol) else 0L
                if (hash.isNotEmpty()) {
                    existingFingerprints.getOrPut(hash) { mutableListOf() }.add(ts)
                }
            } while (cur.moveToNext())
        }
        cur.close()
        Log.i(TAG, "[INBOX SCAN] Loaded ${existingFingerprints.size} existing SMS fingerprints into memory cache.")

        try {
            cursor = contentResolver.query(inboxUri, projection, null, null, "date DESC")
            if (cursor == null) {
                Log.e(TAG, "[INBOX SCAN FAILED] ContentResolver returned NULL cursor. Check READ_SMS permission.")
                return@withContext ScanResult(0, 0, 0, 0)
            }

            val totalInBox = cursor.count
            Log.i(TAG, "[INBOX SCAN] Found $totalInBox total messages in SMS inbox.")

            if (cursor.moveToFirst()) {
                val addressCol = cursor.getColumnIndexOrThrow("address")
                val bodyCol = cursor.getColumnIndexOrThrow("body")
                val dateCol = cursor.getColumnIndexOrThrow("date")

                val localDb = dbHelper.writableDatabase
                localDb.beginTransaction()
                try {
                    do {
                        totalExamined++
                        val address = cursor.getString(addressCol) ?: ""
                        val body = cursor.getString(bodyCol) ?: ""
                        val dateMs = cursor.getLong(dateCol)

                        // Unified SHA-256 body hash
                        val bodyHash = computeBodyHash(address, body)

                        // Deduplication check: same sender+body within 120 seconds or exact timestamp
                        val timestamps = existingFingerprints[bodyHash]
                        if (timestamps != null && (timestamps.any { Math.abs(it - dateMs) <= 120_000L } || timestamps.contains(dateMs))) {
                            alreadyParsedCount++
                            continue
                        }

                        // Extract transaction data
                        val extracted = SmsExtractor.extract(body, sender = address)
                        if (extracted != null) {
                            val clientId = java.util.UUID.randomUUID().toString()

                            // Register SMS fingerprint
                            val smsValues = ContentValues().apply {
                                put(DatabaseHelper.COL_SMS_FINGERPRINT, "${bodyHash}_${dateMs}")
                                put(DatabaseHelper.COL_SMS_BODY_HASH, bodyHash)
                                put(DatabaseHelper.COL_SMS_TIMESTAMP, dateMs)
                            }
                            localDb.insertWithOnConflict(
                                DatabaseHelper.TABLE_PARSED_SMS,
                                null,
                                smsValues,
                                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                            )

                            // Save as local transaction record (synced = 0) with client_id and source
                            val txValues = ContentValues().apply {
                                put(DatabaseHelper.COL_TX_CLIENT_ID, clientId)
                                put(DatabaseHelper.COL_TX_TYPE, extracted.type)
                                put(DatabaseHelper.COL_TX_AMOUNT, extracted.amount)
                                put(DatabaseHelper.COL_TX_CATEGORY, extracted.category)
                                put(DatabaseHelper.COL_TX_MERCHANT, extracted.merchant)
                                put(DatabaseHelper.COL_TX_ACCOUNT, extracted.account)
                                put(DatabaseHelper.COL_TX_NOTES, "Parsed from SMS from $address")
                                put(DatabaseHelper.COL_TX_SOURCE, "sms")
                                put(DatabaseHelper.COL_TX_DATE, dateMs)
                                put(DatabaseHelper.COL_TX_SYNCED, 0)
                            }
                            localDb.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, txValues)

                            existingFingerprints.getOrPut(bodyHash) { mutableListOf() }.add(dateMs)
                            newTxCount++
                        } else {
                            nonFinancialCount++
                        }
                    } while (cursor.moveToNext())

                    localDb.setTransactionSuccessful()
                } finally {
                    localDb.endTransaction()
                }
            } else {
                Log.i(TAG, "[INBOX SCAN] SMS inbox is empty (cursor.moveToFirst() == false).")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[SECURITY EXCEPTION] READ_SMS permission is not granted! Cannot access content://sms/inbox", e)
        } catch (e: Exception) {
            Log.e(TAG, "[INBOX SCAN ERROR] Unexpected error scanning system SMS inbox", e)
        } finally {
            cursor?.close()
        }

        Log.i(
            TAG,
            "==================== [SMS INBOX SCAN SUMMARY] ====================\n" +
            "Messages Examined: $totalExamined\n" +
            "Previously Parsed (Skipped): $alreadyParsedCount\n" +
            "Non-Financial / Skipped: $nonFinancialCount\n" +
            "New Transactions Added: $newTxCount\n" +
            "=================================================================="
        )

        return@withContext ScanResult(
            examined = totalExamined,
            alreadyImported = alreadyParsedCount,
            nonFinancial = nonFinancialCount,
            imported = newTxCount
        )
    }
}
