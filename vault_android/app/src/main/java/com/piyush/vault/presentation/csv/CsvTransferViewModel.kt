package com.piyush.vault.presentation.csv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.repository.CsvCodec
import com.piyush.vault.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

sealed interface CsvTransferEvent { data object Imported : CsvTransferEvent }

@HiltViewModel
class CsvTransferViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val wasFileImported: WasFileImportedUseCase,
    private val saveCsvImport: SaveCsvImportUseCase,
    private val observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val codec: CsvCodec,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState = _uiState.asStateFlow()
    private val channel = Channel<CsvTransferEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()
    private var rawBytes: ByteArray? = null
    private var categoryList: List<Category> = emptyList()
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    init {
        viewModelScope.launch { observeAccounts().catch { emit(emptyList()) }.collect { list -> _uiState.update { it.copy(accounts = list.map { row -> row.account }.filter { a -> a.isUserAccount }) } } }
        viewModelScope.launch { observeCategories().catch { emit(emptyList()) }.collect { categoryList = it } }
    }
    fun loadFile(fileName: String,bytes: ByteArray) {
        loadJob?.cancel(); previewJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = CsvImportUiState(accounts = previous.accounts,accountId = previous.accountId,isLoading = true,fileName = fileName)
            try {
                val table = withContext(Dispatchers.Default) { codec.parse(String(bytes,StandardCharsets.UTF_8)) }
                rawBytes = bytes
                _uiState.update { it.copy(table = table,mapping = codec.defaultMapping(table.headers),fileWasImportedBefore = wasFileImported(sha256(bytes))) }
                refreshPreview()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { _uiState.update { it.copy(isLoading = false,errorMessage = e.message ?: "Couldn't parse this CSV.") } }
        }
    }
    fun updateAccount(id: String?) { if (_uiState.value.isImporting) return; _uiState.update { it.copy(accountId = id) }; refreshPreview() }
    fun updateMapping(mapping: CsvColumnMapping) { if (_uiState.value.isImporting) return; _uiState.update { it.copy(mapping = mapping) }; refreshPreview() }
    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val s = _uiState.value
            val table = s.table ?: return@launch
            _uiState.update { it.copy(isLoading = true,errorMessage = null) }
            try {
                val raw = withContext(Dispatchers.Default) { codec.preview(table,s.mapping,emptySet()) }
                val dates = raw.mapNotNull { it.occurredDate }
                val fingerprints = mutableSetOf<String>()
                if (dates.isNotEmpty()) {
                    val existing = observeTransactions(TransactionFilter(startDate = dates.minOrNull(),endDate = dates.maxOrNull())).first()
                    existing.forEach { row ->
                        val t = row.transaction
                        val key = csvFingerprint(t.occurredDate,t.amountMinor,t.direction,t.description,t.currencyCode)
                        if ("VaultFormat" in table.headers) fingerprints += key+"|"+t.accountId.orEmpty()
                        else if (t.accountId == s.accountId) fingerprints += key
                    }
                }
                val rows = withContext(Dispatchers.Default) { codec.preview(table,s.mapping,fingerprints) }
                _uiState.update { if (it.table !== table || it.mapping != s.mapping || it.accountId != s.accountId) it else it.copy(rows = rows,isLoading = false) }
            } catch(e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(isLoading = false,errorMessage = "Couldn't prepare the preview.") } }
        }
    }
    fun toggleRow(rowNumber: Int,included: Boolean) { _uiState.update { if (it.isImporting) it else it.copy(rows = it.rows.map { row -> if (row.rowNumber == rowNumber && row.errorMessage == null) row.copy(isIncluded = included) else row }) } }
    fun updateRowTime(rowNumber: Int,time: LocalTime?) { _uiState.update { if (it.isImporting) it else it.copy(rows = it.rows.map { row -> if (row.rowNumber == rowNumber && row.errorMessage == null) row.copy(occurredTime = time) else row }) } }
    fun importSelectedRows() {
        val s = _uiState.value
        val selected = s.rows.filter { it.isIncluded && it.errorMessage == null }
        val bytes = rawBytes ?: return
        if (s.isImporting || s.isLoading || selected.isEmpty()) return
        _uiState.update { it.copy(isImporting = true,errorMessage = null) }
        viewModelScope.launch {
            try {
                val batchId = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
                val rowIds = selected.associate { it.rowNumber to UUID.randomUUID().toString() }
                val remap = selected.mapNotNull { row -> row.sourceEventId?.let { it to requireNotNull(rowIds[row.rowNumber]) } }.toMap()
                val categories = mutableListOf<Category>()
                fun category(row: CsvPreviewRow): String? {
                    if (row.kind?.hasCategory != true || row.kind == EventKind.REFUND) return null
                    val name = row.categoryName ?: return null
                    val found = (categoryList + categories).firstOrNull { it.name.equals(name,ignoreCase = true) && !it.isArchived }
                    if (found != null) return found.id
                    return Category(UUID.randomUUID().toString(),name,CategoryApplicability.BOTH,false,false).also { categories += it }.id
                }
                val transactions = selected.map { row ->
                    Transaction(requireNotNull(rowIds[row.rowNumber]),requireNotNull(row.amountMinor),row.currencyCode,requireNotNull(row.direction),row.description,category(row),
                        requireNotNull(row.occurredDate),row.occurredTime,now,now,TransactionSource.CSV,batchId,
                        kind = row.kind ?: if (row.direction == TransactionDirection.EXPENSE) EventKind.EXPENSE else EventKind.INCOME,
                        accountId = if (s.table?.headers?.contains("VaultFormat") == true) row.importedAccount?.id else s.accountId,destinationAccountId = row.importedDestination?.id,userNote = row.note,
                        originalEventId = row.originalEventId?.let { remap[it] ?: it })
                }.sortedBy { when(it.kind) { EventKind.REFUND -> 1; EventKind.REVERSAL -> 2; else -> 0 } }
                val accountDescriptions = selected.flatMap { listOfNotNull(it.importedAccount,it.importedDestination) }
                accountDescriptions.groupBy { it.id }.values.forEach { descriptions -> require(descriptions.distinct().size == 1) { "Conflicting account details in CSV." } }
                saveCsvImport(ImportBatch(batchId,s.fileName ?: "transactions.csv",sha256(bytes),now,transactions.size,s.rows.size-selected.size,s.accountId),
                    transactions,accountDescriptions.distinctBy { it.id },categories)
                channel.send(CsvTransferEvent.Imported)
            } catch(e: CancellationException) { throw e }
            catch(e: IllegalArgumentException) { _uiState.update { it.copy(errorMessage = e.message ?: "Check the selected events and accounts.") } }
            catch (_: Exception) { _uiState.update { it.copy(errorMessage = "Couldn't import. The entire batch was rolled back.") } }
            finally { _uiState.update { it.copy(isImporting = false) } }
        }
    }
    suspend fun createExportCsv(rows: List<TransactionListItem>): String = withContext(Dispatchers.Default) { codec.export(rows,observeAccounts().first().map { it.account }.filter { it.isUserAccount }) }
    fun resetImport() { loadJob?.cancel(); previewJob?.cancel(); rawBytes = null; _uiState.update { CsvImportUiState(accounts = it.accounts) } }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
