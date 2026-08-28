package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wasl.app.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity): Int

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
        WHERE subject_type = 'DEBT'
          AND subject_id = :debtId
          AND reminder_type = 'GENERAL'
        """,
    )
    suspend fun findGeneralForDebt(debtId: String): ReminderEntity?

    @Query(
        """
        SELECT * FROM reminders
        WHERE subject_type = 'DEBT'
          AND subject_id = :debtId
          AND reminder_type = 'GENERAL'
        """,
    )
    fun observeGeneralForDebt(debtId: String): Flow<ReminderEntity?>

    @Query(
        """
        SELECT * FROM reminders
        WHERE reminder_type IN ('DUE_DATE', 'STRONG_ALARM')
          AND (
            status IN ('SCHEDULED', 'BLOCKED_PERMISSION', 'FAILED')
            OR (status = 'DELIVERED' AND reminder_type = 'DUE_DATE')
          )
        ORDER BY trigger_at, id
        """,
    )
    suspend fun findRecoverable(): List<ReminderEntity>

    @Query(
        """
        SELECT * FROM reminders
        WHERE subject_type = 'DEBT'
          AND reminder_type = 'GENERAL'
          AND status IN ('SCHEDULED', 'BLOCKED_PERMISSION', 'FAILED')
        ORDER BY trigger_at, id
        """,
    )
    suspend fun findRecoverableGeneral(): List<ReminderEntity>

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
