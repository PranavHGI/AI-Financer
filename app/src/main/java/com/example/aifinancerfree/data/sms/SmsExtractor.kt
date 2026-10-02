package com.example.aifinancerfree.data.sms

import java.util.regex.Pattern

data class ExtractedTx(
    val amount: Double,
    val merchant: String,
    val category: String,
    val account: String,
    val type: String = "expense"
)

object SmsExtractor {

    // Regex patterns for extracting Amount
    private val amountPatterns = listOf(
        Pattern.compile("(?i)(?:rs\\.?|inr)\\s*([0-9,]+\\.[0-9]{2})"),
        Pattern.compile("(?i)(?:spent|debited|charging|charged|purchase of)\\s*(?:rs\\.?|inr)?\\s*([0-9,]+\\.[0-9]{2})"),
        Pattern.compile("(?i)(?:debited by|spent of)\\s*([0-9,]+\\.[0-9]{2})")
    )

    // Regex patterns for Merchant/Recipient
    private val merchantPatterns = listOf(
        Pattern.compile("(?i)at\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\s+dt|\\.|$)"),
        Pattern.compile("(?i)towards\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\s+dt|\\.|$)"),
        Pattern.compile("(?i)to\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\s+dt|\\.|$)"),
        Pattern.compile("(?i)transfer to\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on|\\s+Ref|\\.|$)")
    )

    // Regex patterns for Card/Account ending digits
    private val accountPatterns = listOf(
        Pattern.compile("(?i)(?:a/c|acct|account|card|ending|xx)\\s*(?:ending)?\\s*\\**([0-9]{3,4})"),
        Pattern.compile("(?i)(?:ending with|ending in)\\s*([0-9]{3,4})")
    )

    fun extract(smsBody: String): ExtractedTx? {
        val lowercaseBody = smsBody.lowercase()

        // We only care about debit/spent transaction messages, ignoring credit messages or OTPs
        if (lowercaseBody.contains("otp") || lowercaseBody.contains("verification code")) {
            return null
        }
        val isDebit = lowercaseBody.contains("debited") || lowercaseBody.contains("spent") || 
                      lowercaseBody.contains("charged") || lowercaseBody.contains("purchase") || 
                      lowercaseBody.contains("txn")

        if (!isDebit) {
            return null
        }

        // 1. Extract Amount
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val rawAmountStr = matcher.group(1)?.replace(",", "")
                amount = rawAmountStr?.toDoubleOrNull()
                if (amount != null) break
            }
        }
        if (amount == null) return null // If amount could not be extracted, discard the message

        // 2. Extract Merchant
        var merchant = "Unknown Merchant"
        for (pattern in merchantPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val match = matcher.group(1)?.trim()
                if (!match.isNullOrEmpty() && match.length > 2) {
                    merchant = match
                    break
                }
            }
        }

        // 3. Extract Account
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

        // 4. Map Category
        val category = mapCategory(merchant)

        return ExtractedTx(
            amount = amount,
            merchant = merchant,
            category = category,
            account = account,
            type = "expense"
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
