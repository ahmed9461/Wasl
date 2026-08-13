package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.PersonEntity

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun findById(id: String): PersonEntity?

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int
}
