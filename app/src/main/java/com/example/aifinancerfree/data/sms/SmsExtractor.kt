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

    // Regex patterns for Merchant/Recipient with word boundaries
    private val merchantPatterns = listOf(
        Pattern.compile("(?i)(?:\\bat|\\btowards|\\bpaid to|\\bsent to|\\btransfer to|\\binfo\\*|\\bvpa)\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on\\b|\\s+Ref\\b|\\s+dt\\b|\\s+avail\\b|\\s+balance\\b|\\.|$)"),
        Pattern.compile("(?i)(?:\\bto|\\bfor)\\s+([A-Za-z0-9\\s\\-*]+?)(?=\\s+on\\b|\\s+Ref\\b|\\s+dt\\b|\\s+avail\\b|\\s+balance\\b|\\.|$)"),
        Pattern.compile("(?i)\\bat\\s+([A-Za-z0-9\\s\\-*]+)")
    )

    // Regex patterns for Card/Account ending digits
    private val accountPatterns = listOf(
        Pattern.compile("(?i)(?:a/c|acct|account|card|ending|xx|vpa)\\s*(?:ending|no\\.?)?\\s*\\**([0-9]{3,4})"),
        Pattern.compile("(?i)(?:ending with|ending in)\\s*([0-9]{3,4})")
    )

    fun extract(smsBody: String, sender: String = ""): ExtractedTx? {
        val lowercaseBody = smsBody.lowercase()
        val preview = if (smsBody.length > 50) smsBody.take(50) + "..." else smsBody

        // 0. Filter out promotional sender headers (e.g. -P or _P)
        val trimmedSender = sender.trim().uppercase()
        if (trimmedSender.endsWith("-P") || trimmedSender.endsWith("_P")) {
            Log.d(TAG, "[SKIP: PROMO SENDER] Dropping promotional header sender '$sender': '$preview'")
            return null
        }

        // 1. Filter out promotional / limit / marketing messages
        val isPromoKeyword = lowercaseBody.contains("credit limit") || 
                             lowercaseBody.contains("limit increased") ||
                             lowercaseBody.contains("available limit") ||
                             lowercaseBody.contains("card limit") ||
                             lowercaseBody.contains("pre-approved") ||
                             lowercaseBody.contains("apply now") ||
                             lowercaseBody.contains("exclusive offer") ||
                             lowercaseBody.contains("congratulations you are eligible")
        if (isPromoKeyword) {
            Log.d(TAG, "[SKIP: PROMO KEYWORD] Discarding marketing / limit update message: '$preview'")
            return null
        }

        // 2. Filter OTP messages (only if pure security OTP without transaction alerts)
        val hasOtpKeyword = lowercaseBody.contains("otp") || 
                            lowercaseBody.contains("verification code") || 
                            lowercaseBody.contains("one time password")
        val isSecurityWarning = lowercaseBody.contains("never share") || 
                                lowercaseBody.contains("do not share") || 
                                lowercaseBody.contains("don't share") ||
                                lowercaseBody.contains("not you?") ||
                                lowercaseBody.contains("never disclose")

        if (hasOtpKeyword && !isSecurityWarning) {
            val isPureOtp = lowercaseBody.contains("is your otp") ||
                            lowercaseBody.contains("otp is") ||
                            lowercaseBody.contains("use otp") ||
                            lowercaseBody.contains("code to verify") ||
                            lowercaseBody.contains("for login")
            val hasTxVerb = lowercaseBody.contains("debited") || lowercaseBody.contains("credited") || lowercaseBody.contains("spent")
            if (isPureOtp || !hasTxVerb) {
                Log.d(TAG, "[SKIP: OTP] Discarding pure OTP message: '$preview'")
                return null
            }
        }

        // 3. Classify debit, credit, or self-transfer
        val isSelfTransfer = lowercaseBody.contains("self transfer") ||
                             lowercaseBody.contains("transfer to self") ||
                             lowercaseBody.contains("transferred to self") ||
                             (lowercaseBody.contains("transferred from") && lowercaseBody.contains("to your"))

        val hasDebit = lowercaseBody.contains("debited") || lowercaseBody.contains("spent") || 
                       lowercaseBody.contains("charged") || lowercaseBody.contains("purchase") || 
                       lowercaseBody.contains("paid") || lowercaseBody.contains("sent") ||
                       lowercaseBody.contains("withdrawn")

        val hasCredit = lowercaseBody.contains("credited") || lowercaseBody.contains("deposited") || 
                        lowercaseBody.contains("received") || lowercaseBody.contains("refund") || 
                        lowercaseBody.contains("cashback") || lowercaseBody.contains("salary")

        // Pure balance enquiry with no debit/credit action
        if (!hasDebit && !hasCredit && !isSelfTransfer) {
            Log.d(TAG, "[SKIP: NON-FINANCIAL] Message does not contain financial action verbs: '$preview'")
            return null
        }

        val txType = when {
            isSelfTransfer -> "expense"
            hasDebit && hasCredit -> {
                // "debited ... credited to [merchant]" -> debit expense for user
                if (lowercaseBody.indexOf("debited") < lowercaseBody.indexOf("credited")) {
                    "expense"
                } else {
                    "income"
                }
            }
            hasCredit -> "income"
            else -> "expense"
        }

        // 4. Extract Amount
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

        // 5. Extract Merchant
        var merchant = if (txType == "income") "Deposit / Transfer" else "Unknown Merchant"
        for (pattern in merchantPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                var match = matcher.group(1)?.trim()
                if (!match.isNullOrEmpty()) {
                    // Clean trailing noise like "via UPI", "Ref ...", "on DD-MM"
                    match = match.replace(Regex("(?i)\\s+via\\s+upi.*"), "")
                    match = match.replace(Regex("(?i)\\s+upi/\\S+"), "")
                    match = match.replace(Regex("(?i)\\s+ref[:\\s].*"), "")
                    match = match.replace(Regex("[\\.\\-\\/,]+$"), "").trim()
                    match = match.replace(Regex("(?i)\\s+via$"), "").trim()
                    match = match.replace(Regex("(?i)\\s+on$"), "").trim()

                    // Ensure not an account reference falsely captured
                    if (!match.matches(Regex("(?i)^your\\s+(?:a/c|account).*")) &&
                        !match.matches(Regex("(?i)^(?:a/c|account|acct).*")) &&
                        match.length >= 2
                    ) {
                        merchant = match.take(40).trim()
                        break
                    }
                }
            }
        }

        // 6. Extract Account reference
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

        // 7. Map Category (aligned with backend categories)
        val category = when {
            isSelfTransfer -> "Transfer"
            txType == "income" -> {
                when {
                    lowercaseBody.contains("salary") -> "Salary"
                    lowercaseBody.contains("freelance") || lowercaseBody.contains("upwork") || lowercaseBody.contains("fiverr") -> "Freelance"
                    lowercaseBody.contains("transfer") -> "Transfer"
                    else -> "Other"
                }
            }
            else -> mapCategory(merchant, lowercaseBody)
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

    private fun mapCategory(merchant: String, body: String = ""): String {
        val text = "${merchant.lowercase()} ${body.lowercase()}"
        return when {
            text.contains("starbucks") || text.contains("mcdonald") || text.contains("zomato") || 
            text.contains("swiggy") || text.contains("food") || text.contains("restaurant") || 
            text.contains("cafe") || text.contains("dine") || text.contains("pizza") || text.contains("burger") ||
            text.contains("kfc") || text.contains("dominos") || text.contains("subway") -> "Food & dining"

            text.contains("uber") || text.contains("ola") || text.contains("taxi") || text.contains("cab") ||
            text.contains("metro") || text.contains("irctc") || text.contains("railway") || 
            text.contains("flight") || text.contains("airline") || text.contains("travel") ||
            text.contains("indigo") || text.contains("fuel") || text.contains("petrol") -> "Travel"

            text.contains("netflix") || text.contains("prime") || text.contains("spotify") || 
            text.contains("hotstar") || text.contains("cinema") || text.contains("movie") || 
            text.contains("pvr") || text.contains("inox") || text.contains("game") || text.contains("steam") ||
            text.contains("google play") -> "Entertainment"

            text.contains("amazon") || text.contains("flipkart") || text.contains("myntra") || text.contains("ajio") ||
            text.contains("zara") || text.contains("walmart") || text.contains("shopping") || 
            text.contains("mall") || text.contains("store") || text.contains("grocery") || text.contains("mart") ||
            text.contains("blinkit") || text.contains("zepto") || text.contains("instamart") -> "Shopping"

            text.contains("electricity") || text.contains("water") || text.contains("gas") || 
            text.contains("airtel") || text.contains("jio") || text.contains("postpaid") || 
            text.contains("broadband") || text.contains("bill") || text.contains("recharge") ||
            text.contains("bescom") || text.contains("tneb") || text.contains("wifi") -> "Bills"

            text.contains("hospital") || text.contains("medical") || text.contains("pharmacy") || 
            text.contains("doctor") || text.contains("clinic") || text.contains("health") ||
            text.contains("apollo") || text.contains("1mg") || text.contains("pharmeasy") -> "Health"

            text.contains("school") || text.contains("college") || text.contains("university") ||
            text.contains("tuition") || text.contains("udemy") || text.contains("coursera") ||
            text.contains("fees") -> "Education"

            else -> "Other"
        }
    }
}
