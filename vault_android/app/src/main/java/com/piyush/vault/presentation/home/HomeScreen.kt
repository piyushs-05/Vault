package com.piyush.vault.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.piyush.vault.presentation.ui.theme.VaultOutflow
import com.piyush.vault.presentation.ui.theme.VaultIncome
import com.piyush.vault.presentation.ui.theme.VaultTransfer
import com.piyush.vault.domain.model.AccountKind
import com.piyush.vault.domain.model.LedgerType
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.minorUnitsText
import com.piyush.vault.presentation.accounts.AccountsUiState
import com.piyush.vault.presentation.summary.PeriodSummaryUiState
import com.piyush.vault.presentation.transactions.TransactionsUiState
import com.piyush.vault.presentation.ui.components.SpendingDonut
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

@Composable
fun HomeScreen(
    transactions: TransactionsUiState,
    accounts: AccountsUiState,
    summary: PeriodSummaryUiState,
    onAddTransaction: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenCategories: () -> Unit,
    onImportCsv: () -> Unit,
    onOpenSms: () -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.forLanguageTag("en-IN") }
    val month = summary.month.month.getDisplayName(TextStyle.FULL, locale)
    val recent = transactions.transactions.take(3)
    val uncategorizedCount = transactions.transactions.count { it.transaction.kind.hasCategory && it.categoryName == null }
    val rupeeAssets = accounts.accounts.filter {
        val account = it.account
        account.isUserAccount && !account.isArchived && account.currencyCode == "INR" &&
            account.ledgerType == LedgerType.ASSET && account.accountKind in setOf(AccountKind.BANK, AccountKind.CASH, AccountKind.WALLET)
    }
    val rupeeLiabilities = accounts.accounts.filter {
        it.account.isUserAccount && !it.account.isArchived && it.account.currencyCode == "INR" &&
            it.account.ledgerType == LedgerType.LIABILITY
    }
    val recordedCash = rupeeAssets.sumOf { it.balanceMinor }
    val recordedOwed = rupeeLiabilities.sumOf { -it.balanceMinor }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 17.dp, top = 8.dp, end = 17.dp, bottom = 23.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Good morning", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("A simple place to understand your day-to-day money.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (transactions.isLoading || accounts.isLoading || summary.isLoading) {
            item {
                Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else if (transactions.errorMessage != null) {
            item {
                Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your activity couldn't load", style = MaterialTheme.typography.titleMedium)
                        Text(transactions.errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        OutlinedButton(onClick = onOpenActivity) { Text("Open activity") }
                    }
                }
            }
        } else if (transactions.transactions.isEmpty()) {
            item {
                EmptyHome(
                    onAddTransaction = onAddTransaction,
                    onImportCsv = onImportCsv,
                    onOpenAccounts = onOpenAccounts,
                    onOpenSms = onOpenSms,
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Available balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .86f))
                            Text("INR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f))
                        }
                        Text(formatMoney(recordedCash, "INR", locale), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            if (rupeeAssets.isEmpty()) "Add an account to see recorded balances" else "Across ${rupeeAssets.size} cash & bank ${if (rupeeAssets.size == 1) "account" else "accounts"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
                        )
                        if (rupeeLiabilities.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Credit card owed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                                    Text(formatMoney(recordedOwed, "INR", locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Spent in $month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = onOpenSummary) { Text("This month⌄") }
                        }
                        if (summary.errorMessage != null) {
                            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(17.dp), colors = VaultCardColors()) {
                                Text("Monthly totals are unavailable right now.", Modifier.padding(13.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                    SpendingDonut(Modifier.size(56.dp))
                                    Column {
                                        Text(formatMoney(summary.totals.expensesMinor, "INR", locale), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("${transactions.transactions.size} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Income ${formatMoney(summary.totals.incomeMinor, "INR", locale)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onOpenActivity, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                            Text("See all  →", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                        Column(Modifier.padding(horizontal = 11.dp)) {
                            recent.forEachIndexed { index, row ->
                                val transaction = row.transaction
                                val isMovement = transaction.kind.name == "TRANSFER" || transaction.kind.name == "CASH_WITHDRAWAL"
                                val amountColor = when {
                                    isMovement -> VaultTransfer
                                    transaction.direction == TransactionDirection.INCOME -> VaultIncome
                                    else -> VaultOutflow
                                }
                                Column(Modifier.fillMaxWidth().clickable { onTransactionClick(transaction.id) }) {
                                    Row(
                                        Modifier.fillMaxWidth().height(54.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        Surface(Modifier.size(32.dp), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    recentActivityIcon(transaction.kind.name, transaction.direction.name, row.categoryName),
                                                    color = amountColor,
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                            }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(transaction.description, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(row.categoryName ?: if (transaction.kind.hasCategory) "Uncategorized" else transaction.kind.label, transaction.accountName ?: "Unassigned history")
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val amountLabel = when {
                                            isMovement -> formatMoney(transaction.amountMinor, transaction.currencyCode, locale)
                                            transaction.direction == TransactionDirection.INCOME -> "+ ${formatMoney(transaction.amountMinor, transaction.currencyCode, locale)}"
                                            else -> "− ${formatMoney(transaction.amountMinor, transaction.currencyCode, locale)}"
                                        }
                                        Text(amountLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = amountColor)
                                    }
                                    if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (transactions.transactions.any { it.transaction.kind.hasCategory && it.categoryName == null }) {
                item {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenCategories), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF8F3E5))) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("◈", color = androidx.compose.ui.graphics.Color(0xFF6A592C))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$uncategorizedCount entries need a category", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color(0xFF6A592C))
                                Text("Label them to get a clearer spending picture.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF6A592C))
                            }
                            Text("›", color = androidx.compose.ui.graphics.Color(0xFF6A592C))
                        }
                    }
                }
            }
            item { Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("＋  Add transaction") } }
        }
    }
}

private fun recentActivityIcon(kind: String, direction: String, categoryName: String?): String {
    if (kind == "TRANSFER" || kind == "CASH_WITHDRAWAL") return "⇄"
    if (kind == "REFUND") return "↶"
    if (kind == "REVERSAL") return "↺"

    val category = categoryName.orEmpty().lowercase()
    return when {
        "grocer" in category || "market" in category -> "🛒"
        "food" in category || "dining" in category || "coffee" in category -> "☕"
        "transport" in category || "travel" in category -> "↗"
        direction == TransactionDirection.INCOME.name -> "↙"
        else -> "•"
    }
}

@Composable
private fun EmptyHome(
    onAddTransaction: () -> Unit,
    onImportCsv: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSms: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        SpendingDonut(Modifier.size(105.dp), centerText = "₹")
        Text("Start with one payment", style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            "Add something you spent today. As you record activity, Vault will help you see where the small payments add up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("＋  Add your first transaction")
        }
        OutlinedButton(onClick = onImportCsv, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("Import a CSV statement")
        }
        Text(
            "No bank connection needed. Your ledger stays on this device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Set up accounts later", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenAccounts) { Text("Learn more") }
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = VaultCardColors(),
        ) {
            Text(
                "You can begin without an account. Add one when you want to see balances or know where money is held.",
                Modifier.padding(13.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            Modifier.fillMaxWidth().clickable(onClick = onOpenSms),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Optional SMS suggestions", style = MaterialTheme.typography.labelLarge)
                Text("Review before saving  ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MoreScreen(
    onCategories: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onSms: () -> Unit,
    onAccounts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Manage Vault", style = MaterialTheme.typography.headlineSmall)
            Text("Tools for keeping your ledger useful.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoreGroup("Transactions") {
            MoreRow("⇧", "Import CSV", onImportCsv)
            MoreRow("⇩", "Export shown activity", onExportCsv)
        }
        MoreGroup("Organize") {
            MoreRow("◈", "Categories", onCategories)
            MoreRow("▣", "Accounts", onAccounts)
        }
        MoreGroup("Optional sources") {
            MoreRow("⌁", "SMS activity", onSms, trailing = "Review first")
            Text("Optional. Alerts need confirmation. Availability depends on app build.", Modifier.padding(start = 2.dp, top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your data stays on this device", style = MaterialTheme.typography.titleSmall)
                Text("Vault does not connect to your bank.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoreGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp)) {
            Text(title.uppercase(), Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MoreRow(icon: String, label: String, onClick: () -> Unit, trailing: String = "›") {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(icon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMoney(amountMinor: Long, currencyCode: String, locale: Locale): String {
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val amount = BigDecimal.valueOf(amountMinor, 2)
    return if (currency == null) "$currencyCode ${minorUnitsText(amountMinor)}" else {
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
    }
}
