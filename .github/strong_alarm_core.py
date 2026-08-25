from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))


def replace_span(path, start, end, replacement):
    text = read(path)
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"Start marker not found in {path}: {start!r}")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"End marker not found in {path}: {end!r}")
    write(path, text[:a] + replacement + text[b:])


# Generic reminder model: Schema v6 already supports multiple reminder types.
path = "app/src/main/java/com/wasl/app/data/RepositoryModels.kt"
replace_once(path,
'''data class AccountOverview(
    val person: PersonRecord,
    val ledger: DebtLedger,
    val lifecycleState: DebtLifecycleState,
    val notes: String? = null,
    val closedAt: Instant? = null,
    val dueReminder: ReminderRecord? = null,
    val dueScheduleAuditEvents: List<DueScheduleAuditEvent> = emptyList(),
    val issuedDocuments: List<IssuedDocumentRecord> = emptyList(),
)''',
'''data class AccountOverview(
    val person: PersonRecord,
    val ledger: DebtLedger,
    val lifecycleState: DebtLifecycleState,
    val notes: String? = null,
    val closedAt: Instant? = null,
    val dueReminder: ReminderRecord? = null,
    val strongAlarm: ReminderRecord? = null,
    val dueScheduleAuditEvents: List<DueScheduleAuditEvent> = emptyList(),
    val issuedDocuments: List<IssuedDocumentRecord> = emptyList(),
)''')
replace_once(path,
'''enum class ReminderStatus {
    SCHEDULED,
    DELIVERED,
    BLOCKED_PERMISSION,
    FAILED,
    CANCELLED,
}

data class ReminderRecord(
    val id: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val status: ReminderStatus,
    val lastFailureCode: String? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
    }
}

data class DueReminderRequest(
    val id: String,
    val triggerAt: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
    }
}

data class DueScheduleSnapshot(
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
) {
    init {
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}''',
'''enum class ReminderStatus {
    SCHEDULED,
    DELIVERED,
    BLOCKED_PERMISSION,
    FAILED,
    CANCELLED,
}

enum class ReminderType {
    DUE_DATE,
    STRONG_ALARM,
}

enum class ReminderScheduleType {
    WORK,
    EXACT_ALARM,
}

data class ReminderRecord(
    val id: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val status: ReminderStatus,
    val type: ReminderType = ReminderType.DUE_DATE,
    val scheduleType: ReminderScheduleType = ReminderScheduleType.WORK,
    val platformRequestCode: Int? = null,
    val lastFailureCode: String? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
        require(
            (type == ReminderType.DUE_DATE && scheduleType == ReminderScheduleType.WORK) ||
                (type == ReminderType.STRONG_ALARM &&
                    scheduleType == ReminderScheduleType.EXACT_ALARM),
        ) { "Reminder type and schedule type are incompatible." }
    }
}

data class DueReminderRequest(
    val id: String,
    val triggerAt: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
    }
}

data class StrongAlarmRequest(
    val id: String,
    val triggerAt: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank()) { "Strong alarm ID cannot be blank." }
    }
}

data class DueScheduleSnapshot(
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
    val strongAlarm: StrongAlarmRequest? = null,
) {
    init {
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
        }
    }
}''')
replace_once(path,
'''data class UpdateDueScheduleCommand(
    val commandId: String,
    val auditEventId: String,
    val debtId: DebtId,
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
    val updatedAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(auditEventId.isNotBlank()) { "Audit event ID cannot be blank." }
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}''',
'''data class UpdateDueScheduleCommand(
    val commandId: String,
    val auditEventId: String,
    val debtId: DebtId,
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
    val strongAlarm: StrongAlarmRequest? = null,
    val updatedAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(auditEventId.isNotBlank()) { "Audit event ID cannot be blank." }
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
        }
    }
}''')
replace_once(path,
'''    val debtNotes: String? = null,
    val dueReminder: DueReminderRequest? = null,
) {
    init {
        require(personName.isNotBlank())''',
'''    val debtNotes: String? = null,
    val dueReminder: DueReminderRequest? = null,
    val strongAlarm: StrongAlarmRequest? = null,
) {
    init {
        require(personName.isNotBlank())''')
replace_once(path,
'''        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}

data class CreateDebtForExistingPersonCommand''',
'''        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
        }
    }
}

data class CreateDebtForExistingPersonCommand''')
replace_once(path,
'''    val description: String? = null,
    val debtNotes: String? = null,
    val dueReminder: DueReminderRequest? = null,
) {
    init {
        require(originalAmount.minorUnits > 0L)''',
'''    val description: String? = null,
    val debtNotes: String? = null,
    val dueReminder: DueReminderRequest? = null,
    val strongAlarm: StrongAlarmRequest? = null,
) {
    init {
        require(originalAmount.minorUnits > 0L)''')
replace_once(path,
'''        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}

data class RecordPaymentCommand''',
'''        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
        }
    }
}

data class RecordPaymentCommand''')

write("app/src/main/java/com/wasl/app/data/local/DueScheduleAuditCodec.kt", '''package com.wasl.app.data.local

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
''')

write("app/src/main/java/com/wasl/app/data/local/dao/ReminderDao.kt", '''package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.ReminderEntity

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findById(id: String): ReminderEntity?

    @Query(
        """
        SELECT * FROM reminders
        WHERE subject_type = 'DEBT'
          AND subject_id = :debtId
          AND reminder_type = 'DUE_DATE'
        """,
    )
    suspend fun findDueDateForDebt(debtId: String): ReminderEntity?

    @Query(
        """
        SELECT * FROM reminders
        WHERE subject_type = 'DEBT'
          AND subject_id = :debtId
          AND reminder_type = 'STRONG_ALARM'
        """,
    )
    suspend fun findStrongAlarmForDebt(debtId: String): ReminderEntity?

    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('SCHEDULED', 'BLOCKED_PERMISSION', 'FAILED')
           OR (status = 'DELIVERED' AND reminder_type = 'DUE_DATE')
        ORDER BY trigger_at, id
        """,
    )
    suspend fun findRecoverable(): List<ReminderEntity>

    @Query(
        """
        UPDATE reminders
        SET trigger_at = :triggerAt,
            zone_id = :zoneId,
            status = 'SCHEDULED',
            last_failure_code = NULL,
            delivered_at = NULL,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateSchedule(id: String, triggerAt: Long, zoneId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE reminders
        SET status = :status,
            last_failure_code = :failureCode,
            delivered_at = :deliveredAt,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        failureCode: String?,
        deliveredAt: Long?,
        updatedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun count(): Int
}
''')

path = "app/src/main/java/com/wasl/app/data/local/RoomWaslRepository.kt"
replace_once(path,
'import com.wasl.app.data.ReminderRecord\nimport com.wasl.app.data.ReminderStatus\nimport com.wasl.app.data.ReminderStore\n',
'import com.wasl.app.data.ReminderRecord\nimport com.wasl.app.data.ReminderScheduleType\nimport com.wasl.app.data.ReminderStatus\nimport com.wasl.app.data.ReminderStore\nimport com.wasl.app.data.ReminderType\nimport com.wasl.app.data.StrongAlarmRequest\n')
replace_span(path,
'    override suspend fun updateDueSchedule(\n',
'    private suspend fun requireAccount',
'''    override suspend fun updateDueSchedule(
        command: UpdateDueScheduleCommand,
    ): AccountOverview = database.withTransaction {
        auditEventDao.findByCommandId(command.commandId)?.let { persisted ->
            validateDueScheduleReplay(command, persisted)
            return@withTransaction requireAccount(command.debtId)
        }

        val aggregate = debtDao.findAggregateById(command.debtId.value)
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        val current = toAccountOverview(aggregate)
        require(current.lifecycleState == DebtLifecycleState.ACTIVE) {
            "Only an active debt can change its due schedule."
        }
        require(!current.ledger.balance.isZero) {
            "A settled debt cannot receive a new due schedule."
        }
        require(!command.updatedAt.isBefore(current.ledger.header.openedAt)) {
            "Schedule update cannot predate the debt."
        }

        val existingReminder = reminderDao.findDueDateForDebt(command.debtId.value)
        val existingStrongAlarm = reminderDao.findStrongAlarmForDebt(command.debtId.value)
        val before = DueScheduleSnapshot(
            dueDate = current.ledger.header.dueDate,
            dueReminder = existingReminder?.activeDueRequestOrNull(),
            strongAlarm = existingStrongAlarm?.activeStrongAlarmRequestOrNull(),
        )
        val after = DueScheduleSnapshot(
            dueDate = command.dueDate,
            dueReminder = command.dueReminder,
            strongAlarm = command.strongAlarm,
        )
        require(before != after) { "Due schedule is unchanged." }

        command.dueReminder?.let { requested ->
            existingReminder?.let { existing ->
                require(existing.id == requested.id) {
                    "An existing due reminder must keep its stable ID."
                }
            }
        }
        command.strongAlarm?.let { requested ->
            existingStrongAlarm?.let { existing ->
                require(existing.id == requested.id) {
                    "An existing strong alarm must keep its stable ID."
                }
            }
        }

        check(
            debtDao.updateDueDate(
                id = command.debtId.value,
                dueDateEpochDay = command.dueDate?.toEpochDay(),
                updatedAt = command.updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Debt due date was not updated." }

        persistDueReminderChange(
            debtId = command.debtId,
            existing = existingReminder,
            requested = command.dueReminder,
            updatedAt = command.updatedAt,
        )
        persistStrongAlarmChange(
            debtId = command.debtId,
            existing = existingStrongAlarm,
            requested = command.strongAlarm,
            updatedAt = command.updatedAt,
        )

        auditEventDao.insert(
            AuditEventEntity(
                id = command.auditEventId,
                commandId = command.commandId,
                aggregateType = AuditAggregateType.DEBT.name,
                aggregateId = command.debtId.value,
                eventType = AuditEventType.DUE_SCHEDULE_CHANGED.name,
                occurredAt = command.updatedAt.toEpochMilli(),
                actor = AuditActor.LOCAL_USER.name,
                beforeSnapshot = DueScheduleAuditCodec.encode(before),
                afterSnapshot = DueScheduleAuditCodec.encode(after),
                reason = null,
            ),
        )

        requireAccount(command.debtId)
    }

    private suspend fun persistDueReminderChange(
        debtId: DebtId,
        existing: ReminderEntity?,
        requested: DueReminderRequest?,
        updatedAt: Instant,
    ) {
        when {
            requested == null && existing != null -> cancelReminder(existing.id, updatedAt)
            requested != null && existing == null -> reminderDao.insert(
                requested.toEntity(debtId, updatedAt),
            )
            requested != null && existing != null -> rescheduleReminder(
                existing.id,
                requested.triggerAt,
                requested.zoneId,
                updatedAt,
            )
        }
    }

    private suspend fun persistStrongAlarmChange(
        debtId: DebtId,
        existing: ReminderEntity?,
        requested: StrongAlarmRequest?,
        updatedAt: Instant,
    ) {
        when {
            requested == null && existing != null -> cancelReminder(existing.id, updatedAt)
            requested != null && existing == null -> reminderDao.insert(
                requested.toEntity(debtId, updatedAt),
            )
            requested != null && existing != null -> rescheduleReminder(
                existing.id,
                requested.triggerAt,
                requested.zoneId,
                updatedAt,
            )
        }
    }

    private suspend fun cancelReminder(id: String, updatedAt: Instant) {
        check(
            reminderDao.updateStatus(
                id = id,
                status = ReminderStatus.CANCELLED.name,
                failureCode = null,
                deliveredAt = null,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $id was not cancelled." }
    }

    private suspend fun rescheduleReminder(
        id: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ) {
        check(
            reminderDao.updateSchedule(
                id = id,
                triggerAt = triggerAt.toEpochMilli(),
                zoneId = zoneId.id,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $id was not rescheduled." }
    }

''')
replace_span(path,
'    private suspend fun insertDebtWithReminder(creation: DebtCreation) {\n',
'    private fun validateCreatePersonReplay',
'''    private suspend fun insertDebtWithReminder(creation: DebtCreation) {
        debtDao.insert(
            DebtEntity(
                id = creation.debtId.value,
                personId = creation.personId.value,
                direction = creation.direction.name,
                originalAmountMinor = creation.originalAmount.minorUnits,
                currencyCode = creation.originalAmount.currency.value,
                openedAt = creation.openedAt.toEpochMilli(),
                dueDateEpochDay = creation.dueDate?.toEpochDay(),
                description = creation.description,
                notes = creation.debtNotes,
                lifecycleState = DebtLifecycleState.ACTIVE.name,
                createdAt = creation.createdAt.toEpochMilli(),
                updatedAt = creation.createdAt.toEpochMilli(),
                closedAt = null,
            ),
        )
        creation.dueReminder?.let { reminder ->
            reminderDao.insert(reminder.toEntity(creation.debtId, creation.createdAt))
        }
        creation.strongAlarm?.let { alarm ->
            reminderDao.insert(alarm.toEntity(creation.debtId, creation.createdAt))
        }
    }

''')
replace_span(path,
'    private fun debtCreationMatches(\n',
'    private fun validatePaymentReplay',
'''    private fun debtCreationMatches(
        creation: DebtCreation,
        aggregate: DebtAggregate,
        persisted: AccountOverview,
    ): Boolean {
        val expectedReminder = creation.dueReminder
        val persistedReminder = persisted.dueReminder
        val reminderMatches = when {
            expectedReminder == null -> persistedReminder == null
            persistedReminder == null -> false
            else -> persistedReminder.id == expectedReminder.id &&
                persistedReminder.debtId == creation.debtId &&
                persistedReminder.triggerAt == expectedReminder.triggerAt &&
                persistedReminder.zoneId == expectedReminder.zoneId &&
                persistedReminder.type == ReminderType.DUE_DATE &&
                persistedReminder.scheduleType == ReminderScheduleType.WORK &&
                persistedReminder.createdAt == creation.createdAt
        }
        val expectedStrongAlarm = creation.strongAlarm
        val persistedStrongAlarm = persisted.strongAlarm
        val strongAlarmMatches = when {
            expectedStrongAlarm == null -> persistedStrongAlarm == null
            persistedStrongAlarm == null -> false
            else -> persistedStrongAlarm.id == expectedStrongAlarm.id &&
                persistedStrongAlarm.debtId == creation.debtId &&
                persistedStrongAlarm.triggerAt == expectedStrongAlarm.triggerAt &&
                persistedStrongAlarm.zoneId == expectedStrongAlarm.zoneId &&
                persistedStrongAlarm.type == ReminderType.STRONG_ALARM &&
                persistedStrongAlarm.scheduleType == ReminderScheduleType.EXACT_ALARM &&
                persistedStrongAlarm.createdAt == creation.createdAt
        }
        return persisted.person.id == creation.personId &&
            persisted.ledger.header.direction == creation.direction &&
            persisted.ledger.header.originalAmount == creation.originalAmount &&
            persisted.ledger.header.openedAt == creation.openedAt &&
            persisted.ledger.header.dueDate == creation.dueDate &&
            persisted.ledger.header.description == creation.description &&
            persisted.notes == creation.debtNotes &&
            aggregate.debt.createdAt == creation.createdAt.toEpochMilli() &&
            reminderMatches &&
            strongAlarmMatches
    }

''')
replace_once(path,
'afterSnapshot == DueScheduleSnapshot(command.dueDate, command.dueReminder)',
'afterSnapshot == DueScheduleSnapshot(command.dueDate, command.dueReminder, command.strongAlarm)')
replace_once(path,
'''        val dueReminders = aggregate.reminders.filter {
            it.subjectType == ReminderSubjectType.DEBT.name &&
                it.reminderType == ReminderType.DUE_DATE.name
        }
        check(dueReminders.size <= 1) { "Debt contains duplicate due reminders." }

        return AccountOverview(''',
'''        val dueReminders = aggregate.reminders.filter {
            it.subjectType == ReminderSubjectType.DEBT.name &&
                it.reminderType == ReminderType.DUE_DATE.name
        }
        check(dueReminders.size <= 1) { "Debt contains duplicate due reminders." }
        val strongAlarms = aggregate.reminders.filter {
            it.subjectType == ReminderSubjectType.DEBT.name &&
                it.reminderType == ReminderType.STRONG_ALARM.name
        }
        check(strongAlarms.size <= 1) { "Debt contains duplicate strong alarms." }

        return AccountOverview(''')
replace_once(path,
'''            dueReminder = dueReminders.singleOrNull()?.toRecord(),
            dueScheduleAuditEvents = aggregate.auditEvents''',
'''            dueReminder = dueReminders.singleOrNull()?.toRecord(),
            strongAlarm = strongAlarms.singleOrNull()?.toRecord(),
            dueScheduleAuditEvents = aggregate.auditEvents''')
replace_span(path,
'    private fun ReminderEntity.toRecord(): ReminderRecord {\n',
'    private fun toDomainEntry',
'''    private fun ReminderEntity.toRecord(): ReminderRecord {
        check(subjectType == ReminderSubjectType.DEBT.name) {
            "Unsupported reminder subject type: $subjectType"
        }
        val type = ReminderType.valueOf(reminderType)
        val schedule = ReminderScheduleType.valueOf(scheduleType)
        check(
            (type == ReminderType.DUE_DATE && schedule == ReminderScheduleType.WORK) ||
                (type == ReminderType.STRONG_ALARM && schedule == ReminderScheduleType.EXACT_ALARM),
        ) { "Unsupported reminder type/schedule combination: $reminderType/$scheduleType" }
        return ReminderRecord(
            id = id,
            debtId = DebtId(subjectId),
            triggerAt = Instant.ofEpochMilli(triggerAt),
            zoneId = ZoneId.of(zoneId),
            status = ReminderStatus.valueOf(status),
            type = type,
            scheduleType = schedule,
            platformRequestCode = platformRequestCode,
            lastFailureCode = lastFailureCode,
            deliveredAt = deliveredAt?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun ReminderEntity.activeDueRequestOrNull(): DueReminderRequest? =
        if (status == ReminderStatus.CANCELLED.name) {
            null
        } else {
            check(reminderType == ReminderType.DUE_DATE.name)
            DueReminderRequest(
                id = id,
                triggerAt = Instant.ofEpochMilli(triggerAt),
                zoneId = ZoneId.of(zoneId),
            )
        }

    private fun ReminderEntity.activeStrongAlarmRequestOrNull(): StrongAlarmRequest? =
        if (status == ReminderStatus.CANCELLED.name) {
            null
        } else {
            check(reminderType == ReminderType.STRONG_ALARM.name)
            StrongAlarmRequest(
                id = id,
                triggerAt = Instant.ofEpochMilli(triggerAt),
                zoneId = ZoneId.of(zoneId),
            )
        }

    private fun DueReminderRequest.toEntity(
        debtId: DebtId,
        createdAt: Instant,
    ): ReminderEntity = ReminderEntity(
        id = id,
        subjectType = ReminderSubjectType.DEBT.name,
        subjectId = debtId.value,
        reminderType = ReminderType.DUE_DATE.name,
        scheduleType = ReminderScheduleType.WORK.name,
        triggerAt = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
        repeatRule = null,
        status = ReminderStatus.SCHEDULED.name,
        platformRequestCode = null,
        lastFailureCode = null,
        deliveredAt = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = createdAt.toEpochMilli(),
    )

    private fun StrongAlarmRequest.toEntity(
        debtId: DebtId,
        createdAt: Instant,
    ): ReminderEntity = ReminderEntity(
        id = id,
        subjectType = ReminderSubjectType.DEBT.name,
        subjectId = debtId.value,
        reminderType = ReminderType.STRONG_ALARM.name,
        scheduleType = ReminderScheduleType.EXACT_ALARM.name,
        triggerAt = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
        repeatRule = null,
        status = ReminderStatus.SCHEDULED.name,
        platformRequestCode = null,
        lastFailureCode = null,
        deliveredAt = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = createdAt.toEpochMilli(),
    )

''')
replace_once(path,
'''        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
    )''',
'''        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
        strongAlarm = strongAlarm,
    )''')
replace_once(path,
'''        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
    )''',
'''        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
        strongAlarm = strongAlarm,
    )''')
replace_once(path,
'''        val debtNotes: String?,
        val dueReminder: DueReminderRequest?,
    )''',
'''        val debtNotes: String?,
        val dueReminder: DueReminderRequest?,
        val strongAlarm: StrongAlarmRequest?,
    )''')
replace_once(path,
'''    private enum class ReminderSubjectType { DEBT }

    private enum class ReminderType { DUE_DATE }

    private enum class ReminderScheduleType { WORK }
''',
'''    private enum class ReminderSubjectType { DEBT }
''')

# Recovery and scheduling runtime.
write("app/src/main/java/com/wasl/app/reminder/ReminderRecoveryPolicy.kt", '''package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.ZoneId

internal data class ReminderRecoveryPlan(
    val shouldSchedule: Boolean,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val shouldPersistScheduledState: Boolean,
)

internal fun planReminderRecovery(
    stored: ReminderRecord,
    currentZone: ZoneId,
    now: Instant,
    canNotify: Boolean,
    canScheduleExactAlarms: Boolean = true,
): ReminderRecoveryPlan {
    if (stored.scheduleType == ReminderScheduleType.EXACT_ALARM) {
        val rebased = if (stored.zoneId != currentZone) {
            stored.triggerAt.atZone(stored.zoneId)
                .toLocalDateTime()
                .atZone(currentZone)
                .toInstant()
        } else {
            stored.triggerAt
        }
        val canSchedule = canNotify && canScheduleExactAlarms && rebased.isAfter(now)
        return ReminderRecoveryPlan(
            shouldSchedule = canSchedule,
            triggerAt = rebased,
            zoneId = currentZone,
            shouldPersistScheduledState = canSchedule && (
                stored.zoneId != currentZone ||
                    stored.status != ReminderStatus.SCHEDULED ||
                    stored.lastFailureCode != null
                ),
        )
    }

    if (stored.status == ReminderStatus.BLOCKED_PERMISSION && !canNotify) {
        return ReminderRecoveryPlan(
            shouldSchedule = false,
            triggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
            shouldPersistScheduledState = false,
        )
    }

    val triggerAt = if (stored.zoneId != currentZone) {
        ReminderTime.rebaseToZone(
            triggerAt = stored.triggerAt,
            sourceZone = stored.zoneId,
            targetZone = currentZone,
            now = now,
        )
    } else {
        stored.triggerAt
    }
    return ReminderRecoveryPlan(
        shouldSchedule = true,
        triggerAt = triggerAt,
        zoneId = currentZone,
        shouldPersistScheduledState = stored.zoneId != currentZone ||
            stored.status != ReminderStatus.SCHEDULED ||
            stored.lastFailureCode != null,
    )
}
''')

path = "app/src/main/java/com/wasl/app/reminder/WorkManagerReminderScheduler.kt"
replace_once(path,
'import com.wasl.app.data.ReminderRecord\n',
'import com.wasl.app.data.ReminderRecord\nimport com.wasl.app.data.ReminderScheduleType\n')
replace_once(path,
'''    override fun schedule(reminder: ReminderRecord) {
        val now = Instant.now(clock)''',
'''    override fun schedule(reminder: ReminderRecord) {
        require(reminder.scheduleType == ReminderScheduleType.WORK) {
            "WorkManager scheduler only accepts WORK reminders."
        }
        val now = Instant.now(clock)''')

write("app/src/main/java/com/wasl/app/reminder/ExactAlarmAccess.kt", '''package com.wasl.app.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object ExactAlarmAccess {
    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canSchedule(context)) return null
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
''')

write("app/src/main/java/com/wasl/app/reminder/ExactAlarmReminderScheduler.kt", '''package com.wasl.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import java.time.Clock
import java.time.Instant

class ExactAlarmPermissionRequiredException : IllegalStateException(
    "Exact alarm access is required for a strong alarm.",
)

internal class ExactAlarmReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: ReminderRecord) {
        require(reminder.scheduleType == ReminderScheduleType.EXACT_ALARM) {
            "Exact scheduler only accepts EXACT_ALARM reminders."
        }
        if (!reminder.triggerAt.isAfter(Instant.now(clock))) return
        if (!ExactAlarmAccess.canSchedule(appContext)) {
            throw ExactAlarmPermissionRequiredException()
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt.toEpochMilli(),
            pendingIntent(reminder.id),
        )
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(pendingIntent(reminderId))
    }

    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(appContext, StrongAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_STRONG_ALARM
            data = Uri.Builder()
                .scheme("wasl")
                .authority("strong-alarm")
                .appendPath(reminderId)
                .build()
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE_STRONG_ALARM = "com.wasl.app.action.FIRE_STRONG_ALARM"
        const val EXTRA_REMINDER_ID = "com.wasl.app.extra.STRONG_ALARM_REMINDER_ID"
    }
}

class HybridReminderScheduler(
    context: Context,
    clock: Clock = Clock.systemUTC(),
) : ReminderScheduler {
    private val workScheduler = WorkManagerReminderScheduler(context, clock)
    private val exactScheduler = ExactAlarmReminderScheduler(context, clock)

    override fun schedule(reminder: ReminderRecord) {
        when (reminder.scheduleType) {
            ReminderScheduleType.WORK -> workScheduler.schedule(reminder)
            ReminderScheduleType.EXACT_ALARM -> exactScheduler.schedule(reminder)
        }
    }

    override fun cancel(reminderId: String) {
        workScheduler.cancel(reminderId)
        exactScheduler.cancel(reminderId)
    }

    override fun requestRecovery() = workScheduler.requestRecovery()
}
''')

write("app/src/main/java/com/wasl/app/reminder/StrongAlarmReceiver.kt", '''package com.wasl.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wasl.app.WaslApplication
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.ReminderType
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StrongAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExactAlarmReminderScheduler.ACTION_FIRE_STRONG_ALARM) return
        val reminderId = intent.getStringExtra(ExactAlarmReminderScheduler.EXTRA_REMINDER_ID)
            ?: return
        val application = context.applicationContext as? WaslApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deliver(application, reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(application: WaslApplication, reminderId: String) {
        val reminder = application.reminderStore.getReminder(reminderId) ?: return
        if (reminder.type != ReminderType.STRONG_ALARM ||
            reminder.scheduleType != ReminderScheduleType.EXACT_ALARM ||
            reminder.status == ReminderStatus.CANCELLED ||
            reminder.status == ReminderStatus.DELIVERED
        ) {
            return
        }
        val now = Instant.now()
        val account = application.repository.getAccount(reminder.debtId)
        if (account == null || account.ledger.balance.isZero) {
            application.reminderStore.markReminderCancelled(reminderId, now)
            application.reminderScheduler.cancel(reminderId)
            return
        }
        if (!application.reminderNotificationPublisher.canNotify()) {
            application.reminderStore.markReminderBlockedByPermission(reminderId, now)
            return
        }

        try {
            val published = application.reminderNotificationPublisher.publishStrongAlarm(
                reminder = reminder,
                account = account,
            )
            if (published) {
                application.reminderStore.markReminderDelivered(reminderId, now)
            } else {
                application.reminderStore.markReminderBlockedByPermission(reminderId, now)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            application.reminderStore.markReminderFailed(
                reminderId = reminderId,
                failureCode = "STRONG_ALARM_DELIVERY",
                updatedAt = now,
            )
        }
    }
}
''')

path = "app/src/main/java/com/wasl/app/reminder/ReminderWorkers.kt"
replace_once(path,
'''                    now = now,
                    canNotify = application.reminderNotificationPublisher.canNotify(),
                )''',
'''                    now = now,
                    canNotify = application.reminderNotificationPublisher.canNotify(),
                    canScheduleExactAlarms = ExactAlarmAccess.canSchedule(applicationContext),
                )''')

path = "app/src/main/java/com/wasl/app/reminder/ReminderNotificationPublisher.kt"
replace_once(path,
'''                NotificationChannel(
                    ALARMS_CHANNEL_ID,
                    "المنبهات القوية",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "قناة مخصصة للمنبهات الصريحة التي يفعّلها المستخدم"
                },''',
'''                NotificationChannel(
                    ALARMS_CHANNEL_ID,
                    "المنبهات القوية",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "قناة مخصصة للمنبهات الصريحة التي يفعّلها المستخدم"
                    enableVibration(true)
                },''')
replace_once(path,
'''    private fun isChannelEnabled(channelId: String): Boolean =''',
'''    @SuppressLint("MissingPermission")
    fun publishStrongAlarm(
        reminder: ReminderRecord,
        account: AccountOverview,
    ): Boolean {
        check(canNotify()) { "Notifications are disabled." }
        ensureChannels()
        if (!isChannelEnabled(ALARMS_CHANNEL_ID)) return false

        val openAccount = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_DEBT
            putExtra(MainActivity.EXTRA_DEBT_ID, reminder.debtId.value)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            openAccount,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "حساب ${account.person.displayName} يحتاج انتباهك الآن. المتبقي ${formatMoney(account)}."
        val publicNotification = NotificationCompat.Builder(context, ALARMS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("منبه وَصل")
            .setContentText("افتح التطبيق لمراجعة حساب مهم.")
            .build()
        val notification = NotificationCompat.Builder(context, ALARMS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("منبه قوي للاستحقاق")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
        NotificationManagerCompat.from(context).notify(
            reminder.id,
            STRONG_ALARM_NOTIFICATION_ID,
            notification,
        )
        return true
    }

    private fun isChannelEnabled(channelId: String): Boolean =''')
replace_once(path,
'''        const val PROMISES_CHANNEL_ID = "wasl_payment_promises"
        const val NOTIFICATION_ID = 1001''',
'''        const val PROMISES_CHANNEL_ID = "wasl_payment_promises"
        const val NOTIFICATION_ID = 1001
        const val STRONG_ALARM_NOTIFICATION_ID = 1002''')

path = "app/src/main/java/com/wasl/app/WaslApplication.kt"
replace_once(path,
'import com.wasl.app.reminder.ReminderScheduler\nimport com.wasl.app.reminder.WorkManagerReminderScheduler\n',
'import com.wasl.app.reminder.HybridReminderScheduler\nimport com.wasl.app.reminder.ReminderScheduler\n')
replace_once(path, '        WorkManagerReminderScheduler(this)\n', '        HybridReminderScheduler(this)\n')

write("app/src/main/java/com/wasl/app/reminder/ReminderSystemEventReceiver.kt", '''package com.wasl.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> WorkManagerReminderScheduler(context).requestRecovery()
        }
    }
}
''')

path = "app/src/main/AndroidManifest.xml"
replace_once(path,
'    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
'    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />')
replace_once(path,
'''                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />''',
'''                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />''')
replace_once(path,
'''        <provider
            android:name="androidx.core.content.FileProvider"''',
'''        <receiver
            android:name=".reminder.StrongAlarmReceiver"
            android:enabled="true"
            android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"''')
