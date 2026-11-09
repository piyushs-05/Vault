package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<AccountBalance>>
    suspend fun getAccount(id: String): Account?
    suspend fun saveAccount(draft: AccountDraft)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun assignLegacyTransactions(ids: List<String>, accountId: String)
}
