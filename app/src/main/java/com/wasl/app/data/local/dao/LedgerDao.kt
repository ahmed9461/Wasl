package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.LedgerEntryEntity

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity)

    @Query("SELECT * FROM ledger_entries WHERE command_id = :commandId")
    suspend fun findByCommandId(commandId: String): LedgerEntryEntity?

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE debt_id = :debtId")
    suspend fun countForDebt(debtId: String): Int
}
