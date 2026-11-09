package com.piyush.vault.data.sms.parsing

import com.piyush.vault.domain.model.parseMinorUnits

internal data class SmsAmount(val minor: Long?, val inferredCurrency: Boolean)

/** Keep all movement candidates: never select the first amount or sum a fee into the payment. */
internal object SmsAmountRules {
    private const val NUMBER = "[+-]?[0-9][0-9,.]*"
    private const val CURRENCY = "(?:\\bINR\\b|\\bRs\\.?|₹|\\brupees\\b)"
    private val labelled = Regex("(?i)$CURRENCY\\s*($NUMBER)(?![A-Za-z0-9,.])")
    private val suffixed = Regex("(?i)(?<![A-Za-z0-9,.])($NUMBER)\\s*$CURRENCY")
    private val unlabelled = Regex("(?i)\\b(?:debited|credited|withdrawn|refunded|spent|paid|sent|received|deposited)\\s+(?:(?:by|for|with|of|amount)\\s+)*($NUMBER)(?![A-Za-z0-9,.])")
    private val nonMovementLabel = Regex("(?i)\\b(?:(?:avl|avail(?:able)?|current|closing|opening|ledger)\\s*(?:bal(?:ance)?)?|bal(?:ance)?|(?:available\\s+)?(?:credit\\s+)?limit|(?:minimum|total|amount|payment)\\s+due|due(?:\\s+amount)?)\\s*(?:is|of)?\\s*[:=.-]?\\s*$")
    private val validNumber = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?")

    fun hasAmountEvidence(body: String): Boolean = labelled.containsMatchIn(body) || suffixed.containsMatchIn(body) || unlabelled.containsMatchIn(body)

    fun extract(body: String): List<SmsAmount> {
        val tokens = buildList<Token> {
            labelled.findAll(body).forEach { add(Token(it.range, it.groupValues[1], false)) }
            suffixed.findAll(body).forEach { match ->
                if (none { it.range.first <= match.range.last && match.range.first <= it.range.last }) add(Token(match.range, match.groupValues[1], false))
            }
            unlabelled.findAll(body).forEach { match ->
                val group = requireNotNull(match.groups[1])
                if (none { it.range.first <= group.range.last && group.range.first <= it.range.last }) add(Token(group.range, group.value, true))
            }
        }.sortedBy { it.range.first }
        return tokens.filterNot { token ->
            val prefix = body.substring((token.range.first - 64).coerceAtLeast(0), token.range.first)
            nonMovementLabel.containsMatchIn(prefix)
        }.map { token ->
            // A trailing sentence stop is allowed; malformed grouping/precision is not repaired.
            val text = token.value.removeSuffix(".")
            val minor = if (validNumber.matches(text)) runCatching { parseMinorUnits(text.replace(",", "")) }.getOrNull() else null
            SmsAmount(minor, token.inferredCurrency)
        }
    }

    private data class Token(val range: IntRange, val value: String, val inferredCurrency: Boolean)
}
