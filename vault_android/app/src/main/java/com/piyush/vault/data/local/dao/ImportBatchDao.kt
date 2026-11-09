package com.piyush.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.piyush.vault.data.local.entity.ImportBatchEntity

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: ImportBatchEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM import_batches WHERE fileContentHash = :hash)")
    suspend fun wasFileImported(hash: String): Boolean
}
