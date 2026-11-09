package com.piyush.vault.presentation.transactions

import com.piyush.vault.domain.model.Account
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.model.TransactionListItem

data class TransactionsUiState(
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
    val transactions: List<TransactionListItem> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
)
