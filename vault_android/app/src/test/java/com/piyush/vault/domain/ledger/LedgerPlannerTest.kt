package com.piyush.vault.domain.ledger

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LedgerPlannerTest {
    private fun account(id: String,kind: AccountKind = AccountKind.BANK) = Account(id,id,if (kind == AccountKind.CREDIT_CARD) LedgerType.LIABILITY else LedgerType.ASSET,kind,createdAtEpochMillis = 0,updatedAtEpochMillis = 0)
    private fun transaction(kind: EventKind) = Transaction("event",10_000,direction = TransactionDirection.EXPENSE,description = "Example",categoryId = null,occurredDate = LocalDate.of(2026,11,2),occurredTime = null,createdAtEpochMillis = 0,updatedAtEpochMillis = 0,kind = kind)
    @Test fun expenseBalancesAndKeepsCategoryOnNominalEntry() {
        val entries = LedgerPlanner.entries(transaction(EventKind.EXPENSE).copy(categoryId = "food"),account("bank"),null)
        assertEquals(-10_000L,entries[0].amountMinor)
        assertNull(entries[0].categoryId)
        assertEquals("food",entries[1].categoryId)
        assertEquals(0L,entries.sumOf { it.amountMinor })
    }
    @Test fun transferHasTwoOwnedAccountEffectsAndNoNominalExpense() {
        val entries = LedgerPlanner.entries(transaction(EventKind.TRANSFER),account("a"),account("b"))
        assertEquals(listOf("a","b"),entries.map { it.accountId })
        assertEquals(listOf(-10_000L,10_000L),entries.map { it.amountMinor })
    }
    @Test(expected = IllegalArgumentException::class) fun sameAccountTransferIsRejected() {
        LedgerPlanner.entries(transaction(EventKind.TRANSFER),account("a"),account("a"))
    }
    @Test fun cardPurchaseAndPaymentHaveOppositeLiabilitySigns() {
        val card = account("card",AccountKind.CREDIT_CARD)
        val purchase = LedgerPlanner.entries(transaction(EventKind.EXPENSE),card,null)
        val payment = LedgerPlanner.entries(transaction(EventKind.TRANSFER),account("bank"),card)
        assertEquals(-10_000L,purchase[0].amountMinor)
        assertEquals(10_000L,payment[1].amountMinor)
    }
    @Test fun withdrawalIncreasesUnallocatedCashInsteadOfExpenses() {
        val entries = LedgerPlanner.entries(transaction(EventKind.CASH_WITHDRAWAL),account("bank"),null)
        assertEquals(SystemAccountRole.UNALLOCATED_CASH.id("INR"),entries[1].accountId)
        assertEquals(10_000L,entries[1].amountMinor)
    }
    @Test(expected = IllegalArgumentException::class) fun transferCannotCarrySpendingCategory() {
        LedgerPlanner.entries(transaction(EventKind.TRANSFER).copy(categoryId = "food"),account("a"),account("b"))
    }
    @Test(expected = IllegalArgumentException::class) fun mixedCurrencyTransferIsRejected() {
        LedgerPlanner.entries(transaction(EventKind.TRANSFER),account("a"),account("b").copy(currencyCode = "USD"))
    }
    @Test(expected = IllegalArgumentException::class) fun unbalancedEntriesAreRejected() {
        LedgerPlanner.validate(listOf(LedgerEntry("a","event","a",0,100),LedgerEntry("b","event","b",1,-90)))
    }
    @Test fun paiseParsingIsExact() { assertEquals(12_550L,parseMinorUnits("125.50")); assertEquals(-1L,parseMinorUnits("-0.01",true)) }
    @Test(expected = IllegalArgumentException::class) fun excessPrecisionIsRejected() { parseMinorUnits("1.001") }
}
