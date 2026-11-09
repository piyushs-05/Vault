package com.piyush.vault.presentation.csv

import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.model.Account

data class CsvImportUiState(
    val accounts: List<Account> = emptyList(),
    val accountId: String? = null,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val fileName: String? = null,
    val fileWasImportedBefore: Boolean = false,
    val table: CsvTable? = null,
    val mapping: CsvColumnMapping = CsvColumnMapping(),
    val rows: List<CsvPreviewRow> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val importableCount: Int get() = rows.count { it.isIncluded && it.errorMessage == null }
}
