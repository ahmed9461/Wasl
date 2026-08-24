package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_promises",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["create_command_id"], unique = true),
        Index(value = ["resolution_command_id"], unique = true),
        Index(value = ["debt_id", "promised_date_epoch_day"]),
        Index(value = ["debt_id", "status"]),
        Index(value = ["status", "promised_date_epoch_day"]),
    ],
)
data class PaymentPromiseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "create_command_id")
    val createCommandId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "promised_date_epoch_day")
    val promisedDateEpochDay: Long,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "resolution_command_id")
    val resolutionCommandId: String?,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long?,
    @ColumnInfo(name = "resolution_note")
    val resolutionNote: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
