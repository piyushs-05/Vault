package com.piyush.vault.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.piyush.vault.data.repository.LedgerStore
import com.piyush.vault.domain.model.*
import com.piyush.vault.data.local.entity.toEntity
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LedgerStoreTest {
    @Test fun olderBackfillKeepsAnchorStableAndTransferUndoRestoresBothSides() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,VaultDatabase::class.java).build()
        try {
            val clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"),ZoneOffset.UTC)
            val store = LedgerStore(db,clock)
            store.seed()
            store.saveAccount(AccountDraft(displayName = "Bank A",kind = AccountKind.BANK,openingBalanceMinor = 100_000,openingBalanceDate = LocalDate.of(2026,11,1)))
            store.saveAccount(AccountDraft(displayName = "Bank B",kind = AccountKind.BANK))
            val a = db.ledgerDao().accounts().single { it.displayName == "Bank A" }.id
            val b = db.ledgerDao().accounts().single { it.displayName == "Bank B" }.id
            val expense = Transaction("expense",5_000,direction = TransactionDirection.EXPENSE,description = "Older purchase",categoryId = null,occurredDate = LocalDate.of(2026,10,31),occurredTime = null,createdAtEpochMillis = 0,updatedAtEpochMillis = 0,accountId = a)
            store.save(expense)
            fun balance(rows: List<com.piyush.vault.data.local.entity.AccountBalanceRow>,id: String) = rows.single { it.account.id == id }.balanceMinor
            assertEquals(100_000L,balance(db.ledgerDao().observeAccounts(LocalDate.of(2026,11,1)).first(),a))
            val transfer = expense.copy(id = "transfer",amountMinor = 10_000,kind = EventKind.TRANSFER,accountId = a,destinationAccountId = b,occurredDate = LocalDate.of(2026,11,2))
            store.save(transfer)
            val rows = db.ledgerDao().observeAccounts(LocalDate.of(2026,11,2)).first()
            assertEquals(90_000L,balance(rows,a)); assertEquals(10_000L,balance(rows,b))
            val totals = db.transactionDao().observePeriodTotals(LocalDate.of(2026,10,1),LocalDate.of(2026,11,30)).first()
            assertEquals(5_000L,totals.expensesMinor); assertEquals(0L,totals.incomeMinor)
            val snapshot = requireNotNull(store.delete("transfer"))
            assertTrue(db.ledgerDao().entries("transfer").isEmpty())
            store.restore(snapshot)
            assertEquals(2,db.ledgerDao().entries("transfer").size)
            assertEquals(90_000L,balance(db.ledgerDao().observeAccounts(LocalDate.of(2026,11,2)).first(),a))
        } finally { db.close() }
    }
    @Test fun smsClaimsAreAtomicAndSecondAlertDoesNotDuplicateTransfer() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,VaultDatabase::class.java).build()
        try {
            val clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"),ZoneOffset.UTC)
            val store = LedgerStore(db,clock); store.seed()
            store.saveAccount(AccountDraft(displayName = "A",kind = AccountKind.BANK))
            store.saveAccount(AccountDraft(displayName = "B",kind = AccountKind.BANK))
            val a = db.ledgerDao().accounts().single { it.displayName == "A" }.id
            val b = db.ledgerDao().accounts().single { it.displayName == "B" }.id
            val observation = ParsedFinancialSms("INR 100 debited","HDFC Bank","1234",10_000,"INR",TransactionDirection.EXPENSE,LocalDate.of(2026,11,2),null,"SMS_BODY_DATE",EventKind.UNKNOWN,null,"Review")
            val source = SourceRecord("sms1",TransactionSource.SMS,"hash1",null,"NEEDS_REVIEW",sourceDescription = observation.description,matchedAccountId = a,observation = observation)
            db.ledgerDao().putSources(listOf(source.toEntity()))
            val draft = TransactionDraft(10_000,direction = TransactionDirection.EXPENSE,description = "Own transfer",categoryId = null,occurredDate = observation.date,occurredTime = null,kind = EventKind.TRANSFER,accountId = a,destinationAccountId = b)
            store.acceptSms(source.id,draft)
            val id = requireNotNull(db.ledgerDao().source(source.id)?.eventId)
            assertTrue(runCatching { store.acceptSms(source.id,draft) }.isFailure)
            val second = source.copy(id = "sms2",sourceKey = "hash2",matchedAccountId = b,observation = observation.copy(direction = TransactionDirection.INCOME))
            db.ledgerDao().putSources(listOf(second.toEntity()))
            store.attachSms(second.id,id)
            assertEquals(2,db.ledgerDao().entries(id).size)
            assertEquals(2,db.ledgerDao().sources(id).size)
            val snapshot = requireNotNull(store.delete(id))
            assertEquals("SUPPRESSED",db.ledgerDao().source(source.id)?.state)
            assertNull(db.ledgerDao().source(source.id)?.observedAmountMinor)
            store.restore(snapshot)
            assertEquals(2,db.ledgerDao().sources(id).size)
            assertEquals(10_000L,db.ledgerDao().source(source.id)?.observedAmountMinor)
        } finally { db.close() }
    }
    @Test fun reconcilingUnknownLegsPreservesBalancesAndMovesBothSources() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,VaultDatabase::class.java).build()
        try {
            val store = LedgerStore(db,Clock.systemUTC()); store.seed()
            store.saveAccount(AccountDraft(displayName = "A",kind = AccountKind.BANK))
            store.saveAccount(AccountDraft(displayName = "B",kind = AccountKind.BANK))
            val a = db.ledgerDao().accounts().single { it.displayName == "A" }.id
            val b = db.ledgerDao().accounts().single { it.displayName == "B" }.id
            val outgoing = Transaction("out",10_000,direction = TransactionDirection.EXPENSE,description = "Unknown debit",categoryId = null,occurredDate = LocalDate.now(),occurredTime = null,createdAtEpochMillis = 0,updatedAtEpochMillis = 0,kind = EventKind.UNKNOWN,accountId = a)
            store.save(outgoing); store.save(outgoing.copy(id = "in",direction = TransactionDirection.INCOME,accountId = b))
            store.mergeUnknownTransfer("out","in")
            assertNull(db.ledgerDao().event("in"))
            assertEquals(EventKind.TRANSFER,db.ledgerDao().event("out")?.kind)
            assertEquals(2,db.ledgerDao().sources("out").size)
            val entries = db.ledgerDao().entries("out")
            assertEquals(-10_000L,entries.single { it.accountId == a }.amountMinor)
            assertEquals(10_000L,entries.single { it.accountId == b }.amountMinor)
            assertTrue(runCatching { store.mergeUnknownTransfer("out","in") }.isFailure)
        } finally { db.close() }
    }
}
