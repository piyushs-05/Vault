package com.piyush.vault.presentation.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface TransactionFormEvent { data object Saved : TransactionFormEvent }

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val getTransaction: GetTransactionUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionFormUiState(
        transactionId = saved["draftId"],amountText = saved["amount"] ?: "", description = saved["description"] ?: "",
        kind = saved.get<String>("kind")?.let { EventKind.valueOf(it) } ?: EventKind.EXPENSE,
        direction = saved.get<String>("direction")?.let { TransactionDirection.valueOf(it) } ?: TransactionDirection.EXPENSE,
        accountId = saved["account"],destinationAccountId = saved["destination"],categoryId = saved["category"],
        occurredDate = saved.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        occurredTime = saved.get<String>("time")?.let(LocalTime::parse),userNote = saved["note"] ?: "",
        originalEventId = saved["original"],revision = saved["revision"],currencyCode = saved["currency"] ?: "INR",
    ))
    val uiState = _uiState.asStateFlow()
    private val channel = Channel<TransactionFormEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()
    init {
        viewModelScope.launch { observeCategories().catch { emit(emptyList()) }.collect { list -> _uiState.update { it.copy(categories = list) } } }
        viewModelScope.launch { observeAccounts().catch { emit(emptyList()) }.collect { list -> _uiState.update { it.copy(accounts = list.map { row -> row.account }.filter { a -> a.isUserAccount }) } } }
        viewModelScope.launch { observeTransactions(TransactionFilter()).catch { emit(emptyList()) }.collect { list -> _uiState.update { it.copy(originals = list.map { row -> row.transaction }) } } }
    }
    fun startNew() { if (saved.get<Boolean>("initialized") == true) return; saved["initialized"] = true; persist() }
    fun startEditing(id: String) {
        if (saved.get<Boolean>("initialized") == true && _uiState.value.transactionId == id) return
        _uiState.update { it.copy(isLoading = true,transactionId = id) }
        viewModelScope.launch {
            try {
                val t = requireNotNull(getTransaction(id)) { "Transaction not found." }
                _uiState.update { it.copy(isLoading = false,transactionId = t.id,amountText = minorUnitsText(t.amountMinor),description = t.description,
                    direction = t.direction,kind = t.kind,accountId = t.accountId,destinationAccountId = t.destinationAccountId,
                    categoryId = t.categoryId,occurredDate = t.occurredDate,occurredTime = t.occurredTime,userNote = t.userNote.orEmpty(),
                    originalEventId = t.originalEventId,revision = t.revision,currencyCode = t.currencyCode) }
                saved["initialized"] = true
                persist()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(isLoading = false,errorMessage = "Couldn't load this transaction.") } }
        }
    }
    private fun update(change: TransactionFormUiState.()->TransactionFormUiState) { _uiState.update { change(it).copy(errorMessage = null) }; persist() }
    fun updateAmount(value: String) = update { copy(amountText = value) }
    fun updateDescription(value: String) = update { copy(description = value) }
    fun updateNote(value: String) = update { copy(userNote = value) }
    fun updateCategory(id: String?) = update { copy(categoryId = id) }
    fun updateAccount(id: String?) = update { copy(accountId = id) }
    fun updateDestination(id: String?) = update { copy(destinationAccountId = id) }
    fun updateDate(date: LocalDate) = update { copy(occurredDate = date) }
    fun updateTime(time: LocalTime?) = update { copy(occurredTime = time) }
    fun updateDirection(direction: TransactionDirection) = update { copy(direction = direction) }
    fun updateKind(kind: EventKind) = update {
        copy(kind = kind,categoryId = null,originalEventId = null,destinationAccountId = null,
            direction = if (kind in listOf(EventKind.INCOME,EventKind.REFUND)) TransactionDirection.INCOME else TransactionDirection.EXPENSE)
    }
    fun updateOriginal(id: String?) = update {
        val original = originals.firstOrNull { it.id == id }
        copy(originalEventId = id,categoryId = if (kind == EventKind.REFUND) original?.categoryId else null,
            amountText = if (kind == EventKind.REVERSAL && original != null) minorUnitsText(original.amountMinor) else amountText,
            accountId = if (kind == EventKind.REVERSAL) original?.accountId else accountId)
    }
    fun save() {
        val s = _uiState.value
        if (s.isSaving || s.isLoading) return
        _uiState.update { it.copy(isSaving = true,errorMessage = null) }
        viewModelScope.launch {
            try {
                saveTransaction(TransactionDraft(parseMinorUnits(s.amountText),s.currencyCode,s.direction,s.description,
                    if (s.kind.hasCategory) s.categoryId else null,s.occurredDate,s.occurredTime,s.transactionId,
                    s.kind,s.accountId,s.destinationAccountId,s.userNote,s.originalEventId,s.revision))
                channel.send(TransactionFormEvent.Saved)
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _uiState.update { it.copy(errorMessage = e.message) } }
            catch (_: Exception) { _uiState.update { it.copy(errorMessage = "Couldn't save. Reopen the form and try again.") } }
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }
    private fun persist() {
        val s = _uiState.value
        saved["draftId"] = s.transactionId; saved["amount"] = s.amountText; saved["description"] = s.description
        saved["kind"] = s.kind.name; saved["direction"] = s.direction.name; saved["account"] = s.accountId
        saved["destination"] = s.destinationAccountId; saved["category"] = s.categoryId
        saved["date"] = s.occurredDate.toString(); saved["time"] = s.occurredTime?.toString(); saved["note"] = s.userNote
        saved["original"] = s.originalEventId; saved["revision"] = s.revision; saved["currency"] = s.currencyCode
    }
}
