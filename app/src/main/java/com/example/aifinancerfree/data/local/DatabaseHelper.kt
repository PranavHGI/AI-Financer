package com.example.aifinancerfree.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aifinancer_local.db"
        private const val DATABASE_VERSION = 2

        // Table Names
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_BUDGETS = "budgets"
        const val TABLE_PARSED_SMS = "parsed_sms"

        // Transactions Columns
        const val COL_TX_ID = "id"
        const val COL_TX_REMOTE_ID = "remote_id"
        const val COL_TX_TYPE = "type"
        const val COL_TX_AMOUNT = "amount"
        const val COL_TX_CATEGORY = "category"
        const val COL_TX_MERCHANT = "merchant"
        const val COL_TX_ACCOUNT = "account"
        const val COL_TX_NOTES = "notes"
        const val COL_TX_DATE = "date"
        const val COL_TX_SYNCED = "synced"

        // Budgets Columns
        const val COL_BUDGET_CATEGORY = "category"
        const val COL_BUDGET_LIMIT = "limit_amount"

        // Parsed SMS Columns
        const val COL_SMS_FINGERPRINT = "fingerprint"
        const val COL_SMS_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTxTable = """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TX_REMOTE_ID TEXT,
                $COL_TX_TYPE TEXT NOT NULL,
                $COL_TX_AMOUNT REAL NOT NULL,
                $COL_TX_CATEGORY TEXT NOT NULL,
                $COL_TX_MERCHANT TEXT NOT NULL,
                $COL_TX_ACCOUNT TEXT NOT NULL,
                $COL_TX_NOTES TEXT,
                $COL_TX_DATE INTEGER NOT NULL,
                $COL_TX_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createBudgetTable = """
            CREATE TABLE $TABLE_BUDGETS (
                $COL_BUDGET_CATEGORY TEXT PRIMARY KEY,
                $COL_BUDGET_LIMIT REAL NOT NULL
            )
        """.trimIndent()

        val createSmsTable = """
            CREATE TABLE $TABLE_PARSED_SMS (
                $COL_SMS_FINGERPRINT TEXT PRIMARY KEY,
                $COL_SMS_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createTxTable)
        db.execSQL(createBudgetTable)
        db.execSQL(createSmsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSACTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BUDGETS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PARSED_SMS")
        onCreate(db)
    }
}
