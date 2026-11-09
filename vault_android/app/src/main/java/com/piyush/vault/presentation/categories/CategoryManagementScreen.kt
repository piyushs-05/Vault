package com.piyush.vault.presentation.categories

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.presentation.ui.components.ScreenHeader
import com.piyush.vault.presentation.ui.components.VaultTextField
import com.piyush.vault.presentation.ui.theme.VaultCardColors

@Composable
fun CategoryManagementScreen(
    state: CategoryManagementUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onApplicabilityChange: (CategoryApplicability) -> Unit,
    onAddCategory: () -> Unit,
    onArchiveOrRestore: (Category) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 17.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader("Categories", onBack, subtitle = "Keep labels useful for your spending.")
            VaultTextField("New category", state.name, { onNameChange(it.take(40)) }, placeholder = "e.g. Pet care", supportingText = "${state.name.length}/40")
            Text("Applies to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryApplicability.entries.forEach { applicability ->
                    FilterChip(
                        selected = state.appliesTo == applicability,
                        onClick = { onApplicabilityChange(applicability) },
                        label = { Text(applicability.label()) },
                    )
                }
            }
            Button(onClick = onAddCategory, enabled = !state.isSaving && state.name.isNotBlank()) {
                Text(if (state.isSaving) "Adding…" else "Add category")
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.infoMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.categories.isEmpty() -> Text("No categories available.")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text("Active", style = MaterialTheme.typography.titleMedium) }
                    items(state.categories.filterNot { it.isArchived }, key = { it.id }) { category ->
                        CategoryCard(category, onArchiveOrRestore, onRequestDelete)
                    }
                    val archivedCategories = state.categories.filter { it.isArchived }
                    if (archivedCategories.isNotEmpty()) {
                        item { Text("Archived", style = MaterialTheme.typography.titleMedium) }
                        items(archivedCategories, key = { it.id }) { category ->
                            CategoryCard(category, onArchiveOrRestore, onRequestDelete)
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteCategory?.let { category ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Permanently delete ${category.name}?") },
            text = {
                when {
                    state.isPreparingDelete -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text("Checking linked transactions…")
                    }
                    state.transactionsToUncategorize == 0 -> Text(
                        "This category will be deleted. This action cannot be undone.",
                    )
                    else -> {
                        val count = requireNotNull(state.transactionsToUncategorize)
                        Text(
                            if (count == 1) {
                                "1 transaction will become uncategorized. This action cannot be undone."
                            } else {
                                "$count transactions will become uncategorized. This action cannot be undone."
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    enabled = !state.isPreparingDelete && state.transactionsToUncategorize != null,
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onArchiveOrRestore: (Category) -> Unit,
    onRequestDelete: (Category) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(category.appliesTo.label())
                    if (category.isSystem) append(" · Default")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (!category.isArchived || !category.isSystem) {
                    TextButton(onClick = { onArchiveOrRestore(category) }) {
                        Text(if (category.isArchived) "Restore" else "Archive")
                    }
                }
                if (!category.isSystem) {
                    TextButton(onClick = { onRequestDelete(category) }) { Text("Delete") }
                }
            }
        }
    }
}

private fun CategoryApplicability.label() = when (this) {
    CategoryApplicability.EXPENSE -> "Expense"
    CategoryApplicability.INCOME -> "Income"
    CategoryApplicability.BOTH -> "Both"
}
