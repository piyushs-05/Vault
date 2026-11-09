package com.piyush.vault.data.local.entity

import androidx.room.DatabaseView
import com.piyush.vault.data.local.migration.LedgerSchemaV2
import com.piyush.vault.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

/** Read projection only. Financial writes go through the aggregate ledger writer. */
@DatabaseView(viewName = "transaction_history", value = LedgerSchemaV2.HISTORY_SELECT)
data class TransactionHistoryRow(
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val direction: TransactionDirection,
    val kind: EventKind,
    val description: String,
    val userNote: String?,
    val categoryId: String?,
    val categoryName: String?,
    val occurredDate: LocalDate,
    val occurredTime: LocalTime?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val source: TransactionSource,
    val importBatchId: String?,
    val accountId: String?,
    val accountName: String,
    val destinationAccountId: String?,
    val destinationAccountName: String?,
    val originalEventId: String?,
    val revision: Long,
    val reportType: String?,
)

data class PeriodTotalsRow(val expensesMinor: Long, val incomeMinor: Long, val refundsMinor: Long, val unknownOutflowMinor: Long)
data class CategorySpendingRow(val categoryId: String?, val categoryName: String?, val totalMinor: Long)
