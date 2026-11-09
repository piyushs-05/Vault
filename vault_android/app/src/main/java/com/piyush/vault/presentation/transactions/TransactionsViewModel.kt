package com.piyush.vault.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.Account
import com.piyush.vault.domain.model.EventKind
import com.piyush.vault.domain.usecase.ObserveAccountsUseCase
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.usecase.DeleteTransactionUseCase
import com.piyush.vault.domain.usecase.ObserveCategoriesUseCase
import com.piyush.vault.domain.usecase.ObserveTransactionsUseCase
import com.piyush.vault.domain.usecase.UndoDeleteTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed interface TransactionsEvent {
    data class Deleted(val transaction: Transaction) : TransactionsEvent
    data object DeleteFailed : TransactionsEvent
    data object UndoFailed : TransactionsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val undoDeleteTransaction: UndoDeleteTransactionUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow(TransactionFilter())
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val eventChannel = Channel<TransactionsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch { observeAccounts().catch { emit(emptyList()) }.collect { accounts.value = it.map { row -> row.account } } }
        viewModelScope.launch {
            observeCategories()
                .catch { emit(emptyList()) }
                .collect { categories.value = it }
        }
    }

    val uiState = combine(filter, categories, accounts) { currentFilter, availableCategories, availableAccounts ->
        Triple(currentFilter, availableCategories, availableAccounts)
    }.flatMapLatest { (currentFilter, availableCategories, availableAccounts) ->
            observeTransactions(currentFilter)
                .map { rows ->
                    TransactionsUiState(
                        isLoading = false,
                        transactions = rows,
                        filter = currentFilter,
                        categories = availableCategories,
                        accounts = availableAccounts,
                    )
                }
                .catch {
                    // Avoid exposing SQL or transaction details in UI error text.
                    emit(
                        TransactionsUiState(
                            isLoading = false,
                            filter = currentFilter,
                            categories = availableCategories,
                        accounts = availableAccounts,
                            errorMessage = "Couldn't load transactions.",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(),
        )

    fun updateAccount(id: String?) { filter.value = filter.value.copy(accountId = id) }
    fun updateKind(kind: EventKind?) { filter.value = filter.value.copy(kind = kind) }

    fun updateFilter(newFilter: TransactionFilter) {
        filter.value = newFilter
    }

    fun updateSearchQuery(query: String) {
        filter.value = filter.value.copy(descriptionQuery = query)
    }

    fun updateDirection(direction: TransactionDirection?) {
        filter.value = filter.value.copy(direction = direction)
    }

    fun updateCategory(categoryId: String?, onlyUncategorized: Boolean = false) {
        filter.value = filter.value.copy(categoryId = categoryId, onlyUncategorized = onlyUncategorized)
    }

    fun updateStartDate(date: java.time.LocalDate?) {
        val current = filter.value
        filter.value = current.copy(
            startDate = date,
            endDate = if (date != null && current.endDate != null && date > current.endDate) date else current.endDate,
        )
    }

    fun updateEndDate(date: java.time.LocalDate?) {
        val current = filter.value
        filter.value = current.copy(
            endDate = date,
            startDate = if (date != null && current.startDate != null && date < current.startDate) date else current.startDate,
        )
    }

    fun clearFilters() {
        filter.value = TransactionFilter()
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                val deleted = deleteTransactionUseCase(id)
                if (deleted != null) eventChannel.send(TransactionsEvent.Deleted(deleted))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                eventChannel.send(TransactionsEvent.DeleteFailed)
            }
        }
    }

    suspend fun undoDelete(transaction: Transaction) {
        try {
            undoDeleteTransaction(transaction)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            eventChannel.send(TransactionsEvent.UndoFailed)
        }
    }
}
