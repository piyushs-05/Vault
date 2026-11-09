package com.piyush.vault.presentation.transactions

import android.app.DatePickerDialog
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.model.TransactionListItem
import com.piyush.vault.presentation.categories.CategoryManagementScreen
import com.piyush.vault.presentation.categories.CategoryManagementViewModel
import com.piyush.vault.presentation.csv.CsvImportScreen
import com.piyush.vault.presentation.csv.CsvTransferViewModel
import com.piyush.vault.presentation.summary.CategoryDrillDownScreen
import com.piyush.vault.presentation.summary.CategoryDrillDownViewModel
import com.piyush.vault.presentation.summary.PeriodSummaryScreen
import com.piyush.vault.presentation.summary.PeriodSummaryViewModel
import java.time.format.TextStyle
import java.io.IOException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import com.piyush.vault.domain.model.EventKind
import com.piyush.vault.presentation.ui.components.SpendingDonut
import com.piyush.vault.presentation.ui.components.VaultTextField
import com.piyush.vault.presentation.ui.theme.VaultIncome
import com.piyush.vault.presentation.ui.theme.VaultOutflow
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import com.piyush.vault.presentation.ui.theme.VaultTransfer
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TransactionsScreen(
    uiState: TransactionsUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAdd: () -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {},
    onImportCsv: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onDirectionFilter: (TransactionDirection?) -> Unit = {},
    onCategoryFilter: (String?, Boolean) -> Unit = { _, _ -> },
    onStartDateFilter: (LocalDate?) -> Unit = {},
    onEndDateFilter: (LocalDate?) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onAccountFilter: (String?) -> Unit = {},
    onKindFilter: (EventKind?) -> Unit = {},
) {
    var transactionPendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Activity", style = MaterialTheme.typography.headlineSmall)
                    Text("Search, filter, and review every movement.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                if (uiState.transactions.isEmpty() && uiState.filter == TransactionFilter()) {
                    EmptyActivityFilters(uiState, onSearch, onDirectionFilter)
                } else {
                    TransactionFilters(
                        state = uiState,
                        onSearch = onSearch,
                        onDirectionFilter = onDirectionFilter,
                        onCategoryFilter = onCategoryFilter,
                        onStartDateFilter = onStartDateFilter,
                        onEndDateFilter = onEndDateFilter,
                        onClearFilters = onClearFilters,
                        onAccountFilter = onAccountFilter,
                        onKindFilter = onKindFilter,
                        accounts = uiState.accounts.map { it.id to it.displayName },
                    )
                }
            }
            when {
                uiState.isLoading -> item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                uiState.errorMessage != null -> item { Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error) }
                uiState.transactions.isEmpty() && uiState.filter != TransactionFilter() -> item { EmptyFilterResults(onClearFilters) }
                uiState.transactions.isEmpty() -> item { EmptyTransactionsMessage(onAdd, onImportCsv) }
                else -> {
                    val groups = uiState.transactions.groupBy { it.transaction.occurredDate }
                    var previousMonth: YearMonth? = null
                    groups.forEach { (date, itemsForDate) ->
                        val month = YearMonth.from(date)
                        if (month != previousMonth) {
                            val monthItems = groups.filterKeys { YearMonth.from(it) == month }.values.flatten()
                            item(key = "month:$month") {
                                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", style = MaterialTheme.typography.titleMedium)
                                    if (monthItems.all { it.transaction.currencyCode == "INR" }) {
                                        Text("Spent ${formatAmount(monthItems.filter { it.transaction.direction == TransactionDirection.EXPENSE }.sumOf { it.transaction.amountMinor }, "INR", Locale.forLanguageTag("en-IN"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            previousMonth = month
                        }
                        item(key = "day:$date") {
                            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                val dayLabel = when (date) {
                                    LocalDate.now() -> "Today"
                                    LocalDate.now().minusDays(1) -> "Yesterday"
                                    else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                                }
                                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                if (itemsForDate.all { it.transaction.currencyCode == "INR" }) {
                                    Text("Spent ${formatAmount(itemsForDate.filter { it.transaction.direction == TransactionDirection.EXPENSE }.sumOf { it.transaction.amountMinor }, "INR", Locale.forLanguageTag("en-IN"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        item(key = "rows:$date") {
                            Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                                Column(Modifier.padding(horizontal = 11.dp)) {
                                    itemsForDate.forEachIndexed { index, item ->
                                        TransactionRow(item, onClick = { onTransactionClick(item.transaction.id) }, onDelete = { transactionPendingDelete = item })
                                        if (index < itemsForDate.lastIndex) androidx.compose.material3.HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFEDF0ED))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!uiState.isLoading && uiState.errorMessage == null && uiState.transactions.isNotEmpty()) {
                item { Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) { Text("＋  Add transaction") } }
            }
        }
    }

    transactionPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { transactionPendingDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("This whole event will be removed, including both sides of a transfer. You can undo this for a few seconds after deleting.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionPendingDelete = null
                        onDeleteTransaction(item.transaction.id)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionPendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TransactionFilters(
    state: TransactionsUiState,
    onSearch: (String) -> Unit,
    onDirectionFilter: (TransactionDirection?) -> Unit,
    onCategoryFilter: (String?, Boolean) -> Unit,
    onStartDateFilter: (LocalDate?) -> Unit,
    onEndDateFilter: (LocalDate?) -> Unit,
    onClearFilters: () -> Unit,
    onAccountFilter: (String?) -> Unit,
    onKindFilter: (EventKind?) -> Unit,
    accounts: List<Pair<String, String>>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yy", Locale.forLanguageTag("en-IN")) }
    val categoryValue = state.filter.categoryId ?: if (state.filter.onlyUncategorized) "__uncategorized__" else "__all__"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VaultTextField("", state.filter.descriptionQuery, onSearch, placeholder = "⌕  Search description")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.filter.direction == null, onClick = { onDirectionFilter(null) }, label = { Text("All") })
            FilterChip(selected = state.filter.direction == TransactionDirection.EXPENSE, onClick = { onDirectionFilter(if (state.filter.direction == TransactionDirection.EXPENSE) null else TransactionDirection.EXPENSE) }, label = { Text("Expense") })
            FilterChip(selected = state.filter.direction == TransactionDirection.INCOME, onClick = { onDirectionFilter(if (state.filter.direction == TransactionDirection.INCOME) null else TransactionDirection.INCOME) }, label = { Text("Income") })
            DropdownFilterChip("Movement", state.filter.kind, listOf<Pair<EventKind?, String>>(null to "All movements") + EventKind.entries.filter { it != EventKind.OPENING_BALANCE }.map { it to it.label }, onKindFilter)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownFilterChip("Account", state.filter.accountId, listOf<Pair<String?, String>>(null to "All accounts") + accounts.map { it.first to it.second }, onAccountFilter)
            DropdownFilterChip("Category", categoryValue, listOf<Pair<String, String>>("__all__" to "All categories", "__uncategorized__" to "Uncategorized") + state.categories.map { it.id to it.name }, { value ->
                when (value) {
                    "__all__" -> onCategoryFilter(null, false)
                    "__uncategorized__" -> onCategoryFilter(null, true)
                    else -> onCategoryFilter(value, false)
                }
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val initial = state.filter.startDate ?: state.filter.endDate ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onStartDateFilter(LocalDate.of(year, month + 1, day)) },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(state.filter.startDate?.let { "From ${it.format(dateFormatter)}" } ?: "Dates")
        }
        OutlinedButton(
            onClick = {
                val initial = state.filter.endDate ?: state.filter.startDate ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onEndDateFilter(LocalDate.of(year, month + 1, day)) },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(state.filter.endDate?.let { "To ${it.format(dateFormatter)}" } ?: "Any end date")
        }
        }
        if (state.filter != TransactionFilter()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearFilters) { Text("Clear filters") }
            }
        }
    }
}

@Composable
private fun EmptyActivityFilters(
    state: TransactionsUiState,
    onSearch: (String) -> Unit,
    onDirectionFilter: (TransactionDirection?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VaultTextField("", state.filter.descriptionQuery, onSearch, placeholder = "⌕  Search transactions")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.filter.direction == null, onClick = { onDirectionFilter(null) }, label = { Text("All") })
            FilterChip(selected = state.filter.direction == TransactionDirection.EXPENSE, onClick = { onDirectionFilter(if (state.filter.direction == TransactionDirection.EXPENSE) null else TransactionDirection.EXPENSE) }, label = { Text("Expense") })
            FilterChip(selected = state.filter.direction == TransactionDirection.INCOME, onClick = { onDirectionFilter(if (state.filter.direction == TransactionDirection.INCOME) null else TransactionDirection.INCOME) }, label = { Text("Income") })
        }
    }
}

@Composable
private fun <T> DropdownFilterChip(
    title: String,
    selected: T,
    choices: List<Pair<T, String>>,
    onChoose: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = choices.firstOrNull { it.first == selected }?.second ?: title
    Box {
        FilterChip(selected = selected != null, onClick = { expanded = true }, label = { Text("$label⌄", maxLines = 1, overflow = TextOverflow.Ellipsis) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onChoose(value); expanded = false })
            }
        }
    }
}

@Composable
private fun EmptyFilterResults(onClearFilters: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No matching transactions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try changing your search or filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearFilters) { Text("Clear filters") }
    }
}

@Composable
private fun EmptyTransactionsMessage(onAdd: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpendingDonut(Modifier.size(125.dp), centerText = "↕")
        Text("Nothing recorded yet", style = MaterialTheme.typography.titleLarge)
        Text("Add a transaction or import a CSV to start seeing your activity here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) { Text("Add transaction") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) { Text("Import CSV") }
    }
}

@Composable
private fun TransactionRow(item: TransactionListItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val transaction = item.transaction
    val locale = remember { Locale.forLanguageTag("en-IN") }
    var menuExpanded by remember { mutableStateOf(false) }
    val amount = remember(transaction.amountMinor, transaction.currencyCode, locale) {
        formatAmount(transaction.amountMinor, transaction.currencyCode, locale)
    }
    val signedAmount = when (transaction.kind.name) {
        "TRANSFER" -> "↔ $amount"
        "CASH_WITHDRAWAL" -> "− $amount"
        else -> (if (transaction.direction == TransactionDirection.INCOME) "+ " else "− ") + amount
    }
    val amountColor = when {
        transaction.kind.name == "TRANSFER" -> VaultTransfer
        transaction.direction == TransactionDirection.INCOME -> VaultIncome
        else -> VaultOutflow
    }

    Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text(when (transaction.kind.name) {
                    "TRANSFER", "CASH_WITHDRAWAL" -> "⇄"
                    "REFUND" -> "↶"
                    "REVERSAL" -> "↺"
                    else -> if (transaction.direction == TransactionDirection.INCOME) "↙" else "•"
                }, color = amountColor, style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(transaction.description, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${transaction.kind.label} · ${item.categoryName ?: if (transaction.kind.hasCategory) "Uncategorized" else transaction.accountName ?: "Unassigned history"}${transaction.destinationAccountName?.let { " → $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(signedAmount, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = amountColor, maxLines = 1)
            Box {
                Text("•••", Modifier.clickable { menuExpanded = true }.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onClick() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
}

private fun formatAmount(amountMinor: Long, currencyCode: String, locale: Locale): String {
    val amount = BigDecimal.valueOf(amountMinor, 2)
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    if (currency == null) {
        val numberFormatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$currencyCode ${numberFormatter.format(amount)}"
    }

    return NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}
