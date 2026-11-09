package com.piyush.vault.presentation.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.piyush.vault.domain.model.CategorySpending
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.presentation.transactions.TransactionsUiState
import com.piyush.vault.presentation.ui.components.SpendingDonut
import com.piyush.vault.presentation.ui.components.ScreenHeader
import com.piyush.vault.presentation.ui.theme.VaultIncome
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

@Composable
fun PeriodSummaryScreen(
    state: PeriodSummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCategoryClick: (CategorySpending) -> Unit,
    onAddTransaction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.forLanguageTag("en-IN") }
    val month = state.month.month.getDisplayName(TextStyle.FULL, locale)
    val hasActivity = state.totals.incomeMinor != 0L || state.totals.expensesMinor != 0L || state.totals.refundsMinor != 0L || state.totals.unknownOutflowMinor != 0L
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (hasActivity) "$month summary" else "Insights", style = MaterialTheme.typography.headlineSmall)
                Text(if (hasActivity) "A monthly view of your income and spending." else "Understand patterns as your history grows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasActivity) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPreviousMonth, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("‹ ${state.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, locale)}") }
                Text("$month ${state.month.year}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNextMonth, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("${state.month.plusMonths(1).month.getDisplayName(TextStyle.SHORT, locale)} ›") }
            }
        }
        when {
            state.isLoading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.errorMessage != null -> item { Text(state.errorMessage, color = MaterialTheme.colorScheme.error) }
            !hasActivity -> {
                item { Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { SpendingDonut(Modifier.size(125.dp), centerText = "◔") } }
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your first month starts here", style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Monthly totals and category patterns will show up after you record transactions. You can still browse and edit activity anytime.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Add a transaction") }
                        Text("Transfers stay separate from income and spending.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                item {
                    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                        Column(Modifier.fillMaxWidth().padding(13.dp)) {
                            Text("Income", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMinor(state.totals.incomeMinor, locale), style = MaterialTheme.typography.titleLarge, color = VaultIncome)
                            SummaryStatRow("Gross spending", formatMinor(state.totals.expensesMinor, locale))
                            SummaryStatRow("Refunds", formatMinor(state.totals.refundsMinor, locale), VaultIncome)
                            SummaryStatRow("Net spending", formatMinor(state.totals.expensesMinor - state.totals.refundsMinor, locale), emphasize = true)
                            if (state.totals.unknownOutflowMinor != 0L) {
                                SummaryStatRow("Unknown-purpose outflows", formatMinor(state.totals.unknownOutflowMinor, locale))
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                        Text("Tap a category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.categorySpending.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                            Text("Category patterns will appear after you record expenses.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    item {
                        val maximum = state.categorySpending.maxOf { it.totalMinor }.coerceAtLeast(1L)
                        Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
                                state.categorySpending.forEachIndexed { index, category ->
                                    CategoryBarRow(category, maximum, locale, onClick = { onCategoryClick(category) })
                                    if (index < state.categorySpending.lastIndex) Spacer(Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF8F3E5))) {
                        Text(
                            "Transfers and opening balances do not count as income or spending.",
                            Modifier.padding(11.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFF6A592C),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CategoryBarRow(category: CategorySpending, maximum: Long, locale: Locale, onClick: () -> Unit) {
    val fraction = (category.totalMinor.toFloat() / maximum.toFloat()).coerceIn(.08f, 1f)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(category.categoryName ?: "Uncategorized", Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
        }
        Text(formatMinor(category.totalMinor, locale), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CategoryDrillDownScreen(
    state: TransactionsUiState,
    categoryName: String,
    periodLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.forLanguageTag("en-IN") }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            ScreenHeader(categoryName, onBack, subtitle = periodLabel)
        }
        when {
            state.isLoading -> item { CircularProgressIndicator() }
            state.errorMessage != null -> item { Text(state.errorMessage, color = MaterialTheme.colorScheme.error) }
            state.transactions.isEmpty() -> item { Text("No transactions in this category for the selected month.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> {
                item {
                    val total = state.transactions.sumOf { if (it.transaction.direction == TransactionDirection.EXPENSE) it.transaction.amountMinor else 0L }
                    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                        Column(Modifier.padding(13.dp)) {
                            Text("Net spending", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMinor(total, locale), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                        Column(Modifier.padding(horizontal = 11.dp)) {
                            state.transactions.forEachIndexed { index, item ->
                                val transaction = item.transaction
                                Row(
                                    Modifier.fillMaxWidth().height(54.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(transaction.description, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${transaction.occurredDate} · ${transaction.accountName ?: "Unassigned history"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text((if (transaction.direction == TransactionDirection.INCOME) "+" else "−") + formatMinor(transaction.amountMinor, locale), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                                if (index < state.transactions.lastIndex) androidx.compose.material3.HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFEDF0ED))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinor(amountMinor: Long, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("INR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(amountMinor, 2))
