package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.InstallmentEntity
import com.wasl.app.data.local.entity.InstallmentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPlanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(entity: InstallmentPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallments(entities: List<InstallmentEntity>)

    @Query(
        """
        SELECT * FROM installment_plans
        WHERE debt_id = :debtId
        ORDER BY revision_number DESC, created_at DESC, id DESC
        """,
    )
    fun observePlansForDebt(debtId: String): Flow<List<InstallmentPlanEntity>>

    @Query(
        """
        SELECT * FROM installments
        WHERE debt_id = :debtId
        ORDER BY plan_id ASC, sequence_number ASC
        """,
    )
    fun observeInstallmentsForDebt(debtId: String): Flow<List<InstallmentEntity>>

    @Query(
        """
        SELECT * FROM installment_plans
        WHERE status = 'ACTIVE'
        ORDER BY created_at ASC, id ASC
        """,
    )
    fun observeActivePlans(): Flow<List<InstallmentPlanEntity>>

    @Query(
        """
        SELECT * FROM installments
        ORDER BY debt_id ASC, plan_id ASC, sequence_number ASC
        """,
    )
    fun observeAllInstallments(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installment_plans WHERE id = :id LIMIT 1")
    suspend fun findPlanById(id: String): InstallmentPlanEntity?

    @Query("SELECT * FROM installment_plans WHERE command_id = :commandId LIMIT 1")
    suspend fun findPlanByCommandId(commandId: String): InstallmentPlanEntity?

    @Query(
        """
        SELECT * FROM installment_plans
        WHERE debt_id = :debtId AND status = 'ACTIVE'
        LIMIT 1
        """,
    )
    suspend fun findActivePlanForDebt(debtId: String): InstallmentPlanEntity?

    @Query(
        """
        SELECT * FROM installments
        WHERE plan_id = :planId
        ORDER BY sequence_number ASC
        """,
    )
    suspend fun findInstallmentsForPlan(planId: String): List<InstallmentEntity>

    @Query(
        """
        UPDATE installment_plans
        SET status = 'SUPERSEDED',
            superseded_at = :supersededAt,
            superseded_after_sequence = :supersededAfterSequence
        WHERE id = :planId AND status = 'ACTIVE'
        """,
    )
    suspend fun markSuperseded(
        planId: String,
        supersededAt: Long,
        supersededAfterSequence: Long,
    ): Int
}
