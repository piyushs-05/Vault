package com.piyush.vault.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class EventKind {
    EXPENSE, INCOME, TRANSFER, CASH_WITHDRAWAL, REFUND, REVERSAL, UNKNOWN, OPENING_BALANCE;

    val label: String get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    val hasCategory: Boolean get() = this == EXPENSE || this == INCOME || this == REFUND
}

data class LedgerEvent(
    val id: String,
    val kind: EventKind,
    val currencyCode: String,
    val description: String,
    val userNote: String?,
    val occurredDate: LocalDate,
    val occurredTime: LocalTime?,
    val dateBasis: String = "USER_CONFIRMED",
    val originalEventId: String? = null,
    val systemKey: String? = null,
    val revision: Long = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Debit-positive: assets/expenses increase with +; liabilities/income increase with -. */
data class LedgerEntry(
    val id: String,
    val eventId: String,
    val accountId: String,
    val position: Int,
    val amountMinor: Long,
    val categoryId: String? = null,
)

data class SourceRecord(
    val id: String,
    val sourceType: TransactionSource,
    val sourceKey: String,
    val eventId: String?,
    val state: String = "ATTACHED",
    val importBatchId: String? = null,
    val sourceRowNumber: Int? = null,
    val sourceDescription: String,
    val observedAtEpochMillis: Long? = null,
    val smsEnvelopeId: String? = null,
    val matchedAccountId: String? = null,
    val observation: ParsedFinancialSms? = null,
    val parserId: String? = null,
    val parserVersion: Int? = null,
)

data class LedgerSnapshot(
    val event: LedgerEvent,
    val entries: List<LedgerEntry>,
    val sources: List<SourceRecord>,
)
