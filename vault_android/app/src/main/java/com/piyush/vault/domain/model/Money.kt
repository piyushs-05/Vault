package com.piyush.vault.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import com.piyush.vault.domain.ledger.LedgerPlanner

fun parseMinorUnits(text: String, allowNegative: Boolean = false): Long {
    val amount = try {
        BigDecimal(text.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Enter a number, such as 125.50.")
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Enter an amount with at most two decimal places.")
    }
    require(amount in -LedgerPlanner.MAX_AMOUNT_MINOR..LedgerPlanner.MAX_AMOUNT_MINOR) { "Amount is too large." }
    require(allowNegative || amount > 0) { "Amount must be greater than zero." }
    return amount
}

fun minorUnitsText(amount: Long): String = BigDecimal.valueOf(amount,2).setScale(2).toPlainString()
