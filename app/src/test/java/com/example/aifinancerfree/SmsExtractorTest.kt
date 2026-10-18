package com.example.aifinancerfree

import com.example.aifinancerfree.data.sms.SmsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsExtractorTest {

    @Test
    fun testDebitAlertWithOtpWarning_isNotDiscarded() {
        val sms = "Rs 450.00 debited from A/c XX1234 on 18-Oct-26 at Starbucks. Avl Bal: Rs 5000. Never share OTP or password with anyone."
        val result = SmsExtractor.extract(sms)
        assertNotNull("Debit alert with OTP warning should be parsed", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
        assertEquals("Food & dining", result.category)
    }

    @Test
    fun testPureOtpMessage_isDiscarded() {
        val sms = "483920 is your secret OTP for login to AI Financer. Valid for 10 mins. Do not disclose."
        val result = SmsExtractor.extract(sms)
        assertNull("Pure login OTP should be discarded", result)
    }

    @Test
    fun testCreditAlertWithTxnWord_isClassifiedAsIncome() {
        val sms = "Your txn of INR 15,000.00 credited to A/c **4321 on 01-Oct-26 by Salary. Avl Bal: INR 45,000.00"
        val result = SmsExtractor.extract(sms)
        assertNotNull("Credit alert should be parsed", result)
        assertEquals("income", result!!.type)
        assertEquals(15000.0, result.amount, 0.01)
        assertEquals("Salary", result.category)
    }

    @Test
    fun testPromotionalSenderHeader_isDiscarded() {
        val sms = "Rs50 CASHBACK on your recharge of Rs299. Recharge now on Vi App!"
        val result = SmsExtractor.extract(sms, sender = "VZ-611123-P")
        assertNull("Promotional header (-P) should be discarded", result)
    }

    @Test
    fun testCreditLimitMarketing_isDiscarded() {
        val sms = "Congratulations! Your Slice small finance bank credit limit has been increased to Rs 25,000. Apply now."
        val result = SmsExtractor.extract(sms)
        assertNull("Credit limit marketing message should be discarded", result)
    }

    @Test
    fun testBalanceEnquiryOnly_isDiscarded() {
        val sms = "Dear Customer, Avl Bal in A/c ending 1234 is INR 12,450.50 on 18-Oct-26."
        val result = SmsExtractor.extract(sms)
        assertNull("Balance enquiry only message should be discarded", result)
    }

    @Test
    fun testMerchantCleanupWithTrailingUpiAndNoise() {
        val sms = "Rs 400.00 debited from A/c *5678 at PARL MULTI E SERVICE via UPI Ref 382910382 on 18-Oct-26."
        val result = SmsExtractor.extract(sms)
        assertNotNull(result)
        assertEquals(400.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
        assertEquals("PARL MULTI E SERVICE", result.merchant)
    }

    @Test
    fun testSelfTransfer_isClassifiedAsTransfer() {
        val sms = "Rs 5,000.00 transferred to self from A/c *1234 to A/c *5678 on 18-Oct-26."
        val result = SmsExtractor.extract(sms)
        assertNotNull(result)
        assertEquals(5000.0, result!!.amount, 0.01)
        assertEquals("Transfer", result.category)
    }

    @Test
    fun testUberRide_mappedToTravel() {
        val sms = "Paid Rs 249.00 to Uber India via UPI from A/c *8899 on 18-Oct-26."
        val result = SmsExtractor.extract(sms)
        assertNotNull(result)
        assertEquals(249.0, result!!.amount, 0.01)
        assertEquals("Travel", result.category)
    }

    @Test
    fun testElectricityBill_mappedToBills() {
        val sms = "Your payment of Rs 1,850.00 towards BESCOM electricity bill was successful from A/c *4455."
        val result = SmsExtractor.extract(sms)
        assertNotNull(result)
        assertEquals(1850.0, result!!.amount, 0.01)
        assertEquals("Bills", result.category)
    }
}
