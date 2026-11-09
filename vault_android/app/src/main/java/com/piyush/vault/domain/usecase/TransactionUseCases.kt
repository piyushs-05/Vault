package com.piyush.vault.domain.usecase

import com.piyush.vault.domain.model.CategorySpending
import com.piyush.vault.domain.model.PeriodTotals
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionDraft
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.model.TransactionListItem
import com.piyush.vault.domain.model.TransactionSource
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.model.EventKind
import com.piyush.vault.domain.repository.CategoryRepository
import com.piyush.vault.domain.repository.TransactionRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(filter: TransactionFilter = TransactionFilter()): Flow<List<TransactionListItem>> =
        repository.observeTransactions(filter)
}

class GetTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: String): Transaction? = repository.getById(id)
}

class ObservePeriodTotalsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(startDate: LocalDate, endDate: LocalDate): Flow<PeriodTotals> =
        repository.observePeriodTotals(startDate, endDate)
}

class ObserveCategorySpendingUseCase(private val repository: TransactionRepository) {
    operator fun invoke(startDate: LocalDate, endDate: LocalDate): Flow<List<CategorySpending>> =
        repository.observeCategorySpending(startDate, endDate)
}

class SaveTransactionUseCase(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(draft: TransactionDraft) {
        require(draft.amountMinor > 0) { "Amount must be greater than zero." }
        require(draft.description.isNotBlank()) { "Description cannot be blank." }
        require(draft.currencyCode.matches(Regex("[A-Z]{3}"))) {
            "Currency must be a three-letter uppercase code."
        }
        val existingId = draft.id
        val existing: Transaction? = if (existingId == null) {
            null
        } else {
            repository.getById(existingId)
        }
        require(existingId == null || existing != null) { "Transaction no longer exists." }
        require(existing != null || draft.currencyCode == "INR") { "New manual transactions currently support INR." }
        require(draft.expectedRevision == null || existing?.revision == draft.expectedRevision) {
            "This transaction changed. Reopen it before saving."
        }
        require(draft.kind.hasCategory || draft.categoryId == null) { "This movement does not use a category." }
        draft.categoryId?.takeIf { draft.kind != EventKind.REFUND }?.let { categoryId ->
            val category = requireNotNull(categoryRepository.getById(categoryId)) {
                "Selected category no longer exists."
            }
            val requiredApplicability = if (draft.kind == EventKind.INCOME) CategoryApplicability.INCOME else CategoryApplicability.EXPENSE
            require(
                category.appliesTo == CategoryApplicability.BOTH ||
                    category.appliesTo == requiredApplicability,
            ) { "Selected category does not apply to this transaction direction." }
            require(!category.isArchived || existing?.categoryId == category.id) {
                "Archived categories cannot be assigned to new transactions."
            }
        }
        val now = clock.millis()
        repository.save(
            Transaction(
                id = existing?.id ?: draft.id ?: newId(),
                amountMinor = draft.amountMinor,
                currencyCode = draft.currencyCode,
                direction = draft.direction,
                description = draft.description.trim(),
                categoryId = draft.categoryId,
                occurredDate = draft.occurredDate,
                occurredTime = draft.occurredTime,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                source = existing?.source ?: TransactionSource.MANUAL,
                importBatchId = existing?.importBatchId,
                kind = draft.kind,
                accountId = draft.accountId,
                destinationAccountId = draft.destinationAccountId,
                userNote = draft.userNote,
                originalEventId = draft.originalEventId,
                revision = draft.expectedRevision ?: existing?.revision ?: 0,
            ),
        )
    }
}

class DeleteTransactionUseCase(private val repository: TransactionRepository) {
    /** Returns the removed record so the presentation layer can offer an Undo action. */
    suspend operator fun invoke(id: String): Transaction? = repository.deleteAndReturn(id)
}

class UndoDeleteTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(deletedTransaction: Transaction) = repository.restore(deletedTransaction)
}

class ReconcileTransferUseCase(private val repository: com.piyush.vault.domain.repository.TransactionRepository) {
    suspend operator fun invoke(outgoingId: String,incomingId: String) = repository.mergeUnknownTransfer(outgoingId,incomingId)
}
