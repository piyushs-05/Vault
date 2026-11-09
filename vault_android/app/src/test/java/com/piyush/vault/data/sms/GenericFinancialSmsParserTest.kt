package com.piyush.vault.data.sms

import com.piyush.vault.data.directory.BundledInstitutionDirectory
import com.piyush.vault.data.sms.parsing.GenericFinancialSmsParser
import com.piyush.vault.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class GenericFinancialSmsParserTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"),ZoneOffset.UTC)
    private val parser = GenericFinancialSmsParser(BundledInstitutionDirectory(),clock)
    private fun parse(text: String) = parser.parse(IncomingSms("VM-HDFCBK",text,clock.millis(),clock.millis()))
    @Test fun amountExcludesAvailableBalanceAndKeepsOnlyAccountSuffix() {
        val p = requireNotNull(parse("A/c XX1234 debited INR 250.50 on 02/11/2026. Available balance INR 9,000.00. Ref 123456789012"))
        assertEquals(25050L,p.amountMinor); assertEquals("1234",p.lastFour)
        assertEquals("HDFC Bank",p.institution); assertEquals(EventKind.UNKNOWN,p.kind)
        assertEquals(LocalDate.of(2026,11,2),p.date); assertFalse(p.description.contains("123456789012"))
    }
    @Test fun otpFailureOnlyAndPersonalMessagesAreIgnoredButMixedStatusNeedsReview() {
        listOf("OTP 123456: INR 500 will be debited", "INR 500 payment failed, not debited").forEach { assertNull(parse(it)) }
        val pending = requireNotNull(parse("INR 500 debited pending processing"))
        assertNull(pending.direction); assertNull(pending.amountMinor)
        assertNull(parser.parse(IncomingSms("+919999999999","I paid INR 500 today",clock.millis(),clock.millis())))
    }
    @Test fun ambiguousAmountsRequireCorrectionAndNoDateInventedFromNow() {
        val p = requireNotNull(parse("A/c XX1234 debited INR 250 and INR 10 fee"))
        assertNull(p.amountMinor); assertEquals("SMS_DATE_UNCERTAIN",p.dateBasis)
        assertEquals(LocalDate.of(2026,11,2),p.date)
    }
    @Test fun cashAndRefundNeedReviewRatherThanInventingPurchaseIncome() {
        assertEquals(EventKind.CASH_WITHDRAWAL,parse("A/c XX1234 withdrawn INR 500 at ATM")?.kind)
        assertEquals(EventKind.REFUND,parse("A/c XX1234 refunded INR 500")?.kind)
        assertEquals(TransactionDirection.INCOME,parse("A/c XX1234 refunded INR 500")?.direction)
    }
    @Test fun invalidDatesFallBackToMessageTimestampAndMissingSuffixRemainsUnknown() {
        val p = requireNotNull(parse("INR 100 debited on 31/02/2026"))
        assertNull(p.lastFour); assertEquals("SMS_DATE_UNCERTAIN",p.dateBasis)
    }
}
