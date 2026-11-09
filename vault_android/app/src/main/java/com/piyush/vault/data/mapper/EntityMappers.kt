package com.piyush.vault.data.mapper

import com.piyush.vault.data.local.entity.CategoryEntity
import com.piyush.vault.data.local.entity.CategorySpendingRow
import com.piyush.vault.data.local.entity.ImportBatchEntity
import com.piyush.vault.data.local.entity.PeriodTotalsRow
import com.piyush.vault.data.local.entity.TransactionHistoryRow
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategorySpending
import com.piyush.vault.domain.model.ImportBatch
import com.piyush.vault.domain.model.PeriodTotals
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionListItem

fun TransactionHistoryRow.toDomain(): Transaction = Transaction(
    id = id, amountMinor = amountMinor, currencyCode = currencyCode, direction = direction,
    description = description, categoryId = categoryId, occurredDate = occurredDate,
    occurredTime = occurredTime, createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis, source = source, importBatchId = importBatchId,
    kind = kind, accountId = accountId, accountName = accountName,
    destinationAccountId = destinationAccountId, destinationAccountName = destinationAccountName,
    userNote = userNote, originalEventId = originalEventId, revision = revision,
)

fun TransactionHistoryRow.toListItem(): TransactionListItem = TransactionListItem(toDomain(), categoryName)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    appliesTo = appliesTo,
    isSystem = isSystem,
    isArchived = isArchived,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    appliesTo = appliesTo,
    isSystem = isSystem,
    isArchived = isArchived,
)

fun ImportBatchEntity.toDomain(): ImportBatch = ImportBatch(
    id = id,
    sourceFileName = sourceFileName,
    fileContentHash = fileContentHash,
    importedAtEpochMillis = importedAtEpochMillis,
    acceptedRowCount = acceptedRowCount,
    rejectedRowCount = rejectedRowCount,
    accountId = accountId,
)

fun ImportBatch.toEntity(): ImportBatchEntity = ImportBatchEntity(
    id = id,
    sourceFileName = sourceFileName,
    fileContentHash = fileContentHash,
    importedAtEpochMillis = importedAtEpochMillis,
    acceptedRowCount = acceptedRowCount,
    rejectedRowCount = rejectedRowCount,
    accountId = accountId,
)

fun PeriodTotalsRow.toDomain(): PeriodTotals = PeriodTotals(expensesMinor, incomeMinor, refundsMinor, unknownOutflowMinor)

fun CategorySpendingRow.toDomain(): CategorySpending =
    CategorySpending(categoryId, categoryName, totalMinor)
