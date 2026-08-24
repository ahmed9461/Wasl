package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.PaymentPromiseEntity

@Dao
interface PaymentPromiseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PaymentPromiseEntity)

    @Query("SELECT * FROM payment_promises WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PaymentPromiseEntity?

    @Query("SELECT * FROM payment_promises WHERE create_command_id = :commandId LIMIT 1")
    suspend fun findByCreateCommandId(commandId: String): PaymentPromiseEntity?

    @Query("SELECT * FROM payment_promises WHERE resolution_command_id = :commandId LIMIT 1")
    suspend fun findByResolutionCommandId(commandId: String): PaymentPromiseEntity?

    @Query(
        """
        UPDATE payment_promises
        SET status = :status,
            resolution_command_id = :resolutionCommandId,
            resolved_at = :resolvedAt,
            resolution_note = :resolutionNote,
            updated_at = :updatedAt
        WHERE id = :id AND status = 'PENDING'
        """,
    )
    suspend fun resolve(
        id: String,
        status: String,
        resolutionCommandId: String,
        resolvedAt: Long,
        resolutionNote: String?,
        updatedAt: Long,
    ): Int
}
