package com.piyush.vault.presentation.summary

import com.piyush.vault.domain.model.CategorySpending
import com.piyush.vault.domain.model.PeriodTotals
import java.time.YearMonth

data class PeriodSummaryUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val totals: PeriodTotals = PeriodTotals(expensesMinor = 0, incomeMinor = 0),
    val categorySpending: List<CategorySpending> = emptyList(),
    val errorMessage: String? = null,
)
