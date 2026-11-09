package com.piyush.vault.data.csv

import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.repository.CsvCodec
import javax.inject.Inject

class LocalCsvCodec @Inject constructor() : CsvCodec {
    override fun parse(content: String) = CsvDocumentParser.parse(content)
    override fun defaultMapping(headers: List<String>) = CsvDocumentParser.defaultMapping(headers)
    override fun preview(table: CsvTable,mapping: CsvColumnMapping,existingFingerprints: Set<String>) = CsvDocumentParser.preview(table,mapping,existingFingerprints)
    override fun export(rows: List<TransactionListItem>,accounts: List<Account>) = VaultCsv.export(rows,accounts)
}
