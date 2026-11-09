package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.Account
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategorySpending
import com.piyush.vault.domain.model.ImportBatch
import com.piyush.vault.domain.model.PeriodTotals
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionDraft
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.model.TransactionListItem
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionListItem>>
    fun observePeriodTotals(startDate: LocalDate, endDate: LocalDate): Flow<PeriodTotals>
    fun observeCategorySpending(startDate: LocalDate, endDate: LocalDate): Flow<List<CategorySpending>>
    suspend fun getById(id: String): Transaction?
    suspend fun save(transaction: Transaction)
    suspend fun deleteAndReturn(id: String): Transaction?
    suspend fun mergeUnknownTransfer(outgoingId: String,incomingId: String)
    suspend fun restore(transaction: Transaction)
    suspend fun wasFileImported(fileContentHash: String): Boolean
    suspend fun saveCsvImport(batch: ImportBatch, transactions: List<Transaction>, accounts: List<Account> = emptyList(), categories: List<Category> = emptyList())
}
