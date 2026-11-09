package com.piyush.vault.presentation.transactions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.piyush.vault.domain.model.*
import com.piyush.vault.presentation.ui.components.ChoicePicker
import com.piyush.vault.presentation.ui.components.VaultTextField
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.model.TransactionDirection
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionFormScreen(
    state: TransactionFormUiState,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDirectionChange: (TransactionDirection) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onKindChange: (EventKind) -> Unit = {},
    onAccountChange: (String?) -> Unit = {},
    onDestinationChange: (String?) -> Unit = {},
    onOriginalChange: (String?) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-IN")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("en-IN")) }
    val selectedCategory = state.categories.firstOrNull { it.id == state.categoryId }
    val categories = state.categories.filter { category ->
        val applies = category.appliesTo == CategoryApplicability.BOTH ||
            (state.kind != EventKind.INCOME && category.appliesTo == CategoryApplicability.EXPENSE) ||
            (state.kind == EventKind.INCOME && category.appliesTo == CategoryApplicability.INCOME)
        applies && (!category.isArchived || category.id == state.categoryId)
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { contentPadding ->
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (state.isEditing) "Edit transaction" else "Add transaction", style = MaterialTheme.typography.headlineSmall)
            Text("Record a payment, income, or movement.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            MovementSegments(state.kind, onKindChange)
            val otherKinds = listOf(EventKind.CASH_WITHDRAWAL, EventKind.REFUND, EventKind.REVERSAL, EventKind.UNKNOWN)
            ChoicePicker(
                "Other movement types",
                state.kind.takeIf { it in otherKinds },
                listOf<Pair<EventKind?, String>>(null to "Choose a type") + otherKinds.map { kind ->
                    kind to when (kind) {
                        EventKind.REFUND -> "Refund · link to original expense"
                        EventKind.REVERSAL -> "Reversal · link to original"
                        EventKind.UNKNOWN -> "Unknown movement · money in or out"
                        else -> kind.label
                    }
                },
                { selected -> selected?.let(onKindChange) },
            )
            VaultTextField("Amount · ${state.currencyCode}", state.amountText, onAmountChange, placeholder = if (state.currencyCode == "INR") "₹ 0.00" else "0.00", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            VaultTextField("Description", state.description, onDescriptionChange, placeholder = "What was this for?")
            if (state.kind != EventKind.REVERSAL) {
                ChoicePicker("Account",state.accountId,
                    listOf<Pair<String?,String>>(null to "Unassigned history") + state.accounts.filter { !it.isArchived || it.id == state.accountId }.map { it.id to it.displayName },onAccountChange)
            }
            if (state.kind == EventKind.TRANSFER) {
                ChoicePicker("Destination account",state.destinationAccountId,
                    listOf<Pair<String?,String>>(null to "Choose destination") + state.accounts.filter { (!it.isArchived || it.id == state.destinationAccountId) && it.id != state.accountId }.map { it.id to it.displayName },onDestinationChange)
            }
            if (state.kind in listOf(EventKind.REFUND,EventKind.REVERSAL)) {
                ChoicePicker("Original transaction",state.originalEventId,
                    listOf<Pair<String?,String>>(null to "Choose original") + state.originals.filter { it.id != state.transactionId && (state.kind != EventKind.REFUND || it.kind == EventKind.EXPENSE) }.map { it.id to "${it.occurredDate} · ${it.description} · ${minorUnitsText(it.amountMinor)}" },onOriginalChange)
            }
            if (state.kind == EventKind.UNKNOWN) {
                ChoicePicker("Recorded account movement",state.direction, listOf(TransactionDirection.EXPENSE to "Money out",TransactionDirection.INCOME to "Money in"),onDirectionChange)
            }
            if (state.kind.hasCategory && state.kind != EventKind.REFUND) {
            Column {
                Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedCategory?.displayName() ?: "No category")
                }
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("No category") },
                        onClick = { onCategoryChange(null); categoryMenuExpanded = false },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName()) },
                            onClick = { onCategoryChange(category.id); categoryMenuExpanded = false },
                        )
                    }
                }
            }
            }
            VaultTextField("Your note · optional", state.userNote, onNoteChange, placeholder = "Add a private note")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                        state.occurredDate.year,
                        state.occurredDate.monthValue - 1,
                        state.occurredDate.dayOfMonth,
                    ).show()
                }) { Text(state.occurredDate.format(dateFormatter)) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Time · optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                    val initialTime = state.occurredTime ?: LocalTime.now()
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onTimeChange(LocalTime.of(hour, minute)) },
                        initialTime.hour,
                        initialTime.minute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                }) { Text(state.occurredTime?.format(timeFormatter) ?: "Add time") }
                }
            }
            if (state.occurredTime != null) TextButton(onClick = { onTimeChange(null) }) { Text("Clear time") }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text(if (state.isSaving) "Saving…" else "Save transaction") }
            OutlinedButton(onClick = onCancel, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Cancel") }
        }
      }
    }
}

@Composable
private fun MovementSegments(value: EventKind, onChange: (EventKind) -> Unit) {
    val primaryKinds = listOf(EventKind.EXPENSE, EventKind.INCOME, EventKind.TRANSFER)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        primaryKinds.forEach { kind ->
            val selected = value == kind
            Box(
                Modifier.weight(1f).background(if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp)).clickable { onChange(kind) }.padding(vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(kind.label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}

private fun TransactionDirection.label() = when (this) {
    TransactionDirection.EXPENSE -> "Expense"
    TransactionDirection.INCOME -> "Income"
}

private fun Category.displayName() = if (isArchived) "$name (archived)" else name
