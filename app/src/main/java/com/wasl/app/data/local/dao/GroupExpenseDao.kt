package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.wasl.app.data.local.entity.GroupExpenseAggregate
import com.wasl.app.data.local.entity.GroupExpenseEntity
import com.wasl.app.data.local.entity.GroupExpenseShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupExpenseDao {
    @Insert
    suspend fun insertGroupExpense(entity: GroupExpenseEntity)

    @Insert
    suspend fun insertShare(entity: GroupExpenseShareEntity)

    @Query("SELECT * FROM group_expense_shares WHERE id = :id LIMIT 1")
    suspend fun findShareById(id: String): GroupExpenseShareEntity?

    @Transaction
    @Query("SELECT * FROM group_expenses WHERE id = :id LIMIT 1")
    suspend fun findAggregateById(id: String): GroupExpenseAggregate?

    @Transaction
    @Query("SELECT * FROM group_expenses WHERE command_id = :commandId LIMIT 1")
    suspend fun findAggregateByCommandId(commandId: String): GroupExpenseAggregate?

    @Transaction
    @Query("SELECT * FROM group_expenses ORDER BY occurred_at DESC, id DESC")
    fun observeAggregates(): Flow<List<GroupExpenseAggregate>>
}
