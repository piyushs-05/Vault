package com.piyush.vault.domain.model

data class ImportBatch(
    val id: String,
    val sourceFileName: String,
    val fileContentHash: String,
    val importedAtEpochMillis: Long,
    val acceptedRowCount: Int,
    val rejectedRowCount: Int,
    val accountId: String? = null,
)
