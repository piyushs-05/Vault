package com.piyush.vault.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.piyush.vault.data.local.dao.CategoryDao
import com.piyush.vault.data.local.dao.ImportBatchDao
import com.piyush.vault.data.local.dao.TransactionDao
import com.piyush.vault.data.local.entity.CategoryEntity
import com.piyush.vault.data.local.entity.ImportBatchEntity
import com.piyush.vault.data.local.entity.*
import com.piyush.vault.data.local.dao.LedgerDao

@Database(
    entities = [CategoryEntity::class, ImportBatchEntity::class, AccountEntity::class, FinancialEventEntity::class, LedgerEntryEntity::class, SourceRecordEntity::class, SmsEnvelopeEntity::class],
    views = [TransactionHistoryRow::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun smsDao(): com.piyush.vault.data.local.dao.SmsDao
}
