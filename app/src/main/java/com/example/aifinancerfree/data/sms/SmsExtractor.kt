package com.example.aifinancerfree.data.sms

import android.util.Log
import java.util.regex.Pattern

data class ExtractedTx(
    val amount: Double,
    val merchant: String,
    val category: String,
    val account: String,
    val type: String = "expense"
)

object SmsExtractor {
    private const val TAG = "SmsExtractor"

    // Regex patterns for extracting Amount (supports ₹, Rs., Rs, INR with or without decimals)
    private val amountPatterns = listOf(
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("(?i)(?:spent|debited|charging|charged|paid|sent|transferred|credited|received|purchase of)\\s*(?:for|by|of)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("(?i)(?:debited by|spent of|paid of|credited with)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("(?i)(?:inr|rs\\.?|₹)\\s*([0-9]+)")
    )

    // Regex patterns for Merchant/Recipient
    private val merchantPatterns = listOf(
        Pattern.compile("(?i)(?:at|towards|to|vpa|info\\*|for)\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\s+dt|\\s+avail|\\s+balance|\\.|$)"),
        Pattern.compile("(?i)(?:transfer to|paid to|sent to)\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\.|$)"),
        Pattern.compile("(?i)at\\s+([A-Za-z0-9\\s\\-*]+)")
    )

    // Regex patterns for Card/Account ending digits
    private val accountPatterns = listOf(
        Pattern.compile("(?i)(?:a/c|acct|account|card|ending|xx|vpa)\\s*(?:ending|no\\.?)?\\s*\\**([0-9]{3,4})"),
        Pattern.compile("(?i)(?:ending with|ending in)\\s*([0-9]{3,4})")
    )

    fun extract(smsBody: String): ExtractedTx? {
        val lowercaseBody = smsBody.lowercase()
        val preview = if (smsBody.length > 50) smsBody.take(50) + "..." else smsBody

        // 1. Skip non-transactional messages like OTPs
        if (lowercaseBody.contains("otp") || lowercaseBody.contains("verification code") || lowercaseBody.contains("one time password")) {
            Log.d(TAG, "[SKIP: OTP] Discarding security OTP message: '$preview'")
            return null
        }

        // 2. Classify debit (expense) or credit (income)
        val isDebit = lowercaseBody.contains("debited") || lowercaseBody.contains("spent") || 
                      lowercaseBody.contains("charged") || lowercaseBody.contains("purchase") || 
                      lowercaseBody.contains("paid") || lowercaseBody.contains("sent") ||
                      lowercaseBody.contains("transferred") || lowercaseBody.contains("txn") ||
                      lowercaseBody.contains("withdrawn")

        val isCredit = lowercaseBody.contains("credited") || lowercaseBody.contains("deposited") || 
                       lowercaseBody.contains("received") || lowercaseBody.contains("refund") || 
                       lowercaseBody.contains("cashback") || lowercaseBody.contains("salary")

        if (!isDebit && !isCredit) {
            Log.d(TAG, "[SKIP: NON-FINANCIAL] Message does not contain financial debit/credit indicators: '$preview'")
            return null
        }

        val txType = if (isCredit && !isDebit) "income" else "expense"

        // 3. Extract Amount
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val rawAmountStr = matcher.group(1)?.replace(",", "")
                amount = rawAmountStr?.toDoubleOrNull()
                if (amount != null && amount > 0.0) break
            }
        }
        if (amount == null) {
            Log.w(TAG, "[SKIP: NO AMOUNT] Financial keywords found, but amount could not be parsed: '$preview'")
            return null
        }

        // 4. Extract Merchant
        var merchant = if (txType == "income") "Deposit / Transfer" else "Unknown Merchant"
        for (pattern in merchantPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val match = matcher.group(1)?.trim()
                if (!match.isNullOrEmpty() && match.length > 2) {
                    merchant = match.take(30).trim()
                    break
                }
            }
        }

        // 5. Extract Account reference
        var account = "Main Account"
        for (pattern in accountPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val acctNum = matcher.group(1)
                if (!acctNum.isNullOrEmpty()) {
                    account = "A/c *${acctNum}"
                    break
                }
            }
        }

        // 6. Map Category
        val category = if (txType == "income") {
            if (lowercaseBody.contains("salary")) "Salary" else "Freelance"
        } else {
            mapCategory(merchant)
        }

        Log.i(
            TAG,
            "[EXTRACT SUCCESS] Parsed transaction -> Type: $txType | Amount: ₹$amount | Merchant: '$merchant' | Category: '$category' | Account: '$account'"
        )

        return ExtractedTx(
            amount = amount,
            merchant = merchant,
            category = category,
            account = account,
            type = txType
        )
    }

    private fun mapCategory(merchant: String): String {
        val name = merchant.lowercase()
        return when {
            name.contains("starbucks") || name.contains("mcdonald") || name.contains("zomato") || 
            name.contains("swiggy") || name.contains("food") || name.contains("restaurant") || 
            name.contains("cafe") || name.contains("dine") || name.contains("pizza") || name.contains("burger") -> "Food"

            name.contains("uber") || name.contains("ola") || name.contains("taxi") || 
            name.contains("metro") || name.contains("irctc") || name.contains("railway") || 
            name.contains("flight") || name.contains("airline") || name.contains("travel") -> "Travel"

            name.contains("netflix") || name.contains("prime") || name.contains("spotify") || 
            name.contains("hotstar") || name.contains("cinema") || name.contains("movie") || 
            name.contains("game") || name.contains("steam") || name.contains("google play") -> "Entertainment"

            name.contains("amazon") || name.contains("flipkart") || name.contains("myntra") || 
            name.contains("zara") || name.contains("walmart") || name.contains("shopping") || 
            name.contains("mall") || name.contains("store") || name.contains("grocery") || name.contains("mart") -> "Shopping"

            name.contains("electricity") || name.contains("water") || name.contains("gas") || 
            name.contains("airtel") || name.contains("jio") || name.contains("postpaid") || 
            name.contains("broadband") || name.contains("bill") || name.contains("recharge") -> "Utilities"

            name.contains("hospital") || name.contains("medical") || name.contains("pharmacy") || 
            name.contains("doctor") || name.contains("clinic") || name.contains("health") -> "Health"

            else -> "Others"
        }
    }
}
