package com.piyush.vault.domain.model

import java.time.LocalDate

enum class LedgerType { ASSET, LIABILITY, EXPENSE, INCOME, EQUITY }
enum class AccountKind { BANK, CASH, WALLET, CREDIT_CARD }
enum class AccountOrigin { USER_ENTERED, SMS_SUGGESTED, SYSTEM }
enum class SystemAccountRole(val type: LedgerType) {
    EXPENSES(LedgerType.EXPENSE), INCOME(LedgerType.INCOME),
    OPENING_EQUITY(LedgerType.EQUITY), SUSPENSE(LedgerType.EQUITY),
    LEGACY_UNASSIGNED(LedgerType.ASSET), UNALLOCATED_CASH(LedgerType.ASSET);

    fun id(currency: String) = "system:${name}:$currency"
}

data class Account(
    val id: String,
    val displayName: String,
    val ledgerType: LedgerType,
    val accountKind: AccountKind?,
    val currencyCode: String = "INR",
    val systemRole: SystemAccountRole? = null,
    val institutionLabel: String? = null,
    val lastFour: String? = null,
    val origin: AccountOrigin = AccountOrigin.USER_ENTERED,
    val isArchived: Boolean = false,
    val openingBalanceMinor: Long? = null,
    val openingBalanceDate: LocalDate? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val isUserAccount: Boolean get() = systemRole == null
}

data class AccountBalance(val account: Account, val balanceMinor: Long) {
    val hasOpeningBalance: Boolean get() = account.openingBalanceMinor != null
}

data class AccountDraft(
    val id: String? = null,
    val displayName: String,
    val kind: AccountKind,
    val institutionLabel: String? = null,
    val lastFour: String? = null,
    val openingBalanceMinor: Long? = null,
    val openingBalanceDate: LocalDate? = null,
)
