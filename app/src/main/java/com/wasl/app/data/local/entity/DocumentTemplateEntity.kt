package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_templates",
    indices = [
        Index(value = ["is_default"]),
        Index(value = ["style"]),
    ],
)
data class DocumentTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "style")
    val style: String,
    @ColumnInfo(name = "show_phone")
    val showPhone: Boolean,
    @ColumnInfo(name = "show_footer")
    val showFooter: Boolean,
    @ColumnInfo(name = "show_balance")
    val showBalance: Boolean,
    @ColumnInfo(name = "show_notes")
    val showNotes: Boolean,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
