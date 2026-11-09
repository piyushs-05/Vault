package com.piyush.vault.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Product gates; history implementation is retained but deliberately unavailable for now. */
object SmsCapabilities {
    const val HISTORY_IMPORT_ENABLED = false
}

data class IncomingSms(val sender: String, val body: String, val sentAtMillis: Long, val receivedAtMillis: Long, val providerId: String? = null)

data class ParsedFinancialSms(
    val description: String,
    val institution: String?,
    val lastFour: String?,
    val amountMinor: Long?,
    val currencyCode: String = "INR",
    val direction: TransactionDirection?,
    val date: LocalDate,
    val time: LocalTime?,
    val dateBasis: String,
    val kind: EventKind,
    val reference: String?,
    val reviewReason: String,
)

data class SmsSettings(
    val historyEnabled: Boolean = false,
    val liveEnabled: Boolean = false,
    val generation: Long = 0,
    val consentedAtMillis: Long? = null,
    val consentVersion: Int = 0,
    val scanStatus: String = "",
    val notificationsEnabled: Boolean = false,
) { val enabled: Boolean get() = historyEnabled || liveEnabled }

data class SmsCandidate(val source: SourceRecord, val observation: ParsedFinancialSms, val matchedAccountId: String?)

interface FinancialSmsParser {
    fun isPotentialAlert(sender: String,body: String): Boolean
    fun parse(message: IncomingSms): ParsedFinancialSms?
}

interface InstitutionDirectory {
    fun names(): List<String>
    fun institutionForSender(sender: String): String?
}
