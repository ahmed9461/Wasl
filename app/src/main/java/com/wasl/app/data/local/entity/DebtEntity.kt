package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["person_id", "opened_at"]),
        Index(value = ["lifecycle_state", "due_date_epoch_day"]),
        Index(value = ["currency_code", "direction"]),
    ],
)
data class DebtEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "person_id")
    val personId: String,
    @ColumnInfo(name = "direction")
    val direction: String,
    @ColumnInfo(name = "original_amount_minor")
    val originalAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "opened_at")
    val openedAt: Long,
    @ColumnInfo(name = "due_date_epoch_day")
    val dueDateEpochDay: Long?,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "lifecycle_state")
    val lifecycleState: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "closed_at")
    val closedAt: Long?,
)
