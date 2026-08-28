package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.UpsertGeneralReminderCommand
import com.wasl.app.data.local.entity.ReminderEntity
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGeneralReminderStore(
    private val database: WaslDatabase,
) : GeneralReminderStore {
    private val reminderDao = database.reminderDao()
    private val debtDao = database.debtDao()

    override fun observeReminderForDebt(debtId: DebtId): Flow<GeneralReminderRecord?> =
        reminderDao.observeGeneralForDebt(debtId.value).map { it?.toGeneralRecord() }

    override suspend fun getReminder(reminderId: String): GeneralReminderRecord? =
        reminderDao.findById(reminderId)
            ?.takeIf { it.reminderType == GENERAL_REMINDER_TYPE }
            ?.toGeneralRecord()

    override suspend fun getReminderForDebt(debtId: DebtId): GeneralReminderRecord? =
        reminderDao.findGeneralForDebt(debtId.value)?.toGeneralRecord()

    override suspend fun getRecoverableReminders(): List<GeneralReminderRecord> =
        reminderDao.findRecoverableGeneral().map { it.toGeneralRecord() }

    override suspend fun upsertReminder(
        command: UpsertGeneralReminderCommand,
    ): GeneralReminderRecord = database.withTransaction {
        val account = debtDao.findAggregateById(command.debtId.value)
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        require(account.debt.lifecycleState == DebtLifecycleState.ACTIVE.name) {
            "General reminders require an active debt."
        }
        require(account.debt.closedAt == null) {
            "A settled debt cannot receive a new general reminder."
        }

        reminderDao.findById(command.reminderId)?.let { byId ->
            if (byId.subjectType != DEBT_SUBJECT_TYPE ||
                byId.subjectId != command.debtId.value ||
                byId.reminderType != GENERAL_REMINDER_TYPE
            ) {
                throw CommandConflictException(
                    "Reminder ID ${command.reminderId} is already used by another reminder.",
                )
            }
        }
        val existing = reminderDao.findGeneralForDebt(command.debtId.value)
        if (existing != null && existing.id != command.reminderId) {
            throw CommandConflictException(
                "Debt ${command.debtId.value} already has a general reminder with another ID.",
            )
        }

        val entity = existing?.copy(
            triggerAt = command.triggerAt.toEpochMilli(),
            zoneId = command.zoneId.id,
            repeatRule = command.repeatRule?.toStorageValue(),
            status = ReminderStatus.SCHEDULED.name,
            lastFailureCode = null,
            deliveredAt = null,
            updatedAt = command.updatedAt.toEpochMilli(),
        ) ?: ReminderEntity(
            id = command.reminderId,
            subjectType = DEBT_SUBJECT_TYPE,
            subjectId = command.debtId.value,
            reminderType = GENERAL_REMINDER_TYPE,
            scheduleType = WORK_SCHEDULE_TYPE,
            triggerAt = command.triggerAt.toEpochMilli(),
            zoneId = command.zoneId.id,
            repeatRule = command.repeatRule?.toStorageValue(),
            status = ReminderStatus.SCHEDULED.name,
            platformRequestCode = null,
            lastFailureCode = null,
            deliveredAt = null,
            createdAt = command.updatedAt.toEpochMilli(),
            updatedAt = command.updatedAt.toEpochMilli(),
        )
        if (existing == null) {
            reminderDao.insert(entity)
        } else {
            reminderDao.update(entity)
        }
        entity.toGeneralRecord()
    }

    override suspend fun updateReminderSchedule(
        reminderId: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ): GeneralReminderRecord = database.withTransaction {
        val existing = requireGeneralReminder(reminderId)
        val updated = existing.copy(
            triggerAt = triggerAt.toEpochMilli(),
            zoneId = zoneId.id,
            status = ReminderStatus.SCHEDULED.name,
            lastFailureCode = null,
            deliveredAt = null,
            updatedAt = updatedAt.toEpochMilli(),
        )
        reminderDao.update(updated)
        updated.toGeneralRecord()
    }

    override suspend fun markReminderDelivered(
        reminderId: String,
        deliveredAt: Instant,
    ): GeneralReminderRecord = setStatus(
        reminderId = reminderId,
        status = ReminderStatus.DELIVERED,
        failureCode = null,
        deliveredAt = deliveredAt,
        updatedAt = deliveredAt,
    )

    override suspend fun markReminderBlockedByPermission(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord = setStatus(
        reminderId = reminderId,
        status = ReminderStatus.BLOCKED_PERMISSION,
        failureCode = FAILURE_NOTIFICATIONS_DISABLED,
        deliveredAt = null,
        updatedAt = updatedAt,
    )

    override suspend fun markReminderFailed(
        reminderId: String,
        failureCode: String,
        updatedAt: Instant,
    ): GeneralReminderRecord {
        require(failureCode.matches(Regex("[A-Z0-9_]{1,48}"))) {
            "General reminder failure code is invalid."
        }
        return setStatus(
            reminderId = reminderId,
            status = ReminderStatus.FAILED,
            failureCode = failureCode,
            deliveredAt = null,
            updatedAt = updatedAt,
        )
    }

    override suspend fun cancelReminder(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord = setStatus(
        reminderId = reminderId,
        status = ReminderStatus.CANCELLED,
        failureCode = null,
        deliveredAt = null,
        updatedAt = updatedAt,
    )

    private suspend fun setStatus(
        reminderId: String,
        status: ReminderStatus,
        failureCode: String?,
        deliveredAt: Instant?,
        updatedAt: Instant,
    ): GeneralReminderRecord = database.withTransaction {
        requireGeneralReminder(reminderId)
        check(
            reminderDao.updateStatus(
                id = reminderId,
                status = status.name,
                failureCode = failureCode,
                deliveredAt = deliveredAt?.toEpochMilli(),
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "General reminder $reminderId was not updated." }
        requireGeneralReminder(reminderId).toGeneralRecord()
    }

    private suspend fun requireGeneralReminder(reminderId: String): ReminderEntity =
        reminderDao.findById(reminderId)
            ?.takeIf {
                it.subjectType == DEBT_SUBJECT_TYPE &&
                    it.reminderType == GENERAL_REMINDER_TYPE &&
                    it.scheduleType == WORK_SCHEDULE_TYPE
            }
            ?: throw RecordNotFoundException("General reminder $reminderId was not found.")

    private fun ReminderEntity.toGeneralRecord(): GeneralReminderRecord {
        check(subjectType == DEBT_SUBJECT_TYPE) { "Unsupported general reminder subject: $subjectType" }
        check(reminderType == GENERAL_REMINDER_TYPE) { "Unsupported reminder type: $reminderType" }
        check(scheduleType == WORK_SCHEDULE_TYPE) { "General reminders require WORK scheduling." }
        return GeneralReminderRecord(
            id = id,
            debtId = DebtId(subjectId),
            triggerAt = Instant.ofEpochMilli(triggerAt),
            zoneId = ZoneId.of(zoneId),
            repeatRule = GeneralReminderRepeatRule.fromStorageValue(repeatRule),
            status = ReminderStatus.valueOf(status),
            lastFailureCode = lastFailureCode,
            deliveredAt = deliveredAt?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    companion object {
        const val GENERAL_REMINDER_TYPE = "GENERAL"
        private const val DEBT_SUBJECT_TYPE = "DEBT"
        private const val WORK_SCHEDULE_TYPE = "WORK"
        private const val FAILURE_NOTIFICATIONS_DISABLED = "NOTIFICATIONS_DISABLED"
    }
}
