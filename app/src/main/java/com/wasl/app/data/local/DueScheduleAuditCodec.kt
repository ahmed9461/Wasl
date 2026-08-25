package com.wasl.app.data.local

import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.DueScheduleSnapshot
import com.wasl.app.data.StrongAlarmRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object DueScheduleAuditCodec {
    private val json = Json { ignoreUnknownKeys = false }

    fun encode(snapshot: DueScheduleSnapshot): String = json.encodeToString(
        StoredDueScheduleSnapshot(
            dueDateEpochDay = snapshot.dueDate?.toEpochDay(),
            reminder = snapshot.dueReminder?.toStored(),
            strongAlarm = snapshot.strongAlarm?.toStored(),
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
            strongAlarm = stored.strongAlarm?.let {
                StrongAlarmRequest(
                    id = it.id,
                    triggerAt = Instant.ofEpochMilli(it.triggerAtEpochMillis),
                    zoneId = ZoneId.of(it.zoneId),
                )
            },
        )
    }

    private fun DueReminderRequest.toStored() = StoredReminder(
        id = id,
        triggerAtEpochMillis = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
    )

    private fun StrongAlarmRequest.toStored() = StoredReminder(
        id = id,
        triggerAtEpochMillis = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
    )
}

@Serializable
private data class StoredDueScheduleSnapshot(
    val dueDateEpochDay: Long?,
    val reminder: StoredReminder?,
    val strongAlarm: StoredReminder? = null,
)

@Serializable
private data class StoredReminder(
    val id: String,
    val triggerAtEpochMillis: Long,
    val zoneId: String,
)
