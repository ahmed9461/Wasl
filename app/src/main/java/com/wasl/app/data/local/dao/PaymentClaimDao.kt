package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.PaymentClaimEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentClaimDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PaymentClaimEntity)

    @Query(
        """
        SELECT * FROM payment_claims
        WHERE debt_id = :debtId
        ORDER BY claimed_at DESC, created_at DESC, id DESC
        """,
    )
    fun observeForDebt(debtId: String): Flow<List<PaymentClaimEntity>>

    @Query(
        """
        SELECT * FROM payment_claims
        WHERE status = 'ACTIVE'
          AND follow_up_date_epoch_day IS NOT NULL
          AND follow_up_date_epoch_day <= :onOrBeforeEpochDay
        ORDER BY follow_up_date_epoch_day ASC, claimed_at ASC, id ASC
        """,
    )
    fun observeActiveOnOrBefore(onOrBeforeEpochDay: Long): Flow<List<PaymentClaimEntity>>

    @Query("SELECT * FROM payment_claims WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PaymentClaimEntity?

    @Query("SELECT * FROM payment_claims WHERE create_command_id = :commandId LIMIT 1")
    suspend fun findByCreateCommandId(commandId: String): PaymentClaimEntity?

    @Query("SELECT * FROM payment_claims WHERE resolution_command_id = :commandId LIMIT 1")
    suspend fun findByResolutionCommandId(commandId: String): PaymentClaimEntity?

    @Query(
        """
        UPDATE payment_claims
        SET status = :status,
            resolution_command_id = :resolutionCommandId,
            resolved_at = :resolvedAt,
            resolution_note = :resolutionNote,
            updated_at = :updatedAt
        WHERE id = :id AND status = 'ACTIVE'
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
