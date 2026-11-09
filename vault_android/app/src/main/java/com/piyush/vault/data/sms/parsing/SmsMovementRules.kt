package com.piyush.vault.data.sms.parsing

import com.piyush.vault.domain.model.TransactionDirection
import java.util.Locale

internal data class SmsMovement(val direction: TransactionDirection?)

/** Movement words must be completed and refer to the user's side, rather than a beneficiary. */
internal object SmsMovementRules {
    private val words = Regex("(?i)\\b(debited|credited|spent|paid|withdrawn|withdrawal|refunded|reversed|received|sent|deposited|transferred)\\b")
    val refund = Regex("(?i)\\b(refund|refunded)\\b")
    val reversal = Regex("(?i)\\b(reversed|reversal)\\b")
    val withdrawal = Regex("(?i)\\b(withdrawn|withdrawal)\\b")
    private val negation = Regex("(?i)\\b(?:not|never|wasn't|was not|has not been|hasn't been|will not be|not been)\\s+(?:(?:be|been|being|yet|successfully)\\s+)*$")
    private val future = Regex("(?i)\\b(?:will|would|shall|may|might|to)\\s+(?:be\\s+)?$|\\b(?:request(?:ed)?|scheduled)\\s+(?:to\\s+)?(?:be\\s+)?$")
    private val otherPartyBefore = Regex("(?i)\\b(?:beneficiary|recipient|receiver|payee)(?:'s)?(?:\\s+(?:a/c|account|has|been|is|was|successfully|amount|bank|now|being))*\\s*$")
    private val otherPartyAfter = Regex("(?i)^\\s+(?:to|into|in)\\s+(?:the\\s+)?(?:beneficiary|recipient|receiver|payee)\\b")
    private val uncertain = Regex("(?i)\\b(failed|declined|unsuccessful|pending|processing|initiated|requested|scheduled|under review)\\b|\\b(?:will|may|might|shall|would)\\s+be\\b|\\bif\\b[^.;!?]{0,50}\\b(?:debited|credited|paid|withdrawn|received)\\b")

    fun hasUncertainStatus(body: String): Boolean = uncertain.containsMatchIn(body)

    fun extract(body: String): List<SmsMovement> = words.findAll(body).mapNotNull { match ->
        val before = body.substring((match.range.first - 70).coerceAtLeast(0), match.range.first)
        val after = body.substring(match.range.last + 1, (match.range.last + 71).coerceAtMost(body.length))
        if (negation.containsMatchIn(before) || future.containsMatchIn(before) ||
            otherPartyBefore.containsMatchIn(before) || otherPartyAfter.containsMatchIn(after)) return@mapNotNull null
        // These words can describe delivery of a code/request rather than movement of money.
        if (Regex("(?i)^\\s+(?:otp|code|request|notification|alert)\\b").containsMatchIn(after)) return@mapNotNull null
        val direction = when (match.value.lowercase(Locale.ROOT)) {
            "credited", "refunded", "received", "deposited" -> TransactionDirection.INCOME
            "reversed" -> null // Keep the observation, but a reversal alone doesn't establish direction.
            "transferred" -> when {
                Regex("(?i)^\\s+(?:to|into)\\s+your\\s+(?:a/c|account|acct)\\b").containsMatchIn(after) -> TransactionDirection.INCOME
                Regex("(?i)^\\s+from\\s+(?:your\\s+)?(?:a/c|account|acct)\\b").containsMatchIn(after) -> TransactionDirection.EXPENSE
                else -> null
            }
            "sent" -> if (Regex("(?i)^\\s+(?:to|into)\\s+your\\s+(?:a/c|account|acct)\\b").containsMatchIn(after)) TransactionDirection.INCOME else TransactionDirection.EXPENSE
            else -> TransactionDirection.EXPENSE
        }
        SmsMovement(direction)
    }.toList()
}
