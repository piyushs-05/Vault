package com.piyush.vault.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.piyush.vault.domain.model.CategoryApplicability

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"]), Index(value = ["isArchived", "appliesTo"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val appliesTo: CategoryApplicability,
    val isSystem: Boolean,
    val isArchived: Boolean,
)
