package com.piyush.vault.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.Category
import com.piyush.vault.domain.model.CategoryApplicability
import com.piyush.vault.domain.usecase.ArchiveCategoryUseCase
import com.piyush.vault.domain.usecase.CountCategoryTransactionsUseCase
import com.piyush.vault.domain.usecase.ObserveCategoriesUseCase
import com.piyush.vault.domain.usecase.PermanentlyDeleteCategoryUseCase
import com.piyush.vault.domain.usecase.SaveCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val saveCategory: SaveCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val countCategoryTransactions: CountCategoryTransactionsUseCase,
    private val permanentlyDeleteCategory: PermanentlyDeleteCategoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeCategories()
                .catch { _ ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't load categories.")
                    }
                    emit(emptyList())
                }
                .collect { categories ->
                    _uiState.update { it.copy(isLoading = false, categories = categories) }
                }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null, infoMessage = null) }
    }

    fun updateApplicability(applicability: CategoryApplicability) {
        _uiState.update { it.copy(appliesTo = applicability, errorMessage = null) }
    }

    fun addCategory() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
            try {
                saveCategory(
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = state.name,
                        appliesTo = state.appliesTo,
                        isSystem = false,
                        isArchived = false,
                    ),
                )
                _uiState.update { it.copy(name = "", infoMessage = "Category added.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Check the category name.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't add this category. Please try again.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun archiveOrRestore(category: Category) {
        viewModelScope.launch {
            try {
                if (category.isArchived) {
                    require(!category.isSystem) { "Default categories cannot be restored after being archived." }
                    saveCategory(category.copy(isArchived = false))
                    _uiState.update { it.copy(infoMessage = "Category restored.", errorMessage = null) }
                } else {
                    archiveCategory(category.id)
                    _uiState.update { it.copy(infoMessage = "Category archived.", errorMessage = null) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Couldn't update this category.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't update this category.") }
            }
        }
    }

    fun requestPermanentDelete(category: Category) {
        if (category.isSystem || _uiState.value.isPreparingDelete) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreparingDelete = true,
                    pendingDeleteCategory = category,
                    transactionsToUncategorize = null,
                    errorMessage = null,
                )
            }
            try {
                val count = countCategoryTransactions(category.id)
                _uiState.update { it.copy(transactionsToUncategorize = count) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        pendingDeleteCategory = null,
                        errorMessage = "Couldn't check how many transactions use this category.",
                    )
                }
            } finally {
                _uiState.update { it.copy(isPreparingDelete = false) }
            }
        }
    }

    fun cancelPermanentDelete() {
        _uiState.update { it.copy(pendingDeleteCategory = null, transactionsToUncategorize = null) }
    }

    fun confirmPermanentDelete() {
        val category = _uiState.value.pendingDeleteCategory ?: return
        if (_uiState.value.transactionsToUncategorize == null) return
        viewModelScope.launch {
            try {
                permanentlyDeleteCategory(category.id)
                _uiState.update {
                    it.copy(
                        pendingDeleteCategory = null,
                        transactionsToUncategorize = null,
                        infoMessage = "Category deleted. Its transactions are now uncategorized.",
                        errorMessage = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Couldn't delete this category.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't delete this category.") }
            }
        }
    }
}
