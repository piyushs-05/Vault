package com.piyush.vault.domain.usecase

import com.piyush.vault.domain.model.AccountDraft
import com.piyush.vault.domain.repository.AccountRepository

class ObserveAccountsUseCase(private val repository: AccountRepository) {
    operator fun invoke() = repository.observeAccounts()
}
class SaveAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(draft: AccountDraft) = repository.saveAccount(draft)
}
class ArchiveAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(id: String, archived: Boolean) = repository.setArchived(id,archived)
}
class AssignLegacyTransactionsUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(ids: List<String>, accountId: String) = repository.assignLegacyTransactions(ids,accountId)
}
