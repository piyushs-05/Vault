package com.piyush.vault.presentation.csv

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.*
import com.piyush.vault.presentation.ui.components.ChoicePicker
import com.piyush.vault.presentation.ui.components.ScreenHeader
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import java.math.BigDecimal
import java.time.LocalTime

@Composable
fun CsvImportScreen(
    state: CsvImportUiState,
    onBack: () -> Unit,
    onChooseFile: () -> Unit,
    onMappingChange: (CsvColumnMapping) -> Unit,
    onToggleRow: (Int, Boolean) -> Unit,
    onUpdateRowTime: (Int, LocalTime?) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onAccountChange: (String?) -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 17.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader("Import a statement", onBack, subtitle = "Preview and check the rows before they enter your history.")
            }
            item {
                OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
                    Text(state.fileName?.let { "Choose another CSV" } ?: "Choose CSV file")
                }
            }
            if (state.table?.headers?.contains("VaultFormat") != true) {
                item { ChoicePicker("Import into account",state.accountId,listOf<Pair<String?,String>>(null to "Unassigned history") + state.accounts.filter { !it.isArchived }.map { it.id to it.displayName },onAccountChange) }
                item { Text("Signed debits/credits are initially interpreted as expenses/income. Review transfers and unknown movements after import.",style = MaterialTheme.typography.bodySmall) }
            } else {
                item { Text("Vault event CSV: movement kinds and account references are preserved. Linked refunds/reversals require their originals in the selection or already in this database. Review all rows before importing.") }
            }
            state.fileName?.let { fileName ->
                item { Text("Selected file: $fileName", style = MaterialTheme.typography.bodyMedium) }
            }
            if (state.isLoading) {
                item { CircularProgressIndicator() }
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            state.infoMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.fileWasImportedBefore) {
                item {
                    Card {
                        Text(
                            "This exact file was imported before. Likely duplicate rows start unchecked; review them before importing again.",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            val table = state.table
            if (table != null) {
              if ("VaultFormat" !in table.headers) {
                item {
                    Text("Map your columns", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Required: date, description, and either one signed amount column or both debit and credit columns. Day-first dates are assumed for slash-separated dates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CsvColumnPicker("Date column", table.headers, state.mapping.dateColumn) { selected ->
                            onMappingChange(state.mapping.copy(dateColumn = selected))
                        }
                        CsvColumnPicker("Description column", table.headers, state.mapping.descriptionColumn) { selected ->
                            onMappingChange(state.mapping.copy(descriptionColumn = selected))
                        }
                        CsvColumnPicker("Signed amount column", table.headers, state.mapping.amountColumn) { selected ->
                            onMappingChange(
                                state.mapping.copy(
                                    amountColumn = selected,
                                    debitColumn = if (selected == null) state.mapping.debitColumn else null,
                                    creditColumn = if (selected == null) state.mapping.creditColumn else null,
                                ),
                            )
                        }
                        CsvColumnPicker("Debit column (optional pair)", table.headers, state.mapping.debitColumn) { selected ->
                            onMappingChange(state.mapping.copy(amountColumn = null, debitColumn = selected))
                        }
                        CsvColumnPicker("Credit column (optional pair)", table.headers, state.mapping.creditColumn) { selected ->
                            onMappingChange(state.mapping.copy(amountColumn = null, creditColumn = selected))
                        }
                        CsvColumnPicker("Direction/type column (optional)", table.headers, state.mapping.directionColumn) { selected ->
                            onMappingChange(state.mapping.copy(directionColumn = selected))
                        }
                        CsvColumnPicker("Time column (optional)", table.headers, state.mapping.timeColumn) { selected ->
                            onMappingChange(state.mapping.copy(timeColumn = selected))
                        }
                    }
                }
              }
                item {
                    val likelyDuplicates = state.rows.count { it.isLikelyDuplicate }
                    val invalid = state.rows.count { it.errorMessage != null }
                    Text(
                        "${state.importableCount} selected · $likelyDuplicates likely duplicates · $invalid invalid rows",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Duplicate matches are best-effort warnings (date, amount, direction, description), not proof. You can include a match if it is a real separate purchase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.rows, key = { it.rowNumber }) { row ->
                    CsvPreviewCard(row, onToggleRow, onUpdateRowTime)
                }
                item {
                    Button(
                        onClick = onImport,
                        enabled = !state.isImporting && !state.isLoading && state.importableCount > 0,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    ) {
                        Text(if (state.isImporting) "Importing…" else "Import ${state.importableCount} selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvColumnPicker(
    label: String,
    headers: List<String>,
    selectedColumn: Int?,
    onSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedColumn?.let { headers.getOrNull(it) } ?: "Not selected"
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Not selected") }, onClick = { onSelected(null); expanded = false })
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = { onSelected(index); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun CsvPreviewCard(
    row: CsvPreviewRow,
    onToggleRow: (Int, Boolean) -> Unit,
    onUpdateRowTime: (Int, LocalTime?) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = row.isIncluded,
                enabled = row.errorMessage == null,
                onCheckedChange = { onToggleRow(row.rowNumber, it) },
            )
            Spacer(Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                val amount = row.amountMinor?.let { BigDecimal.valueOf(it, 2).setScale(2).toPlainString() }
                val direction = row.kind?.label ?: when (row.direction) {
                    TransactionDirection.EXPENSE -> "Expense"
                    TransactionDirection.INCOME -> "Income"
                    null -> ""
                }
                Text(
                    text = listOfNotNull(row.occurredDate?.toString(), direction, amount?.let { "₹$it" })
                        .joinToString(" · ").ifBlank { "CSV row ${row.rowNumber}" },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    row.errorMessage ?: row.description,
                    color = if (row.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (row.errorMessage == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.occurredTime?.let { "Time: $it" } ?: "Time not provided", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            val time = row.occurredTime ?: LocalTime.now()
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> onUpdateRowTime(row.rowNumber, LocalTime.of(hour, minute)) },
                                time.hour,
                                time.minute,
                                DateFormat.is24HourFormat(context),
                            ).show()
                        }) { Text(if (row.occurredTime == null) "Add time" else "Change time") }
                        if (row.occurredTime != null) {
                            TextButton(onClick = { onUpdateRowTime(row.rowNumber, null) }) { Text("Clear") }
                        }
                    }
                }
                if (row.isLikelyDuplicate) {
                    Text("Likely duplicate · excluded by default", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
