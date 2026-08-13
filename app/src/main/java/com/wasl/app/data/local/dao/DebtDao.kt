package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wasl.app.data.local.entity.DebtAggregate
import com.wasl.app.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(debt: DebtEntity)

    @Transaction
    @Query(
        """
        SELECT * FROM debts
        WHERE lifecycle_state = 'ACTIVE'
        ORDER BY opened_at DESC, id DESC
        """,
    )
    fun observeActiveAggregates(): Flow<List<DebtAggregate>>

    @Transaction
    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun findAggregateById(id: String): DebtAggregate?

    @Query(
        """
        UPDATE debts
        SET closed_at = :closedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateClosure(id: String, closedAt: Long?, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun count(): Int
}
