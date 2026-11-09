package com.piyush.vault.presentation.accounts

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.piyush.vault.domain.model.*
import com.piyush.vault.presentation.ui.components.ChoicePicker
import com.piyush.vault.presentation.ui.components.VaultTextField
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import java.time.LocalDate

@Composable
fun AccountsScreen(state: AccountsUiState,onAdd: ()->Unit,onEdit: (String)->Unit,onHistory: (String)->Unit,onArchive: (Account)->Unit,onSelect: (String,Boolean)->Unit,onTarget: (String?)->Unit,onAssign: ()->Unit) {
    val userAccounts = state.accounts.filter { it.account.isUserAccount && !it.account.isArchived }
    val cashAndBank = userAccounts.filter { it.account.ledgerType == LedgerType.ASSET && it.account.accountKind in setOf(AccountKind.BANK, AccountKind.CASH, AccountKind.WALLET) }
    val available = cashAndBank.sumOf { it.balanceMinor }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 17.dp),verticalArrangement = Arrangement.spacedBy(9.dp),contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Accounts", style = MaterialTheme.typography.headlineSmall)
                    Text("See where your money is held or owed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (userAccounts.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("▣", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Accounts are optional", style = MaterialTheme.typography.titleLarge)
                        Text("You can track transactions first. Add a bank, cash, or card account whenever you want balances by account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("＋  Add an account") }
                        Text("No bank credentials or full account numbers needed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                        Column(Modifier.padding(13.dp)) {
                            Text("Cash & bank available", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${minorUnitsText(available)}", style = MaterialTheme.typography.titleLarge)
                            Text("Across ${cashAndBank.size} ${if (cashAndBank.size == 1) "account" else "accounts"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Your accounts", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onAdd) { Text("＋ Add") }
                    }
                }
            }
            if (state.isLoading) item { CircularProgressIndicator() }
            state.error?.let { item { Text(it,color = MaterialTheme.colorScheme.error) } }
            items(state.accounts.filter { it.account.isUserAccount },key = { it.account.id }) { row ->
                val account = row.account
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(34.dp), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text("▣", color = MaterialTheme.colorScheme.primary) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(account.displayName + if (account.isArchived) " (archived)" else "", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(listOfNotNull(account.institutionLabel,account.lastFour?.let { "•• $it" }).joinToString(" · ").ifBlank { account.accountKind?.name.orEmpty() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val balance = if (account.ledgerType == LedgerType.LIABILITY) -row.balanceMinor else row.balanceMinor
                            Text("${account.currencyCode} ${minorUnitsText(balance)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(when {
                            account.ledgerType == LedgerType.LIABILITY -> "Card · amount owed"
                            !row.hasOpeningBalance -> "Recorded movement · no opening balance"
                            else -> "Calculated balance"
                        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { onHistory(account.id) }) { Text("History") }
                            TextButton(onClick = { onEdit(account.id) }) { Text("Edit") }
                            TextButton(onClick = { onArchive(account) },enabled = !state.isSaving) { Text(if (account.isArchived) "Restore" else "Archive") }
                        }
                    }
                }
            }
            if (state.unassigned.isNotEmpty()) {
                item { Text("Unassigned history",style = MaterialTheme.typography.titleMedium) }
                item { Text("Select the records that belong to this account. This changes account attribution, not their expense/income classification.") }
                item { ChoicePicker("Target account",state.assignmentAccountId,listOf<Pair<String?,String>>(null to "Choose account") + state.accounts.map { it.account }.filter { it.isUserAccount && !it.isArchived }.map { it.id to it.displayName },onTarget) }
                items(state.unassigned,key = { "assign:${it.id}" }) { t ->
                    Row { Checkbox(checked = t.id in state.selectedIds,onCheckedChange = { onSelect(t.id,it) }); Text("${t.occurredDate} · ${t.description} · ${minorUnitsText(t.amountMinor)}") }
                }
                item { Button(onClick = onAssign,enabled = state.selectedIds.isNotEmpty() && state.assignmentAccountId != null && !state.isSaving) { Text("Assign ${state.selectedIds.size} selected") } }
            }
        }
    }
}

@Composable
fun AccountFormScreen(state: AccountsUiState,vm: AccountsViewModel,onBack: ()->Unit) {
    val context = LocalContext.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (state.editId == null) "Add account" else "Edit account",style = MaterialTheme.typography.headlineSmall)
                Text("A nickname is enough to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VaultTextField("Account name",state.name,vm::name,placeholder = "e.g. Main savings")
            AccountKindSegments(state.kind, vm::kind, enabled = state.editId == null)
            ChoicePicker("Suggested institutions",state.institution,listOf("" to "Enter manually") + vm.institutions.map { it to it },vm::bank)
            VaultTextField("Institution · optional",state.institution,vm::bank,placeholder = "Bank or provider name")
            VaultTextField("Last four digits · optional",state.lastFour,vm::suffix,placeholder = "••••")
            VaultTextField(if (state.kind == AccountKind.CREDIT_CARD) "Opening balance owed · optional" else "Opening balance · optional",state.opening,vm::opening,placeholder = "₹ 0.00")
            Text("Vault does not ask for bank passwords or full account numbers. Leave the opening balance blank to track movements only.",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Opening date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {
                val date = runCatching { LocalDate.parse(state.openingDate) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context,{ _,y,m,d -> vm.date(LocalDate.of(y,m+1,d).toString()) },date.year,date.monthValue-1,date.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text(state.openingDate) }
            state.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save,enabled = !state.isSaving && !state.isLoading,modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(13.dp)) { Text(if (state.isSaving) "Saving…" else "Save account") }
            OutlinedButton(onClick = onBack,enabled = !state.isSaving,modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(13.dp)) { Text("Skip for now") }
        }
    }
}

@Composable
private fun AccountKindSegments(value: AccountKind, onChange: (AccountKind) -> Unit, enabled: Boolean) {
    val values = listOf(AccountKind.BANK, AccountKind.CASH, AccountKind.WALLET, AccountKind.CREDIT_CARD)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEach { kind ->
            val selected = value == kind
            Box(
                Modifier.weight(1f).background(if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp)).clickable(enabled = enabled) { onChange(kind) }.padding(vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(kind.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
