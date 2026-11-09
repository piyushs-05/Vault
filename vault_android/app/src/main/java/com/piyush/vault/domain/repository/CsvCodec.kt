package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.*

interface CsvCodec {
    fun parse(content: String): CsvTable
    fun defaultMapping(headers: List<String>): CsvColumnMapping
    fun preview(table: CsvTable,mapping: CsvColumnMapping,existingFingerprints: Set<String>): List<CsvPreviewRow>
    fun export(rows: List<TransactionListItem>,accounts: List<Account>): String
}
