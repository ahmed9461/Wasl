package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun findById(id: String): PersonEntity?

    @Query(
        """
        SELECT * FROM persons
        WHERE archived_at IS NULL
          AND (
            :queryPattern IS NULL
            OR display_name LIKE :queryPattern ESCAPE '\' COLLATE NOCASE
          )
        ORDER BY updated_at DESC, created_at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeActiveForSelection(
        queryPattern: String?,
        limit: Int,
    ): Flow<List<PersonEntity>>

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int
}
