package com.piyush.vault.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class TransactionDirection {
    EXPENSE,
    INCOME,
}

enum class TransactionSource {
    MANUAL,
    CSV,
    SMS,
    LEGACY,
}

/** Domain representation; amountMinor is a positive number of paise. */
data class Transaction(
    val id: String,
    val amountMinor: Long,
    val currencyCode: String = "INR",
    val direction: TransactionDirection,
    val description: String,
    val categoryId: String?,
    val occurredDate: LocalDate,
    val occurredTime: LocalTime?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val source: TransactionSource = TransactionSource.MANUAL,
    val importBatchId: String? = null,
    val kind: EventKind = if (direction == TransactionDirection.EXPENSE) EventKind.EXPENSE else EventKind.INCOME,
    val accountId: String? = null,
    val accountName: String? = null,
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val userNote: String? = null,
    val originalEventId: String? = null,
    val revision: Long = 0,
    val deletedSnapshot: LedgerSnapshot? = null,
)

/** Editable fields supplied by a form; persistence IDs and timestamps are assigned by the use case. */
data class TransactionDraft(
    val amountMinor: Long,
    val currencyCode: String = "INR",
    val direction: TransactionDirection,
    val description: String,
    val categoryId: String?,
    val occurredDate: LocalDate,
    val occurredTime: LocalTime?,
    val id: String? = null,
    val kind: EventKind = if (direction == TransactionDirection.EXPENSE) EventKind.EXPENSE else EventKind.INCOME,
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val userNote: String? = null,
    val originalEventId: String? = null,
    val expectedRevision: Long? = null,
)

data class TransactionFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val categoryId: String? = null,
    val onlyUncategorized: Boolean = false,
    val direction: TransactionDirection? = null,
    val descriptionQuery: String = "",
    val accountId: String? = null,
    val kind: EventKind? = null,
    val currencyCode: String? = null,
) {
    init {
        require(startDate == null || endDate == null || startDate <= endDate) {
            "Start date must be on or before end date."
        }
        require(!(onlyUncategorized && categoryId != null)) {
            "Choose either one category or uncategorized transactions."
        }
    }
}

data class TransactionListItem(
    val transaction: Transaction,
    val categoryName: String?,
)

data class PeriodTotals(
    val expensesMinor: Long,
    val incomeMinor: Long,
    val refundsMinor: Long = 0,
    val unknownOutflowMinor: Long = 0,
)

data class CategorySpending(
    val categoryId: String?,
    val categoryName: String?,
    val totalMinor: Long,
)
