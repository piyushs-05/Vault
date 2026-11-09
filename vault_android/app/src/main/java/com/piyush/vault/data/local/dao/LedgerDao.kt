package com.piyush.vault.data.local.dao

import androidx.room.*
import com.piyush.vault.data.local.entity.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT a.*, COALESCE((SELECT SUM(l.amountMinor) FROM ledger_entries l JOIN financial_events e ON e.id=l.eventId WHERE l.accountId=a.id AND e.occurredDate<=:today),0) AS balanceMinor FROM accounts a WHERE a.systemRole IS NULL OR a.systemRole IN ('UNALLOCATED_CASH','LEGACY_UNASSIGNED') ORDER BY a.isArchived, a.systemRole IS NOT NULL, a.displayName COLLATE NOCASE")
    fun observeAccounts(today: LocalDate): Flow<List<AccountBalanceRow>>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun account(id: String): AccountEntity?
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Upsert suspend fun putAccount(account: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedAccounts(accounts: List<AccountEntity>)
    @Query("UPDATE accounts SET isArchived=:archived, updatedAtEpochMillis=:now WHERE id=:id AND systemRole IS NULL") suspend fun archive(id: String, archived: Boolean, now: Long)
    @Query("SELECT * FROM financial_events WHERE id=:id") suspend fun event(id: String): FinancialEventEntity?
    @Query("SELECT * FROM financial_events WHERE originalEventId=:id") suspend fun related(id: String): List<FinancialEventEntity>
    @Query("SELECT * FROM ledger_entries WHERE eventId=:id ORDER BY position") suspend fun entries(id: String): List<LedgerEntryEntity>
    @Query("SELECT * FROM source_records WHERE eventId=:id ORDER BY id") suspend fun sources(id: String): List<SourceRecordEntity>
    @Query("SELECT * FROM source_records WHERE id=:id") suspend fun source(id: String): SourceRecordEntity?
    @Upsert suspend fun putEvent(event: FinancialEventEntity)
    @Insert suspend fun insertEntries(entries: List<LedgerEntryEntity>)
    @Upsert suspend fun putSources(sources: List<SourceRecordEntity>)
    @Query("DELETE FROM ledger_entries WHERE eventId=:id") suspend fun removeEntries(id: String)
    @Query("DELETE FROM financial_events WHERE id=:id") suspend fun removeEvent(id: String)
    @Query("UPDATE source_records SET state='SUPPRESSED', eventId=NULL, sourceDescription='', matchedAccountId=NULL,detectedInstitution=NULL,maskedLastFour=NULL,observedAmountMinor=NULL,observedCurrency=NULL,observedDirection=NULL,observedDate=NULL,observedTime=NULL,dateBasis=NULL,suggestedKind=NULL,externalReference=NULL,reviewReason=NULL WHERE eventId=:id") suspend fun suppressSources(id: String)
    @Query("SELECT COALESCE(SUM(l.amountMinor),0) FROM ledger_entries l JOIN financial_events e ON e.id=l.eventId WHERE l.accountId=:accountId AND e.occurredDate<:date AND e.kind!='OPENING_BALANCE'")
    suspend fun sumBefore(accountId: String, date: LocalDate): Long
    @Query("SELECT COALESCE(SUM(ABS(l.amountMinor)),0) FROM ledger_entries l JOIN financial_events e ON e.id=l.eventId WHERE e.originalEventId=:originalId AND e.kind='REFUND' AND e.id!=:excludingId AND l.position=0")
    suspend fun refunded(originalId: String, excludingId: String): Long
}
