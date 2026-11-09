package com.piyush.vault.data.repository

import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.local.entity.toDomain
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.repository.AccountRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(private val db: VaultDatabase, private val store: LedgerStore, private val clock: Clock) : AccountRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccounts(): Flow<List<AccountBalance>> = flow {
        while (true) {
            val today = LocalDate.now(clock)
            emit(today)
            val next = today.plusDays(1).atStartOfDay(clock.zone).toInstant()
            delay(Duration.between(clock.instant(),next).toMillis().coerceAtLeast(1L))
        }
    }.flatMapLatest { date -> db.ledgerDao().observeAccounts(date) }
        .map { rows -> rows.map { AccountBalance(it.account.toDomain(),it.balanceMinor) } }
    override suspend fun getAccount(id: String) = db.ledgerDao().account(id)?.toDomain()
    override suspend fun saveAccount(draft: AccountDraft) = store.saveAccount(draft)
    override suspend fun setArchived(id: String, archived: Boolean) = store.archive(id, archived)
    override suspend fun assignLegacyTransactions(ids: List<String>, accountId: String) = store.assign(ids, accountId)
}
