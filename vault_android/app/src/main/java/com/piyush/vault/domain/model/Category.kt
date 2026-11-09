package com.piyush.vault.domain.model

enum class CategoryApplicability {
    EXPENSE,
    INCOME,
    BOTH,
}

data class Category(
    val id: String,
    val name: String,
    val appliesTo: CategoryApplicability,
    val isSystem: Boolean,
    val isArchived: Boolean,
)
