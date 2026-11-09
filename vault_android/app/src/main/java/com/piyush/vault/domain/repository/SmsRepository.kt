package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    val isAvailable: Boolean
    val settings: Flow<SmsSettings>
    fun observeSource(id: String): Flow<SourceRecord?>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun openPrompt(sourceId: String)
    suspend fun deferPrompt(sourceId: String)
    fun observeCandidates(): Flow<List<SmsCandidate>>
    suspend fun enable(history: Boolean,live: Boolean)
    suspend fun disable()
    suspend fun scanHistory(start: LocalDate,end: LocalDate)
    suspend fun assignAccount(sourceId: String,accountId: String)
    suspend fun confirm(sourceId: String,draft: TransactionDraft)
    suspend fun attach(sourceId: String,eventId: String)
    suspend fun reject(sourceId: String)
    suspend fun recover()
}
