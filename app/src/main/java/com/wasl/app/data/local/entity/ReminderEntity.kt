package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [
        Index(
            value = ["subject_type", "subject_id", "reminder_type"],
            unique = true,
        ),
        Index(value = ["status", "trigger_at"]),
        Index(value = ["subject_id"]),
        Index(value = ["platform_request_code"], unique = true),
    ],
)
data class ReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "subject_type")
    val subjectType: String,
    @ColumnInfo(name = "subject_id")
    val subjectId: String,
    @ColumnInfo(name = "reminder_type")
    val reminderType: String,
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,
    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "repeat_rule")
    val repeatRule: String?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "platform_request_code")
    val platformRequestCode: Int?,
    @ColumnInfo(name = "last_failure_code")
    val lastFailureCode: String?,
    @ColumnInfo(name = "delivered_at")
    val deliveredAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
