package com.piyush.vault.presentation.transactions

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

data class TransactionFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val transactionId: String? = null,
    val amountText: String = "",
    val description: String = "",
    val direction: TransactionDirection = TransactionDirection.EXPENSE,
    val categoryId: String? = null,
    val occurredDate: LocalDate = LocalDate.now(),
    val occurredTime: LocalTime? = null,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val kind: EventKind = EventKind.EXPENSE,
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val userNote: String = "",
    val originalEventId: String? = null,
    val accounts: List<Account> = emptyList(),
    val originals: List<Transaction> = emptyList(),
    val revision: Long? = null,
    val currencyCode: String = "INR",
) { val isEditing: Boolean get() = transactionId != null }
