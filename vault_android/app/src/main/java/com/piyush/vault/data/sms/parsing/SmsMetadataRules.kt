package com.piyush.vault.data.sms.parsing

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

internal object SmsMetadataRules {
    fun normalize(body: String): String = body.replace('\u00a0', ' ').replace('\u202f', ' ')
        .replace(Regex("[\\t\\r\\n ]+"), " ").trim()

    private val account = Regex("(?i)\\b(?:a/c|ac|acct|account|card)(?:\\s*(?:no\\.?|number|ending(?: in)?|ends(?: in)?|in|with))?\\s*[:.-]?\\s*([X*#0-9]{4,24})(?![X*#0-9])")
    private val otherParty = Regex("(?i)\\b(?:beneficiary|recipient|receiver|payee)(?:'s)?\\s*$")
    fun accountSuffix(body: String): String? = account.findAll(body).filterNot {
        otherParty.containsMatchIn(body.substring((it.range.first - 40).coerceAtLeast(0), it.range.first))
    }.mapNotNull { match ->
        // Require four visible trailing digits; do not combine separated masked fragments.
        Regex("[0-9]{4}$").find(match.groupValues[1])?.value
    }.distinct().toList().singleOrNull()

    private val dateToken = Regex("(?i)\\b(?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[-/][0-9]{1,2}[-/](?:[0-9]{4}|[0-9]{2})|[0-9]{1,2}[-/ ]?[A-Za-z]{3}[-/ ]?(?:[0-9]{4}|[0-9]{2}))\\b")
    private val dateFormats = listOf("uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu", "d/M/uu", "d-M-uu", "d-MMM-uuuu", "d-MMM-uu", "d/MMM/uuuu", "d/MMM/uu", "d MMM uuuu", "d MMM uu", "dMMMuuuu", "dMMMuu").map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }
    fun date(body: String): LocalDate? {
        val tokens = dateToken.findAll(body).toList()
        if (tokens.isEmpty()) return null
        val dates = tokens.map { token -> dateFormats.firstNotNullOfOrNull { format ->
            runCatching { LocalDate.parse(token.value, format) }.getOrNull()
        } }
        if (dates.any { it == null }) return null
        return dates.distinct().singleOrNull()
    }

    private val timeToken = Regex("(?i)(?<![0-9:])([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)(?:\\s*(AM|PM))?(?![A-Za-z0-9:])")
    fun time(body: String): LocalTime? {
        val tokens = timeToken.findAll(body).toList()
        if (tokens.isEmpty()) return null
        val times = tokens.map { token ->
            val amPm = token.groupValues[2]
            val pattern = (if (amPm.isEmpty()) "H" else "h") + (if (token.groupValues[1].count { it == ':' } == 2) ":mm:ss" else ":mm") + (if (amPm.isEmpty()) "" else " a")
            val value = token.groupValues[1] + if (amPm.isEmpty()) "" else " ${amPm.uppercase(Locale.ROOT)}"
            runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
        }
        return if (times.any { it == null }) null else times.distinct().singleOrNull()
    }

    private val referenceToken = Regex("(?i)\\b(?:UTR|UPI\\s+ref(?:erence)?|ref(?:erence)?(?:\\s*no\\.?)?|txn(?:\\s*id)?)\\s*[:#.-]?\\s*([A-Z0-9]{6,32})(?![A-Z0-9])")
    fun reference(body: String): String? = referenceToken.findAll(body).map { it.groupValues[1].uppercase(Locale.ROOT) }
        .filter { value -> value.any { it.isDigit() } }.distinct().toList().singleOrNull()

    fun sanitizedDescription(body: String): String = body
        .replace(Regex("(?<![0-9])[0-9]{5,}(?![0-9])")) { "…" + it.value.takeLast(4) }.take(1200)
}
