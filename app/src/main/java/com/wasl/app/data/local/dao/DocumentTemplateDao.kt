package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.DocumentTemplateEntity

@Dao
interface DocumentTemplateDao {
    @Query("SELECT * FROM document_templates ORDER BY is_default DESC, display_name COLLATE NOCASE, id")
    suspend fun findAll(): List<DocumentTemplateEntity>

    @Query("SELECT * FROM document_templates WHERE is_default = 1 ORDER BY id LIMIT 1")
    suspend fun findDefault(): DocumentTemplateEntity?

    @Query("SELECT * FROM document_templates WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DocumentTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DocumentTemplateEntity): Long
}
