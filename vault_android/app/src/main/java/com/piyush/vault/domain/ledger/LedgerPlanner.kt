package com.piyush.vault.domain.ledger

import com.piyush.vault.domain.model.*

/** Pure money rules; persistence owns the enclosing transaction and account/category lookups. */
object LedgerPlanner {
    fun entries(transaction: Transaction, account: Account, destination: Account?): List<LedgerEntry> {
        val amount = transaction.amountMinor
        require(amount in 1..MAX_AMOUNT_MINOR) { "Amount is outside the supported range." }
        require(account.currencyCode == transaction.currencyCode) { "Account currency does not match." }
        val kind = transaction.kind
        val (primaryAmount, counterAccount) = when (kind) {
            EventKind.EXPENSE -> -amount to SystemAccountRole.EXPENSES.id(transaction.currencyCode)
            EventKind.INCOME -> amount to SystemAccountRole.INCOME.id(transaction.currencyCode)
            EventKind.REFUND -> amount to SystemAccountRole.EXPENSES.id(transaction.currencyCode)
            EventKind.TRANSFER -> {
                requireNotNull(destination) { "Choose a destination account." }
                require(account.id != destination.id) { "Transfer accounts must be different." }
                require(destination.currencyCode == account.currencyCode) { "Transfer currencies must match." }
                require(destination.isUserAccount) { "Choose a real destination account." }
                -amount to destination.id
            }
            EventKind.CASH_WITHDRAWAL -> {
                require(account.accountKind == AccountKind.BANK) { "Choose a bank account for a withdrawal." }
                -amount to SystemAccountRole.UNALLOCATED_CASH.id(transaction.currencyCode)
            }
            EventKind.UNKNOWN -> (if (transaction.direction == TransactionDirection.EXPENSE) -amount else amount) to
                SystemAccountRole.SUSPENSE.id(transaction.currencyCode)
            EventKind.REVERSAL, EventKind.OPENING_BALANCE ->
                throw IllegalArgumentException("This event needs its dedicated workflow.")
        }
        require(kind.hasCategory || transaction.categoryId == null) { "This movement does not use a category." }
        return listOf(
            LedgerEntry("${transaction.id}:0", transaction.id, account.id, 0, primaryAmount),
            LedgerEntry("${transaction.id}:1", transaction.id, counterAccount, 1, -primaryAmount, transaction.categoryId),
        ).also(::validate)
    }

    fun validate(entries: List<LedgerEntry>) {
        require(entries.size == 2) { "This version supports exactly two entries per event." }
        require(entries.map { it.eventId }.distinct().size == 1) { "Entries must belong to one event." }
        require(entries.map { it.id }.distinct().size == entries.size) { "Entry IDs must be unique." }
        require(entries.map { it.position }.sorted() == entries.indices.toList()) { "Entry positions must be contiguous." }
        require(entries.all { it.amountMinor != 0L && it.amountMinor in -MAX_AMOUNT_MINOR..MAX_AMOUNT_MINOR }) {
            "Entries must contain supported nonzero amounts."
        }
        require(entries.fold(0L) { total, entry -> Math.addExact(total, entry.amountMinor) } == 0L) {
            "Ledger entries must balance."
        }
    }

    // Leaves ample headroom for checked aggregation; paise, not floating point rupees.
    const val MAX_AMOUNT_MINOR = 9_000_000_000_000_000L
}
