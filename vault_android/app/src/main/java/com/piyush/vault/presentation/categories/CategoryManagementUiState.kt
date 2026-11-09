package com.piyush.vault.presentation.categories

import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability

data class CategoryManagementUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val name: String = "",
    val appliesTo: CategoryApplicability = CategoryApplicability.EXPENSE,
    val isSaving: Boolean = false,
    val isPreparingDelete: Boolean = false,
    val pendingDeleteCategory: Category? = null,
    val transactionsToUncategorize: Int? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
