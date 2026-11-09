package com.piyush.vault.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.usecase.ObserveCategorySpendingUseCase
import com.piyush.vault.domain.usecase.ObservePeriodTotalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PeriodSummaryViewModel @Inject constructor(
    private val observePeriodTotals: ObservePeriodTotalsUseCase,
    private val observeCategorySpending: ObserveCategorySpendingUseCase,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = selectedMonth.flatMapLatest { month ->
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        combine(
            observePeriodTotals(start, end),
            observeCategorySpending(start, end),
        ) { totals, categorySpending ->
            PeriodSummaryUiState(
                isLoading = false,
                month = month,
                totals = totals,
                categorySpending = categorySpending,
            )
        }.catch {
            emit(PeriodSummaryUiState(isLoading = false, month = month, errorMessage = "Couldn't load this period summary."))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeriodSummaryUiState(month = selectedMonth.value),
    )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }
}
