package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "sequence_number"], unique = true),
        Index(value = ["plan_id", "due_date_epoch_day"]),
        Index(value = ["debt_id", "due_date_epoch_day"]),
    ],
)
data class InstallmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Int,
    @ColumnInfo(name = "due_date_epoch_day")
    val dueDateEpochDay: Long,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
