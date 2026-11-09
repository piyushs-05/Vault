package com.piyush.vault.presentation.sms

import androidx.lifecycle.*
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.usecase.*
import com.piyush.vault.presentation.transactions.TransactionFormUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class SmsReviewState(val loading: Boolean = true,val ready: Boolean = false,val message: String? = null,val reason: String = "",val dirty: Boolean = false)

data class SmsUiState(val available: Boolean = false,val settings: SmsSettings = SmsSettings(),
    val candidates: List<SmsCandidate> = emptyList(),val busy: Boolean = false,val error: String? = null)

@HiltViewModel
class SmsViewModel @Inject constructor(
    private val sms: SmsUseCases, accounts: ObserveAccountsUseCase, categories: ObserveCategoriesUseCase,
    transactions: ObserveTransactionsUseCase, private val saved: SavedStateHandle,
    private val reconcile: ReconcileTransferUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SmsUiState(available = sms.isAvailable))
    val state = _state.asStateFlow()
    private val _form = MutableStateFlow(TransactionFormUiState(isLoading = true))
    val form = _form.asStateFlow()
    private val done = Channel<Unit>(Channel.BUFFERED)
    val completed = done.receiveAsFlow()
    private val _review = MutableStateFlow(SmsReviewState())
    val review = _review.asStateFlow()
    private var reviewJob: Job? = null
    private var sourceId: String? = null
    private var directionResolved = false
    init {
        viewModelScope.launch { sms.settings.catch { _state.update { it.copy(error = "Couldn't read SMS settings.") } }.collect { settings -> _state.update { it.copy(settings = settings) } } }
        viewModelScope.launch { sms.candidates().catch { _state.update { it.copy(error = "Couldn't load alerts.") } }.collect { candidates -> _state.update { it.copy(candidates = candidates) } } }
        viewModelScope.launch { accounts().collect { rows -> _form.update { it.copy(accounts = rows.map { a -> a.account }.filter { a -> a.isUserAccount && !a.isArchived }) } } }
        viewModelScope.launch { categories().collect { rows -> _form.update { it.copy(categories = rows) } } }
        viewModelScope.launch { transactions(TransactionFilter()).collect { rows -> _form.update { it.copy(originals = rows.map { row -> row.transaction }) } } }
    }
    fun begin(id: String) {
        if (sourceId == id) return
        sourceId = id
        reviewJob?.cancel()
        directionResolved = false
        _form.update { it.copy(isLoading = true,errorMessage = null) }
        _review.value = SmsReviewState()
        reviewJob = viewModelScope.launch {
            var initialized = false
            sms.source(id).catch { error ->
                if (error is CancellationException) throw error
                _review.value = SmsReviewState(loading = false,message = "Couldn't load this alert. Close and try again.")
                _form.update { it.copy(isLoading = false) }
            }.collect { source ->
                if (sourceId != id) return@collect
                val observation = source?.observation
                if (source == null || source.eventId != null || source.state !in listOf("NEEDS_ACCOUNT","NEEDS_REVIEW") || observation == null) {
                    _review.update { it.copy(loading = false,ready = false,message = if (source == null) "This alert is no longer available." else "This alert has already been handled or SMS review was disabled.") }
                    _form.update { it.copy(isLoading = false) }
                    return@collect
                }
                if (!initialized) {
                    if (saved.get<String>("smsDraftSourceId") != id) {
                        listOf("amount","description","direction","kind","account","date","time","category","destination","original","note","smsDirectionConfirmed","smsDraftDirty")
                            .forEach { saved.remove<Any?>(it) }
                        saved["smsDraftSourceId"] = id
                    }
                    directionResolved = observation.direction != null || saved.get<Boolean>("smsDirectionConfirmed") == true
                    _form.update { it.copy(isLoading = false,errorMessage = null,
                        amountText = saved["amount"] ?: observation.amountMinor?.let(::minorUnitsText).orEmpty(),
                        description = saved["description"] ?: observation.description,
                        direction = saved.get<String>("direction")?.let(TransactionDirection::valueOf) ?: observation.direction ?: TransactionDirection.EXPENSE,
                        kind = saved.get<String>("kind")?.let(EventKind::valueOf) ?: observation.kind,
                        accountId = if (saved.contains("account")) saved["account"] else source.matchedAccountId,
                        occurredDate = saved.get<String>("date")?.let(LocalDate::parse) ?: observation.date,
                        occurredTime = if (saved.contains("time")) saved.get<String>("time")?.let(java.time.LocalTime::parse) else observation.time,
                        currencyCode = observation.currencyCode,categoryId = saved["category"],
                        destinationAccountId = saved["destination"],originalEventId = saved["original"],userNote = saved["note"] ?: "") }
                    initialized = true
                    _review.value = SmsReviewState(loading = false,ready = true,reason = observation.reviewReason,dirty = saved["smsDraftDirty"] ?: false)
                    try { sms.opened(id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* A failed notification cleanup must not prevent review. */ }
                }
            }
        }
    }
    fun change(change: TransactionFormUiState.()->TransactionFormUiState) {
        if (!review.value.ready || form.value.isSaving) return
        saved["smsDraftDirty"] = true
        _review.update { it.copy(dirty = true) }
        _form.update { change(it).copy(errorMessage = null) }
        val f = form.value
        saved["amount"] = f.amountText; saved["description"] = f.description; saved["direction"] = f.direction.name
        saved["kind"] = f.kind.name; saved["account"] = f.accountId; saved["date"] = f.occurredDate.toString(); saved["time"] = f.occurredTime?.toString()
        saved["category"] = f.categoryId; saved["destination"] = f.destinationAccountId; saved["original"] = f.originalEventId; saved["note"] = f.userNote
    }
    fun chooseDirection(direction: TransactionDirection) {
        if (!review.value.ready || form.value.isSaving) return
        directionResolved = true
        saved["smsDirectionConfirmed"] = true
        change { copy(direction = direction) }
    }
    fun chooseKind(kind: EventKind) {
        if (!review.value.ready || form.value.isSaving) return
        // An explicit purpose selection also resolves its money direction. UNKNOWN still
        // requires the direction picker; reversal direction comes from the linked original.
        if (kind != EventKind.UNKNOWN) {
            directionResolved = true
            saved["smsDirectionConfirmed"] = true
        }
        change {
            copy(kind = kind,categoryId = null,originalEventId = null,direction = when (kind) {
                EventKind.INCOME, EventKind.REFUND -> TransactionDirection.INCOME
                EventKind.EXPENSE, EventKind.CASH_WITHDRAWAL, EventKind.TRANSFER -> TransactionDirection.EXPENSE
                else -> direction
            })
        }
    }
    private fun mutate(block: suspend ()->Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true,error = null) }; _form.update { it.copy(isSaving = true,errorMessage = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _state.update { it.copy(error = e.message) }; _form.update { it.copy(errorMessage = e.message) } }
            catch (_: Exception) { _state.update { it.copy(error = "Couldn't update SMS. Please try again.") }; _form.update { it.copy(errorMessage = "Couldn't save this alert.") } }
            finally { _state.update { it.copy(busy = false) }; _form.update { it.copy(isSaving = false) } }
        }
    }
    fun notifications(enabled: Boolean) = mutate { sms.notifications(enabled) }
    fun later() = mutate {
        try { sms.later(requireNotNull(sourceId)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Dismissal must still work if prompt bookkeeping is unavailable. */ }
        done.send(Unit)
    }
    fun ignoreCurrent() = mutate { sms.reject(requireNotNull(sourceId)); done.send(Unit) }
    fun enable(history: Boolean,live: Boolean) = mutate { sms.enable(history,live) }
    fun denied() { _state.update { it.copy(error = "SMS access wasn't granted. Manual entry and CSV import remain available.") } }
    fun disable() = mutate { sms.disable() }
    fun scan(start: String,end: String) = mutate {
        val dates = runCatching { LocalDate.parse(start) to LocalDate.parse(end) }.getOrElse { throw IllegalArgumentException("Enter dates as YYYY-MM-DD.") }
        sms.scan(dates.first,dates.second)
    }
    fun reject(id: String) = mutate { sms.reject(id) }
    fun reconcile(outgoingId: String,incomingId: String) = mutate { reconcile.invoke(outgoingId,incomingId) }
    fun confirm() = mutate {
        val f = form.value
        require(review.value.ready) { "This alert is no longer available for review." }
        require(!f.isLoading) { "Wait for this alert to load." }
        require(directionResolved) { "This alert's direction is uncertain. Explicitly select money in or money out." }
        require(f.accountId != null) { "Choose the account this alert belongs to." }
        sms.confirm(requireNotNull(sourceId),TransactionDraft(parseMinorUnits(f.amountText),f.currencyCode,f.direction,f.description,
            if (f.kind.hasCategory) f.categoryId else null,f.occurredDate,f.occurredTime,kind = f.kind,accountId = f.accountId,
            destinationAccountId = f.destinationAccountId,userNote = f.userNote,originalEventId = f.originalEventId))
        done.send(Unit)
    }
    fun attach(eventId: String) = mutate {
        require(review.value.ready) { "This alert is no longer available for review." }
        sms.assign(requireNotNull(sourceId),requireNotNull(form.value.accountId) { "Choose the alert account first." })
        sms.attach(requireNotNull(sourceId),eventId); done.send(Unit)
    }
}
