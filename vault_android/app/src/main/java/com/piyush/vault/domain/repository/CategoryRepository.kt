package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeAvailableFor(applicability: CategoryApplicability): Flow<List<Category>>
    suspend fun getById(id: String): Category?
    suspend fun save(category: Category)
    suspend fun seedDefaults(categories: List<Category>)
    suspend fun archive(id: String)
    suspend fun countTransactions(id: String): Int
    suspend fun permanentlyDelete(id: String)
}
