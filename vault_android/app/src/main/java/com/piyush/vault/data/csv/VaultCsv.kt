package com.piyush.vault.data.csv

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

/** Versioned event rows, sufficient to reconstruct the supported two-entry event shapes. Not a DB backup. */
object VaultCsv {
    private val headers = listOf("VaultFormat","EventId","Date","Time","Kind","Direction","Amount","Currency","Description","Note","OriginalEventId","Category",
        "AccountId","AccountName","AccountKind","Bank","LastFour","OpeningBalanceMinor","OpeningDate",
        "DestinationAccountId","DestinationAccountName","DestinationAccountKind","DestinationBank","DestinationLastFour","DestinationOpeningBalanceMinor","DestinationOpeningDate")

    fun export(rows: List<TransactionListItem>,accounts: List<Account>): String = buildString {
        appendLine(headers.joinToString(","))
        rows.forEach { item ->
            val t = item.transaction
            val a = accounts.firstOrNull { it.id == t.accountId }
            val b = accounts.firstOrNull { it.id == t.destinationAccountId && it.isUserAccount }
            fun fields(account: Account?) = listOf(account?.id,account?.displayName,account?.accountKind?.name,account?.institutionLabel,account?.lastFour,account?.openingBalanceMinor?.toString(),account?.openingBalanceDate?.toString()).map { it.orEmpty() }
            val direction = if (t.kind in listOf(EventKind.TRANSFER,EventKind.CASH_WITHDRAWAL)) TransactionDirection.EXPENSE else t.direction
            val cells = listOf("2",t.id,t.occurredDate.toString(),t.occurredTime?.toString().orEmpty(),t.kind.name,direction.name,minorUnitsText(t.amountMinor),t.currencyCode,t.description,t.userNote.orEmpty(),t.originalEventId.orEmpty(),item.categoryName.orEmpty()) + fields(a) + fields(b)
            appendLine(cells.joinToString(",") { escape(it) })
        }
    }

    fun preview(table: CsvTable,existing: Set<String>): List<CsvPreviewRow> {
        val seen = mutableSetOf<String>()
        return table.rows.mapIndexed { index,row ->
            fun text(name: String) = table.headers.indexOf(name).takeIf { it>=0 }?.let { row.getOrElse(it) { "" } }.orEmpty()
            try {
                require(text("VaultFormat") == "2" && headers.all { it in table.headers }) { "Unsupported or incomplete Vault CSV format." }
                val currency = text("Currency")
                require(currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency." }
                val kind = EventKind.valueOf(text("Kind"))
                require(kind != EventKind.OPENING_BALANCE) { "Opening adjustments are derived from account metadata." }
                val amount = parseMinorUnits(text("Amount"))
                val date = LocalDate.parse(text("Date"))
                val time = text("Time").takeIf { it.isNotBlank() }?.let(LocalTime::parse)
                val direction = TransactionDirection.valueOf(text("Direction"))
                fun account(destination: Boolean): Account? {
                    val prefix = if (destination) "Destination" else ""
                    val id = text(prefix+"AccountId").takeIf { it.isNotBlank() } ?: return null
                    require(!id.startsWith("system:") && !id.startsWith("opening:")) { "Reserved account ID." }
                    val accountKind = AccountKind.valueOf(text(prefix+"AccountKind"))
                    val bank = text(if (destination) "DestinationBank" else "Bank").takeIf { it.isNotBlank() }
                    val suffix = text(if (destination) "DestinationLastFour" else "LastFour").takeIf { it.isNotBlank() }
                    require(suffix == null || suffix.matches(Regex("[0-9]{4}"))) { "Invalid masked account suffix." }
                    val opening = text(if (destination) "DestinationOpeningBalanceMinor" else "OpeningBalanceMinor").takeIf { it.isNotBlank() }?.toLong()
                    val openingDate = text(if (destination) "DestinationOpeningDate" else "OpeningDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    require((opening == null) == (openingDate == null)) { "Opening metadata is incomplete." }
                    val name = text(prefix+"AccountName").trim()
                    require(name.isNotEmpty()) { "Account name is missing." }
                    return Account(id,name,if (accountKind == AccountKind.CREDIT_CARD) LedgerType.LIABILITY else LedgerType.ASSET,accountKind,currency,
                        institutionLabel = bank,lastFour = suffix,openingBalanceMinor = opening,openingBalanceDate = openingDate,createdAtEpochMillis = 0,updatedAtEpochMillis = 0)
                }
                val a = account(false); val b = account(true)
                require(kind != EventKind.TRANSFER || (a != null && b != null && a.id != b.id)) { "Transfer accounts are incomplete." }
                require(kind != EventKind.CASH_WITHDRAWAL || a?.accountKind == AccountKind.BANK) { "A withdrawal needs its bank account." }
                val description = text("Description").trim()
                require(description.isNotEmpty()) { "Description is missing." }
                val sourceId = text("EventId")
                require(sourceId.isNotBlank()) { "Event ID is missing." }
                val fingerprint = csvFingerprint(date,amount,direction,description,currency) + "|" + a?.id.orEmpty()
                val duplicate = fingerprint in existing || !seen.add(sourceId)
                CsvPreviewRow(index+2,amount,direction,description,date,time,duplicate,!duplicate,
                    kind = kind,currencyCode = currency,importedAccount = a,importedDestination = b,sourceEventId = sourceId,
                    originalEventId = text("OriginalEventId").takeIf { it.isNotBlank() },note = text("Note").takeIf { it.isNotBlank() },categoryName = text("Category").takeIf { it.isNotBlank() })
            } catch (_: Exception) { CsvPreviewRow(index+2,errorMessage = "This Vault row has invalid or incomplete event/account fields.") }
        }
    }
    private fun escape(text: String) = if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"","\"\"")}\"" else text
}
