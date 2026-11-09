package com.piyush.vault.domain.usecase

import com.piyush.vault.domain.model.Account
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.EventKind
import com.piyush.vault.domain.model.ImportBatch
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionSource
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.repository.CategoryRepository
import com.piyush.vault.domain.repository.TransactionRepository

class WasFileImportedUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(fileContentHash: String): Boolean =
        repository.wasFileImported(fileContentHash)
}

class SaveCsvImportUseCase(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(batch: ImportBatch, transactions: List<Transaction>, accounts: List<Account> = emptyList(), categories: List<Category> = emptyList()) {
        require(batch.fileContentHash.isNotBlank()) { "Import file hash cannot be blank." }
        require(batch.acceptedRowCount >= 0 && batch.rejectedRowCount >= 0) {
            "Import row counts cannot be negative."
        }
        require(batch.acceptedRowCount == transactions.size) {
            "Accepted row count must match the transactions being saved."
        }
        require(transactions.all {
            it.source == TransactionSource.CSV &&
                it.importBatchId == batch.id &&
                it.amountMinor > 0 &&
                it.description.isNotBlank() &&
                it.currencyCode.matches(Regex("[A-Z]{3}"))
        }) { "Every imported transaction must be valid and linked to this CSV batch." }
        transactions.forEach { transaction ->
            transaction.categoryId?.takeIf { transaction.kind != EventKind.REFUND }?.let { categoryId ->
                val category = requireNotNull(categories.firstOrNull { it.id == categoryId } ?: categoryRepository.getById(categoryId)) {
                    "An imported transaction refers to a category that no longer exists."
                }
                val requiredApplicability = if (transaction.kind == EventKind.INCOME) CategoryApplicability.INCOME else CategoryApplicability.EXPENSE
                require(!category.isArchived) { "Imported transactions cannot use archived categories." }
                require(
                    category.appliesTo == CategoryApplicability.BOTH ||
                        category.appliesTo == requiredApplicability,
                ) { "An imported transaction uses a category for the wrong direction." }
            }
        }
        repository.saveCsvImport(batch, transactions, accounts, categories)
    }
}
