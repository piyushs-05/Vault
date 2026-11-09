package com.piyush.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.piyush.vault.data.local.entity.*
import com.piyush.vault.domain.model.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("""
        SELECT * FROM transaction_history t
        WHERE (:startDate IS NULL OR occurredDate>=:startDate)
          AND (:endDate IS NULL OR occurredDate<=:endDate)
          AND (:filterCategory=0 OR (reportType IS NOT NULL AND categoryId IS :categoryId))
          AND (:direction IS NULL OR reportType=:direction)
          AND (:kind IS NULL OR kind=:kind)
          AND (:accountId IS NULL OR EXISTS (SELECT 1 FROM ledger_entries l WHERE l.eventId=t.id AND l.accountId=:accountId))
          AND (:currency IS NULL OR currencyCode=:currency)
          AND (:descriptionQuery='' OR description LIKE '%' || :descriptionQuery || '%' COLLATE NOCASE OR userNote LIKE '%' || :descriptionQuery || '%' COLLATE NOCASE)
        ORDER BY occurredDate DESC, occurredTime IS NULL, occurredTime DESC, createdAtEpochMillis DESC
    """)
    fun observeTransactions(startDate: LocalDate?, endDate: LocalDate?, filterCategory: Boolean, categoryId: String?, direction: TransactionDirection?, descriptionQuery: String, accountId: String?, kind: EventKind?, currency: String?): Flow<List<TransactionHistoryRow>>
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN a.ledgerType='EXPENSE' AND l.amountMinor>0 THEN l.amountMinor ELSE 0 END),0) AS expensesMinor,
               COALESCE(SUM(CASE WHEN a.ledgerType='INCOME' THEN -l.amountMinor ELSE 0 END),0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN a.ledgerType='EXPENSE' AND l.amountMinor<0 THEN -l.amountMinor ELSE 0 END),0) AS refundsMinor,
               COALESCE(SUM(CASE WHEN a.systemRole='SUSPENSE' AND l.amountMinor>0 THEN l.amountMinor ELSE 0 END),0) AS unknownOutflowMinor
        FROM ledger_entries l JOIN accounts a ON a.id=l.accountId JOIN financial_events e ON e.id=l.eventId
        WHERE e.occurredDate BETWEEN :startDate AND :endDate AND e.currencyCode='INR'
    """)
    fun observePeriodTotals(startDate: LocalDate, endDate: LocalDate): Flow<PeriodTotalsRow>
    @Query("""
        SELECT l.categoryId, c.name AS categoryName, SUM(l.amountMinor) AS totalMinor
        FROM ledger_entries l JOIN accounts a ON a.id=l.accountId JOIN financial_events e ON e.id=l.eventId
        LEFT JOIN categories c ON c.id=l.categoryId
        WHERE a.ledgerType='EXPENSE' AND e.occurredDate BETWEEN :startDate AND :endDate AND e.currencyCode='INR'
        GROUP BY l.categoryId,c.name ORDER BY totalMinor DESC
    """)
    fun observeCategorySpending(startDate: LocalDate, endDate: LocalDate): Flow<List<CategorySpendingRow>>
    @Query("SELECT * FROM transaction_history WHERE id=:id") suspend fun getById(id: String): TransactionHistoryRow?
}
