package com.wasl.app.data.local

import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.DueScheduleSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object DueScheduleAuditCodec {
    private val json = Json

    fun encode(snapshot: DueScheduleSnapshot): String = json.encodeToString(
        StoredDueScheduleSnapshot(
            dueDateEpochDay = snapshot.dueDate?.toEpochDay(),
            reminder = snapshot.dueReminder?.let {
                StoredDueReminder(
                    id = it.id,
                    triggerAtEpochMillis = it.triggerAt.toEpochMilli(),
                    zoneId = it.zoneId.id,
                )
            },
        ),
    )

    fun decode(value: String): DueScheduleSnapshot {
        val stored = json.decodeFromString<StoredDueScheduleSnapshot>(value)
        return DueScheduleSnapshot(
            dueDate = stored.dueDateEpochDay?.let(LocalDate::ofEpochDay),
            dueReminder = stored.reminder?.let {
                DueReminderRequest(
                    id = it.id,
                    triggerAt = Instant.ofEpochMilli(it.triggerAtEpochMillis),
                    zoneId = ZoneId.of(it.zoneId),
                )
            },
        )
    }
}

@Serializable
private data class StoredDueScheduleSnapshot(
    val dueDateEpochDay: Long?,
    val reminder: StoredDueReminder?,
)

@Serializable
private data class StoredDueReminder(
    val id: String,
    val triggerAtEpochMillis: Long,
    val zoneId: String,
)
