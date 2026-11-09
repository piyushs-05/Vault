package com.piyush.vault.data.sms.parsing

import com.piyush.vault.domain.model.EventKind
import com.piyush.vault.domain.model.FinancialSmsParser
import com.piyush.vault.domain.model.IncomingSms
import com.piyush.vault.domain.model.InstitutionDirectory
import com.piyush.vault.domain.model.ParsedFinancialSms
import com.piyush.vault.domain.model.TransactionDirection
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/** Shared English/INR rules. A parsed observation is never permission to post to the ledger. */
class GenericFinancialSmsParser @Inject constructor(
    private val directory: InstitutionDirectory,
    private val clock: Clock,
) : FinancialSmsParser {
    override fun isPotentialAlert(sender: String, body: String): Boolean =
        inspect(sender, body) != null

    override fun parse(message: IncomingSms): ParsedFinancialSms? {
        val evidence = inspect(message.sender, message.body) ?: return null
        val reasons = linkedSetOf("Confirm financial purpose and account")
        val amounts = SmsAmountRules.extract(evidence.body)
        val movements = evidence.movements
        val directions = movements.map { it.direction }.distinct()
        // More than one movement can mean two events, even if both have the same amount/direction.
        val multipleMovements = movements.size > 1
        val uncertainStatus = SmsMovementRules.hasUncertainStatus(evidence.body)
        val direction = directions.singleOrNull().takeUnless { multipleMovements || uncertainStatus }
        val amount = amounts.singleOrNull()?.minor.takeUnless { multipleMovements || uncertainStatus }
        if (amount == null) reasons += "Amount missing, invalid or ambiguous; enter the movement amount"
        if (direction == null) reasons += "Choose money in or money out; movement direction is unresolved"
        if (multipleMovements) reasons += "Multiple movement clauses; this alert may describe more than one event"
        if (uncertainStatus) reasons += "Failed, pending or conditional status also present; verify the actual posted movement"
        if (amounts.singleOrNull()?.inferredCurrency == true) reasons += "INR assumed from an unlabelled amount; confirm currency"

        val account = SmsMetadataRules.accountSuffix(evidence.body)
        if (account == null) reasons += "Account suffix unavailable or ambiguous"
        val timestamp = message.sentAtMillis.takeIf { it > 0 } ?: message.receivedAtMillis
        val fallback = Instant.ofEpochMilli(timestamp).atZone(clock.zone).toLocalDate()
        val date = SmsMetadataRules.date(evidence.body)
        if (date == null) reasons += "Date missing, invalid or ambiguous; suggested from message timestamp"
        val time = SmsMetadataRules.time(evidence.body)
        val kind = when {
            direction == null -> EventKind.UNKNOWN
            SmsMovementRules.reversal.containsMatchIn(evidence.body) -> EventKind.REVERSAL
            SmsMovementRules.refund.containsMatchIn(evidence.body) -> EventKind.REFUND
            direction == TransactionDirection.EXPENSE && SmsMovementRules.withdrawal.containsMatchIn(evidence.body) -> EventKind.CASH_WITHDRAWAL
            Regex("(?i)\\b(spent|purchase|paid to)\\b").containsMatchIn(evidence.body) -> EventKind.EXPENSE
            direction == TransactionDirection.INCOME && Regex("(?i)\\bsalary\\b").containsMatchIn(evidence.body) -> EventKind.INCOME
            else -> EventKind.UNKNOWN
        }
        if (kind == EventKind.REFUND || kind == EventKind.REVERSAL) reasons += "Link the original event"
        // 'Transferred' alone does not establish that both accounts belong to this user.
        if (Regex("(?i)\\b(transfer(?:red)?|trf)\\b").containsMatchIn(evidence.body)) {
            reasons += "Confirm whether this is an own-account transfer or a payment"
        }
        return ParsedFinancialSms(
            description = SmsMetadataRules.sanitizedDescription(evidence.body),
            institution = directory.institutionForSender(message.sender),
            lastFour = account,
            amountMinor = amount,
            currencyCode = "INR",
            direction = direction,
            date = date ?: fallback,
            time = time,
            dateBasis = if (date == null) "SMS_DATE_UNCERTAIN" else "SMS_BODY_DATE",
            kind = kind,
            reference = SmsMetadataRules.reference(evidence.body),
            reviewReason = reasons.joinToString(" · "),
        )
    }

    private fun inspect(sender: String, body: String): SmsEvidence? {
        val header = sender.trim().uppercase(Locale.ROOT)
        if (body.length !in 8..8_000 || !header.matches(Regex("[A-Z0-9-]{3,32}")) || header.none { it in 'A'..'Z' }) return null
        val normalized = SmsMetadataRules.normalize(body)
        // Keep actual authentication messages out of durable staging. A safety footer saying
        // 'do not share OTP' alone is not an authentication message.
        if (Regex("(?i)\\b(?:otp|one[ -]time password|verification code)\\s*(?:is|:|-)?\\s*[0-9]{3,8}\\b|\\b[0-9]{3,8}\\s+is your\\s+(?:otp|one[ -]time password)").containsMatchIn(normalized)) return null
        // Initial pipeline is INR only; never silently reinterpret an explicit foreign currency.
        if (Regex("(?i)\\b(?:USD|AED|EUR|GBP|SGD|AUD|CAD)\\b|[$€£]").containsMatchIn(normalized)) return null
        val movements = SmsMovementRules.extract(normalized)
        if (movements.isEmpty()) return null
        if (!SmsAmountRules.hasAmountEvidence(normalized)) return null
        return SmsEvidence(normalized, movements)
    }

    companion object {
        const val PARSER_ID = "generic-financial-alert"
        const val PARSER_VERSION = 2
    }
}

private data class SmsEvidence(val body: String, val movements: List<SmsMovement>)
