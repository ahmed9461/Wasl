package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.DocumentIdentityEntity

@Dao
interface DocumentIdentityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(identity: DocumentIdentityEntity)

    @Query("SELECT * FROM document_identities WHERE id = :id")
    suspend fun findById(id: String): DocumentIdentityEntity?

    @Query(
        """
        SELECT * FROM document_identities
        WHERE is_default = 1
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findDefault(): DocumentIdentityEntity?

    @Query("UPDATE document_identities SET is_default = 0 WHERE id != :exceptId AND is_default = 1")
    suspend fun clearOtherDefaults(exceptId: String): Int

    @Query(
        """
        UPDATE document_identities
        SET display_name = :displayName,
            activity_name = :activityName,
            phone = :phone,
            footer_text = :footerText,
            is_default = 1,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateDefault(
        id: String,
        displayName: String,
        activityName: String?,
        phone: String?,
        footerText: String?,
        updatedAt: Long,
    ): Int
}
