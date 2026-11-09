package com.piyush.vault.presentation.accounts

import androidx.lifecycle.*
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<AccountBalance> = emptyList(), val unassigned: List<Transaction> = emptyList(),
    val selectedIds: Set<String> = emptySet(), val assignmentAccountId: String? = null,
    val isLoading: Boolean = true, val isSaving: Boolean = false, val error: String? = null,
    val editId: String? = null, val name: String = "", val kind: AccountKind = AccountKind.BANK,
    val institution: String = "", val lastFour: String = "", val opening: String = "",
    val openingDate: String = LocalDate.now().toString(),
    val currencyCode: String = "INR",
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeAccounts: ObserveAccountsUseCase, observeTransactions: ObserveTransactionsUseCase,
    private val saveAccount: SaveAccountUseCase, private val archiveAccount: ArchiveAccountUseCase,
    private val assign: AssignLegacyTransactionsUseCase, private val saved: SavedStateHandle,
    directory: InstitutionDirectory,
) : ViewModel() {
    val institutions = directory.names()
    private val _state = MutableStateFlow(AccountsUiState(editId = saved["editId"],name = saved["name"] ?: "",
        kind = saved.get<String>("kind")?.let { AccountKind.valueOf(it) } ?: AccountKind.BANK,institution = saved["bank"] ?: "",
        lastFour = saved["suffix"] ?: "",opening = saved["opening"] ?: "",openingDate = saved["date"] ?: LocalDate.now().toString(),currencyCode = saved["currency"] ?: "INR"))
    val state = _state.asStateFlow()
    private val channel = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = channel.receiveAsFlow()
    init {
        viewModelScope.launch { observeAccounts().catch { _state.update { it.copy(isLoading = false,error = "Couldn't load accounts.") }; emit(emptyList()) }.collect { rows -> _state.update { it.copy(accounts = rows,isLoading = false) } } }
        viewModelScope.launch { observeTransactions(TransactionFilter()).catch { emit(emptyList()) }.collect { rows -> _state.update { it.copy(unassigned = rows.map { row -> row.transaction }.filter { t -> t.accountId == null }) } } }
    }
    fun begin(id: String?) {
        if (saved.get<Boolean>("initialized") == true) return
        if (id == null) { saved["initialized"] = true; return }
        // The account stream is already lifecycle-owned by this ViewModel; wait for its first usable value.
        viewModelScope.launch {
            val account = state.map { s -> s.accounts.firstOrNull { it.account.id == id }?.account }.filterNotNull().first()
            update { copy(editId = id,currencyCode = account.currencyCode,name = account.displayName,kind = requireNotNull(account.accountKind),institution = account.institutionLabel.orEmpty(),
                lastFour = account.lastFour.orEmpty(),opening = account.openingBalanceMinor?.let { minorUnitsText(if (account.accountKind == AccountKind.CREDIT_CARD) -it else it) }.orEmpty(),
                openingDate = (account.openingBalanceDate ?: LocalDate.now()).toString()) }
            saved["initialized"] = true
        }
    }
    private fun update(change: AccountsUiState.()->AccountsUiState) {
        _state.update { change(it).copy(error = null) }
        val s = state.value
        saved["editId"] = s.editId; saved["name"] = s.name; saved["kind"] = s.kind.name
        saved["bank"] = s.institution; saved["suffix"] = s.lastFour; saved["opening"] = s.opening; saved["date"] = s.openingDate; saved["currency"] = s.currencyCode
    }
    fun name(value: String) = update { copy(name = value) }
    fun kind(value: AccountKind) = update { copy(kind = value) }
    fun bank(value: String) = update { copy(institution = value) }
    fun suffix(value: String) = update { copy(lastFour = value) }
    fun opening(value: String) = update { copy(opening = value) }
    fun date(value: String) = update { copy(openingDate = value) }
    fun selectAccount(id: String?) { _state.update { it.copy(assignmentAccountId = id) } }
    fun select(id: String, selected: Boolean) { _state.update { it.copy(selectedIds = if (selected) it.selectedIds+id else it.selectedIds-id) } }
    private fun mutate(block: suspend ()->Unit) {
        if (state.value.isSaving) return
        _state.update { it.copy(isSaving = true,error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _state.update { it.copy(error = e.message) } }
            catch (_: Exception) { _state.update { it.copy(error = "Couldn't update accounts. Please try again.") } }
            finally { _state.update { it.copy(isSaving = false) } }
        }
    }
    fun save() = mutate {
        val s = state.value
        val opening = s.opening.takeIf { it.isNotBlank() }?.let { parseMinorUnits(it,allowNegative = true) }
        val signed = opening?.let { if (s.kind == AccountKind.CREDIT_CARD) -it else it }
        saveAccount(AccountDraft(s.editId,s.name,s.kind,s.institution,s.lastFour,signed,opening?.let { LocalDate.parse(s.openingDate) }))
        channel.send(Unit)
    }
    fun archive(account: Account) = mutate { archiveAccount(account.id,!account.isArchived) }
    fun assignSelected() = mutate {
        val s = state.value
        assign(s.selectedIds.toList(),requireNotNull(s.assignmentAccountId) { "Choose the target account." })
        _state.update { it.copy(selectedIds = emptySet()) }
    }
}
