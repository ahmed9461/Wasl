package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installment_plans",
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
        Index(value = ["command_id"], unique = true),
        Index(value = ["debt_id", "revision_number"], unique = true),
        Index(value = ["debt_id", "status"]),
        Index(value = ["supersedes_plan_id"]),
    ],
)
data class InstallmentPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "supersedes_plan_id")
    val supersedesPlanId: String?,
    @ColumnInfo(name = "superseded_at")
    val supersededAt: Long?,
    @ColumnInfo(name = "superseded_after_sequence")
    val supersededAfterSequence: Long?,
    @ColumnInfo(name = "reason")
    val reason: String?,
)
