package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = LedgerEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["reverses_entry_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["debt_id", "sequence_number"], unique = true),
        Index(value = ["reverses_entry_id"], unique = true),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "debt_id")
    val debtId: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long?,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String?,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long?,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "reverses_entry_id")
    val reversesEntryId: String?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "reason")
    val reason: String?,
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Long,
)
