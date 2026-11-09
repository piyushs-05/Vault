package com.piyush.vault.domain.model

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

data class CsvColumnMapping(
    val dateColumn: Int? = null,
    val descriptionColumn: Int? = null,
    val amountColumn: Int? = null,
    val directionColumn: Int? = null,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val timeColumn: Int? = null,
)

data class CsvPreviewRow(
    val rowNumber: Int,
    val amountMinor: Long? = null,
    val direction: TransactionDirection? = null,
    val description: String = "",
    val occurredDate: LocalDate? = null,
    val occurredTime: LocalTime? = null,
    val isLikelyDuplicate: Boolean = false,
    val isIncluded: Boolean = false,
    val errorMessage: String? = null,
    val kind: EventKind? = null,
    val currencyCode: String = "INR",
    val importedAccount: Account? = null,
    val importedDestination: Account? = null,
    val sourceEventId: String? = null,
    val originalEventId: String? = null,
    val note: String? = null,
    val categoryName: String? = null,
)


fun csvFingerprint(date: LocalDate, amountMinor: Long, direction: TransactionDirection, description: String, currency: String = "INR"): String =
    listOf(date.toString(),amountMinor.toString(),direction.name,currency,description.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")).joinToString("|")
