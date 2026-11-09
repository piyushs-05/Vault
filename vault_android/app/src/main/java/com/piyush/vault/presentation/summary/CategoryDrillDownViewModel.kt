package com.piyush.vault.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.usecase.ObserveTransactionsUseCase
import com.piyush.vault.presentation.transactions.TransactionsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CategoryDrillDownViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow<TransactionFilter?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = filter.flatMapLatest { currentFilter ->
        if (currentFilter == null) {
            kotlinx.coroutines.flow.flowOf(TransactionsUiState())
        } else {
            observeTransactions(currentFilter)
                .map { rows -> TransactionsUiState(isLoading = false, transactions = rows, filter = currentFilter) }
                .catch {
                    emit(TransactionsUiState(isLoading = false, filter = currentFilter, errorMessage = "Couldn't load these transactions."))
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )

    fun show(filter: TransactionFilter) {
        this.filter.value = filter
    }
}
