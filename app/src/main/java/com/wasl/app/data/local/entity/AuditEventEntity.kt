package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["aggregate_id", "aggregate_type", "occurred_at"]),
    ],
)
data class AuditEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "aggregate_type")
    val aggregateType: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "actor")
    val actor: String,
    @ColumnInfo(name = "before_snapshot")
    val beforeSnapshot: String?,
    @ColumnInfo(name = "after_snapshot")
    val afterSnapshot: String?,
    @ColumnInfo(name = "reason")
    val reason: String?,
)
