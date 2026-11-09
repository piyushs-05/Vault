package com.piyush.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.piyush.vault.data.local.entity.CategoryEntity
import com.piyush.vault.domain.model.CategoryApplicability
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM categories
        WHERE isArchived = 0 AND (appliesTo = :applicability OR appliesTo = 'BOTH')
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeAvailableFor(applicability: CategoryApplicability): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("SELECT COUNT(DISTINCT eventId) FROM ledger_entries WHERE categoryId = :id")
    suspend fun countTransactions(id: String): Int

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun deleteCustomCategory(id: String): Int
}
