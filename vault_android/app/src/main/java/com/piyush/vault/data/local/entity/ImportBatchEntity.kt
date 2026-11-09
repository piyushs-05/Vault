package com.piyush.vault.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "import_batches", indices = [Index(value = ["fileContentHash"]), Index(value = ["accountId"])],
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL)])
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val sourceFileName: String,
    val fileContentHash: String,
    val importedAtEpochMillis: Long,
    val acceptedRowCount: Int,
    val rejectedRowCount: Int,
    val accountId: String? = null,
)
