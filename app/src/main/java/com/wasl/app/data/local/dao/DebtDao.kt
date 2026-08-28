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
    @Query(
        """
        SELECT * FROM debts
        WHERE lifecycle_state = 'ACTIVE'
          AND closed_at IS NULL
          AND due_date_epoch_day IS NOT NULL
          AND due_date_epoch_day <= :onOrBeforeEpochDay
        ORDER BY due_date_epoch_day ASC, opened_at DESC, id DESC
        """,
    )
    fun observeDueAggregates(onOrBeforeEpochDay: Long): Flow<List<DebtAggregate>>

    @Transaction
    @Query(
        """
        SELECT debts.* FROM debts
        INNER JOIN persons ON persons.id = debts.person_id
        WHERE debts.lifecycle_state = 'ACTIVE'
          AND (
            persons.display_name LIKE :queryPattern ESCAPE '\' COLLATE NOCASE
            OR COALESCE(debts.description, '') LIKE :queryPattern ESCAPE '\' COLLATE NOCASE
          )
        ORDER BY
          CASE
            WHEN persons.display_name LIKE :queryPattern ESCAPE '\' COLLATE NOCASE THEN 0
            ELSE 1
          END,
          debts.updated_at DESC,
          debts.opened_at DESC,
          debts.id DESC
        LIMIT :limit
        """,
    )
    fun observeSearchAggregates(
        queryPattern: String,
        limit: Int,
    ): Flow<List<DebtAggregate>>

    @Transaction
    @Query("SELECT * FROM debts WHERE id = :id")
    fun observeAggregateById(id: String): Flow<DebtAggregate?>

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

    @Query(
        """
        UPDATE debts
        SET due_date_epoch_day = :dueDateEpochDay, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateDueDate(id: String, dueDateEpochDay: Long?, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun count(): Int
}
