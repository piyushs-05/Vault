package com.piyush.vault.data.repository

import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.mapper.*
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.repository.TransactionRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class RoomTransactionRepository @Inject constructor(private val database: VaultDatabase, private val store: LedgerStore) : TransactionRepository {
    override fun observeTransactions(filter: TransactionFilter) = database.transactionDao().observeTransactions(
        filter.startDate,filter.endDate,filter.categoryId != null || filter.onlyUncategorized,
        filter.categoryId,filter.direction,filter.descriptionQuery.trim(),filter.accountId,filter.kind,filter.currencyCode,
    ).map { rows -> rows.map { row ->
        val item = row.toListItem()
        if (filter.accountId != null && filter.accountId == row.destinationAccountId)
            item.copy(transaction = item.transaction.copy(direction = TransactionDirection.INCOME)) else item
    } }
    override fun observePeriodTotals(startDate: LocalDate,endDate: LocalDate) = database.transactionDao().observePeriodTotals(startDate,endDate).map { it.toDomain() }
    override fun observeCategorySpending(startDate: LocalDate,endDate: LocalDate) = database.transactionDao().observeCategorySpending(startDate,endDate).map { rows -> rows.map { it.toDomain() } }
    override suspend fun getById(id: String) = database.transactionDao().getById(id)?.toDomain()
    override suspend fun save(transaction: Transaction) = store.save(transaction)
    override suspend fun deleteAndReturn(id: String) = store.delete(id)
    override suspend fun mergeUnknownTransfer(outgoingId: String,incomingId: String) = store.mergeUnknownTransfer(outgoingId,incomingId)
    override suspend fun restore(transaction: Transaction) = store.restore(transaction)
    override suspend fun wasFileImported(fileContentHash: String) = database.importBatchDao().wasFileImported(fileContentHash)
    override suspend fun saveCsvImport(batch: ImportBatch,transactions: List<Transaction>,accounts: List<Account>,categories: List<Category>) = store.importBatch(batch,transactions,accounts,categories)
}
