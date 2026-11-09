package com.piyush.vault.data.csv

import com.piyush.vault.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Small RFC 4180-style parser: handles quoted commas, escaped quotes, and line breaks inside quotes. */
object CsvDocumentParser {
    fun parse(content: String): CsvTable {
        val format = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setAllowMissingColumnNames(true).get()
        return format.parse(content.removePrefix("\uFEFF").reader()).use { parser ->
            val headers = parser.headerNames.mapIndexed { index,name -> name.trim().ifBlank { "Column ${index+1}" } }
            val rows = parser.map { record ->
                require(record.size() == headers.size) { "CSV row ${record.recordNumber+1} has a different number of fields than its header." }
                record.toList()
            }
            require(rows.isNotEmpty()) { "The CSV needs at least one data row." }
            require(rows.size <= 100_000) { "Import at most 100,000 rows at once." }
            CsvTable(headers,rows)
        }
    }

    fun defaultMapping(headers: List<String>): CsvColumnMapping {
        fun find(vararg names: String): Int? = headers.indexOfFirst { header ->
            val normalized = header.trim().lowercase(Locale.ROOT)
            names.any { normalized == it || normalized.contains(it) }
        }.takeIf { it >= 0 }

        val date = find("transaction date", "date", "value date")
        val description = find("description", "narration", "remarks", "particulars", "merchant")
        val debit = find("debit", "withdrawal", "withdrawals")
        val credit = find("credit", "deposit", "deposits")
        val amount = find("amount", "transaction amount")
        return CsvColumnMapping(
            dateColumn = date,
            descriptionColumn = description,
            amountColumn = amount.takeIf { debit == null && credit == null },
            directionColumn = find("direction", "type", "dr/cr", "transaction type"),
            debitColumn = debit,
            creditColumn = credit,
            timeColumn = find("time", "transaction time"),
        )
    }

    fun preview(
        table: CsvTable,
        mapping: CsvColumnMapping,
        existingFingerprints: Set<String>,
    ): List<CsvPreviewRow> {
        if ("VaultFormat" in table.headers) return VaultCsv.preview(table,existingFingerprints)
        val seen = mutableSetOf<String>()
        val fieldsMapped = mapping.dateColumn != null && mapping.descriptionColumn != null &&
            ((mapping.amountColumn != null && mapping.debitColumn == null && mapping.creditColumn == null) ||
                (mapping.amountColumn == null && mapping.debitColumn != null && mapping.creditColumn != null))
        if (!fieldsMapped) {
            return table.rows.mapIndexed { index, _ ->
                CsvPreviewRow(rowNumber = index + 2, errorMessage = "Choose date, description, and amount columns.")
            }
        }

        return table.rows.mapIndexed { index, row ->
            try {
                val date = parseDate(value(row, mapping.dateColumn))
                val description = value(row, mapping.descriptionColumn).trim()
                require(description.isNotEmpty()) { "Description is empty." }
                val timeText = value(row, mapping.timeColumn).trim()
                val time = timeText.takeIf { it.isNotEmpty() }?.let(::parseTime)

                val (amountMinor, direction) = if (mapping.amountColumn != null) {
                    val parsed = parseAmount(value(row, mapping.amountColumn))
                    val mappedDirection = value(row, mapping.directionColumn).takeIf { it.isNotBlank() }
                        ?.let(::parseDirection)
                    val inferred = if (parsed.signum() < 0) TransactionDirection.EXPENSE else TransactionDirection.INCOME
                    val amount = parsed.abs().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
                    amount to (mappedDirection ?: inferred)
                } else {
                    val debit = value(row,mapping.debitColumn).takeIf { it.isNotBlank() }?.let(::parseAmount)?.abs() ?: BigDecimal.ZERO
                    val credit = value(row,mapping.creditColumn).takeIf { it.isNotBlank() }?.let(::parseAmount)?.abs() ?: BigDecimal.ZERO
                    require((debit.signum()>0) xor (credit.signum()>0)) { "Exactly one debit/credit amount must be nonzero." }
                    val isDebit = debit.signum()>0
                    val amount = (if (isDebit) debit else credit).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
                    amount to if (isDebit) TransactionDirection.EXPENSE else TransactionDirection.INCOME
                }
                require(amountMinor > 0) { "Amount must be greater than zero." }

                val currencyIndex = table.headers.indexOfFirst { it.equals("Currency",ignoreCase = true) }
                val currency = if (currencyIndex >= 0) row[currencyIndex].trim().uppercase(Locale.ROOT) else "INR"
                require(currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency." }
                val fingerprint = csvFingerprint(date, amountMinor, direction, description,currency)
                val duplicate = fingerprint in existingFingerprints || !seen.add(fingerprint)
                CsvPreviewRow(
                    rowNumber = index + 2,
                    amountMinor = amountMinor,
                    direction = direction,
                    description = description,
                    occurredDate = date,
                    occurredTime = time,
                    isLikelyDuplicate = duplicate,
                    isIncluded = !duplicate,
                    currencyCode = currency,
                    kind = if (direction == TransactionDirection.EXPENSE) com.piyush.vault.domain.model.EventKind.EXPENSE else com.piyush.vault.domain.model.EventKind.INCOME,
                )
            } catch (error: Exception) {
                CsvPreviewRow(
                    rowNumber = index + 2,
                    errorMessage = error.message ?: "This row could not be parsed.",
                )
            }
        }
    }

    private fun value(row: List<String>, column: Int?): String = column?.let { row.getOrElse(it) { "" } }.orEmpty()

    private fun parseAmount(raw: String): BigDecimal {
        var normalized = raw.trim().replace("₹", "").replace("INR", "", ignoreCase = true)
            .replace(",", "").replace(" ", "")
        val parenthesizedNegative = normalized.startsWith("(") && normalized.endsWith(")")
        if (parenthesizedNegative) normalized = normalized.substring(1, normalized.length - 1)
        val value = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Amount '$raw' is not a supported number.")
        }
        return if (parenthesizedNegative) value.negate() else value
    }

    private fun parseDate(raw: String): LocalDate {
        val value = raw.trim()
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT),
        )
        for (format in formats) {
            try {
                return LocalDate.parse(value, format)
            } catch (_: DateTimeParseException) {
                // Try the next documented generic date format.
            }
        }
        throw IllegalArgumentException("Date '$raw' is unsupported; use YYYY-MM-DD or a day-first date.")
    }

    private fun parseTime(raw: String): LocalTime {
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH),
        )
        for (format in formats) {
            try {
                return LocalTime.parse(raw, format)
            } catch (_: DateTimeParseException) {
                // Try another common time representation.
            }
        }
        throw IllegalArgumentException("Time '$raw' is unsupported.")
    }

    private fun parseDirection(raw: String): TransactionDirection = when (raw.trim().lowercase(Locale.ROOT)) {
        "debit", "dr", "expense", "withdrawal", "withdraw" -> TransactionDirection.EXPENSE
        "credit", "cr", "income", "deposit" -> TransactionDirection.INCOME
        else -> throw IllegalArgumentException("Transaction type '$raw' is not recognized as debit or credit.")
    }
}
