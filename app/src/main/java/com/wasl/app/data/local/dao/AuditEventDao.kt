package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.AuditEventEntity

@Dao
interface AuditEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events WHERE command_id = :commandId")
    suspend fun findByCommandId(commandId: String): AuditEventEntity?

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun count(): Int
}
