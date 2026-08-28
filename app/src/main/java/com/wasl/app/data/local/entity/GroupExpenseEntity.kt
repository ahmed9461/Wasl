package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "group_expenses",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["occurred_at"]),
    ],
)
data class GroupExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "direction")
    val direction: String,
    @ColumnInfo(name = "total_amount_minor")
    val totalAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Entity(
    tableName = "group_expense_shares",
    foreignKeys = [
        ForeignKey(
            entity = GroupExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_expense_id"],
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
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["group_expense_id", "sequence_number"], unique = true),
        Index(value = ["group_expense_id", "person_id"], unique = true),
        Index(value = ["debt_id"], unique = true),
        Index(value = ["person_id"]),
    ],
)
data class GroupExpenseShareEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "group_expense_id")
    val groupExpenseId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "person_id")
    val personId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Int,
)

data class GroupExpenseAggregate(
    @Embedded
    val groupExpense: GroupExpenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "group_expense_id",
    )
    val shares: List<GroupExpenseShareEntity>,
)
