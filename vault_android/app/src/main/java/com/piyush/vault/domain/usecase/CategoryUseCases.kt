package com.piyush.vault.domain.usecase

import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesUseCase(private val repository: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = repository.observeAll()
}

class ObserveAvailableCategoriesUseCase(private val repository: CategoryRepository) {
    operator fun invoke(applicability: CategoryApplicability): Flow<List<Category>> =
        repository.observeAvailableFor(applicability)
}

class SaveCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category) {
        require(!category.isSystem) { "System categories are managed by the app." }
        val normalizedName = category.name.trim()
        require(normalizedName.isNotEmpty()) { "Category name cannot be blank." }
        require(normalizedName.length <= 40) { "Category name must be 40 characters or fewer." }
        repository.save(category.copy(name = normalizedName))
    }
}

class ArchiveCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(id: String) = repository.archive(id)
}

class CountCategoryTransactionsUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(id: String): Int = repository.countTransactions(id)
}

class PermanentlyDeleteCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(id: String) = repository.permanentlyDelete(id)
}

class SeedDefaultCategoriesUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke() = repository.seedDefaults(defaultCategories)

    private val defaultCategories = listOf(
        Category("00000000-0000-0000-0000-000000000001", "Food & dining", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000002", "Groceries", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000003", "Transport", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000004", "Bills & utilities", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000005", "Shopping", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000006", "Health", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000007", "Entertainment", CategoryApplicability.EXPENSE, true, false),
        Category("00000000-0000-0000-0000-000000000008", "Salary", CategoryApplicability.INCOME, true, false),
        Category("00000000-0000-0000-0000-000000000009", "Other income", CategoryApplicability.INCOME, true, false),
    )
}
