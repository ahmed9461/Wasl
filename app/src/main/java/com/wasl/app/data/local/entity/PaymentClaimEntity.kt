package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_claims",
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
        Index(value = ["debt_id", "claimed_at"]),
        Index(value = ["debt_id", "status"]),
        Index(value = ["status", "follow_up_date_epoch_day"]),
    ],
)
data class PaymentClaimEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "create_command_id")
    val createCommandId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "claimed_at")
    val claimedAt: Long,
    @ColumnInfo(name = "follow_up_kind")
    val followUpKind: String,
    @ColumnInfo(name = "follow_up_date_epoch_day")
    val followUpDateEpochDay: Long?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "status")
    val status: String,
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
