package com.piyush.vault.domain.usecase

import com.piyush.vault.domain.model.TransactionDraft
import com.piyush.vault.domain.repository.SmsRepository
import java.time.LocalDate

class SmsUseCases(private val repository: SmsRepository) {
    val isAvailable get() = repository.isAvailable
    val settings get() = repository.settings
    fun source(id: String) = repository.observeSource(id)
    suspend fun notifications(enabled: Boolean) = repository.setNotificationsEnabled(enabled)
    suspend fun opened(id: String) = repository.openPrompt(id)
    suspend fun later(id: String) = repository.deferPrompt(id)
    fun candidates() = repository.observeCandidates()
    suspend fun enable(history: Boolean,live: Boolean) = repository.enable(history,live)
    suspend fun disable() = repository.disable()
    suspend fun scan(start: LocalDate,end: LocalDate) = repository.scanHistory(start,end)
    suspend fun assign(sourceId: String,accountId: String) = repository.assignAccount(sourceId,accountId)
    suspend fun confirm(sourceId: String,draft: TransactionDraft) = repository.confirm(sourceId,draft)
    suspend fun attach(sourceId: String,eventId: String) = repository.attach(sourceId,eventId)
    suspend fun reject(sourceId: String) = repository.reject(sourceId)
}
