package com.piyush.vault.data.repository

import androidx.room.withTransaction
import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.mapper.toDomain
import com.piyush.vault.data.mapper.toEntity
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository @Inject constructor(private val database: VaultDatabase) : CategoryRepository {
    private val categoryDao = database.categoryDao()

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAvailableFor(applicability: CategoryApplicability): Flow<List<Category>> =
        categoryDao.observeAvailableFor(applicability).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun save(category: Category) = categoryDao.upsert(category.toEntity())

    override suspend fun seedDefaults(categories: List<Category>) {
        categoryDao.insertDefaults(categories.map { it.toEntity() })
    }

    override suspend fun archive(id: String) = categoryDao.archive(id)

    override suspend fun countTransactions(id: String): Int = categoryDao.countTransactions(id)

    override suspend fun permanentlyDelete(id: String) {
        val category = categoryDao.getById(id) ?: return
        require(!category.isSystem) { "Default categories can be archived but not permanently deleted." }
        database.withTransaction {
            categoryDao.deleteCustomCategory(id)
        }
    }
}
