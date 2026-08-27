package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AttachmentEntity)

    @Query(
        """
        SELECT * FROM attachments
        WHERE debt_id = :debtId
        ORDER BY created_at DESC, id DESC
        """,
    )
    fun observeForDebt(debtId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AttachmentEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE relative_path = :relativePath")
    suspend fun countForRelativePath(relativePath: String): Int
}
