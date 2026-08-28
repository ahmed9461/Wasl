from pathlib import Path
import re

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    if text.count(old) != 1:
        raise SystemExit(f'{path}: expected one occurrence, found {text.count(old)} for {old[:80]!r}')
    write(path, text.replace(old, new, 1))


def replace_all_exact(path, old, new, expected):
    text = read(path)
    count = text.count(old)
    if count != expected:
        raise SystemExit(f'{path}: expected {expected} occurrences, found {count} for {old[:80]!r}')
    write(path, text.replace(old, new))


write('app/src/main/java/com/wasl/app/data/DocumentTemplateModels.kt', '''package com.wasl.app.data

import java.time.Instant

enum class DocumentTemplateStyle {
    MINIMAL,
    BUSINESS,
    CLASSIC,
    COMPACT,
    MODERN,
}

data class DocumentTemplateRecord(
    val id: String,
    val displayName: String,
    val style: DocumentTemplateStyle,
    val showPhone: Boolean,
    val showFooter: Boolean,
    val showBalance: Boolean,
    val showNotes: Boolean,
    val isDefault: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Document template ID cannot be blank." }
        require(displayName.isNotBlank()) { "Document template name cannot be blank." }
    }

    fun toSnapshot(): DocumentTemplateSnapshot = DocumentTemplateSnapshot(
        id = id,
        displayName = displayName,
        style = style,
        showPhone = showPhone,
        showFooter = showFooter,
        showBalance = showBalance,
        showNotes = showNotes,
    )
}

data class DocumentTemplateSnapshot(
    val id: String,
    val displayName: String,
    val style: DocumentTemplateStyle,
    val showPhone: Boolean,
    val showFooter: Boolean,
    val showBalance: Boolean,
    val showNotes: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Document template snapshot ID cannot be blank." }
        require(displayName.isNotBlank()) { "Document template snapshot name cannot be blank." }
    }
}

object DocumentTemplateCatalog {
    const val MINIMAL_ID = "builtin-minimal"
    const val DEFAULT_TEMPLATE_ID = "builtin-business"
    const val CLASSIC_ID = "builtin-classic"
    const val COMPACT_ID = "builtin-compact"
    const val MODERN_ID = "builtin-modern"

    val builtIns: List<DocumentTemplateRecord> = listOf(
        builtIn(MINIMAL_ID, "بسيط", DocumentTemplateStyle.MINIMAL, false, false, true, false, false),
        builtIn(DEFAULT_TEMPLATE_ID, "عملي", DocumentTemplateStyle.BUSINESS, true, true, true, true, true),
        builtIn(CLASSIC_ID, "كلاسيكي", DocumentTemplateStyle.CLASSIC, true, true, true, true, false),
        builtIn(COMPACT_ID, "مضغوط", DocumentTemplateStyle.COMPACT, false, false, true, false, false),
        builtIn(MODERN_ID, "حديث", DocumentTemplateStyle.MODERN, true, true, true, true, false),
    )

    val defaultSnapshot: DocumentTemplateSnapshot =
        builtIns.single { it.id == DEFAULT_TEMPLATE_ID }.toSnapshot()

    private fun builtIn(
        id: String,
        displayName: String,
        style: DocumentTemplateStyle,
        showPhone: Boolean,
        showFooter: Boolean,
        showBalance: Boolean,
        showNotes: Boolean,
        isDefault: Boolean,
    ) = DocumentTemplateRecord(
        id = id,
        displayName = displayName,
        style = style,
        showPhone = showPhone,
        showFooter = showFooter,
        showBalance = showBalance,
        showNotes = showNotes,
        isDefault = isDefault,
        isBuiltIn = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
''')

write('app/src/main/java/com/wasl/app/data/local/entity/DocumentTemplateEntity.kt', '''package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_templates",
    indices = [
        Index(value = ["is_default"]),
        Index(value = ["style"]),
    ],
)
data class DocumentTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "style")
    val style: String,
    @ColumnInfo(name = "show_phone")
    val showPhone: Boolean,
    @ColumnInfo(name = "show_footer")
    val showFooter: Boolean,
    @ColumnInfo(name = "show_balance")
    val showBalance: Boolean,
    @ColumnInfo(name = "show_notes")
    val showNotes: Boolean,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
''')

write('app/src/main/java/com/wasl/app/data/local/dao/DocumentTemplateDao.kt', '''package com.wasl.app.data.local.dao

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
''')

# Repository models: snapshot owns immutable template; old call sites keep the built-in Business default.
p = 'app/src/main/java/com/wasl/app/data/RepositoryModels.kt'
replace_once(p,
'''    val direction: DebtDirection\n    val identity: DocumentIdentitySnapshot\n}''',
'''    val direction: DebtDirection\n    val identity: DocumentIdentitySnapshot\n    val template: DocumentTemplateSnapshot\n}''')
replace_all_exact(p,
'''    override val identity: DocumentIdentitySnapshot,\n) : DocumentSnapshot {''',
'''    override val identity: DocumentIdentitySnapshot,\n    override val template: DocumentTemplateSnapshot = DocumentTemplateCatalog.defaultSnapshot,\n) : DocumentSnapshot {''',
3)
replace_all_exact(p,
'''    val issueZoneId: ZoneId,\n) {\n    init {\n        require(commandId.isNotBlank()) { "Document command ID cannot be blank." }''',
'''    val issueZoneId: ZoneId,\n    val templateId: String = DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID,\n) {\n    init {\n        require(commandId.isNotBlank()) { "Document command ID cannot be blank." }''',
3)
replace_all_exact(p,
'''        require(identityId.isNotBlank()) { "Document identity ID cannot be blank." }\n        require(issuerDisplayName.isNotBlank()) { "Issuer name cannot be blank." }''',
'''        require(identityId.isNotBlank()) { "Document identity ID cannot be blank." }\n        require(templateId.isNotBlank()) { "Document template ID cannot be blank." }\n        require(issuerDisplayName.isNotBlank()) { "Issuer name cannot be blank." }''',
3)

# Payment snapshot codec: additive fields with defaults keep version-1 snapshots decodable.
p = 'app/src/main/java/com/wasl/app/data/local/PaymentReceiptSnapshotCodec.kt'
replace_once(p,
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.PaymentReceiptSnapshot''',
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentTemplateCatalog\nimport com.wasl.app.data.DocumentTemplateSnapshot\nimport com.wasl.app.data.DocumentTemplateStyle\nimport com.wasl.app.data.PaymentReceiptSnapshot''')
replace_once(p,
'''        val footerText: String?,\n    ) {''',
'''        val footerText: String?,\n        val templateId: String = DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID,\n        val templateDisplayName: String = "عملي",\n        val templateStyle: String = DocumentTemplateStyle.BUSINESS.name,\n        val templateShowPhone: Boolean = true,\n        val templateShowFooter: Boolean = true,\n        val templateShowBalance: Boolean = true,\n        val templateShowNotes: Boolean = true,\n    ) {''')
replace_once(p,
'''                identity = DocumentIdentitySnapshot(\n                    displayName = issuerDisplayName,\n                    activityName = issuerActivityName,\n                    phone = issuerPhone,\n                    footerText = footerText,\n                ),\n            )''',
'''                identity = DocumentIdentitySnapshot(\n                    displayName = issuerDisplayName,\n                    activityName = issuerActivityName,\n                    phone = issuerPhone,\n                    footerText = footerText,\n                ),\n                template = DocumentTemplateSnapshot(\n                    id = templateId,\n                    displayName = templateDisplayName,\n                    style = DocumentTemplateStyle.valueOf(templateStyle),\n                    showPhone = templateShowPhone,\n                    showFooter = templateShowFooter,\n                    showBalance = templateShowBalance,\n                    showNotes = templateShowNotes,\n                ),\n            )''')
replace_once(p,
'''                footerText = snapshot.identity.footerText,\n            )''',
'''                footerText = snapshot.identity.footerText,\n                templateId = snapshot.template.id,\n                templateDisplayName = snapshot.template.displayName,\n                templateStyle = snapshot.template.style.name,\n                templateShowPhone = snapshot.template.showPhone,\n                templateShowFooter = snapshot.template.showFooter,\n                templateShowBalance = snapshot.template.showBalance,\n                templateShowNotes = snapshot.template.showNotes,\n            )''')

# Account-document snapshot codec: same backward-compatible additive template payload.
p = 'app/src/main/java/com/wasl/app/data/local/AccountDocumentSnapshotCodec.kt'
replace_once(p,
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentSnapshot''',
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentTemplateCatalog\nimport com.wasl.app.data.DocumentTemplateSnapshot\nimport com.wasl.app.data.DocumentTemplateStyle\nimport com.wasl.app.data.DocumentSnapshot''')
replace_all_exact(p,
'''        val footerText: String?,\n    ) {''',
'''        val footerText: String?,\n        val templateId: String = DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID,\n        val templateDisplayName: String = "عملي",\n        val templateStyle: String = DocumentTemplateStyle.BUSINESS.name,\n        val templateShowPhone: Boolean = true,\n        val templateShowFooter: Boolean = true,\n        val templateShowBalance: Boolean = true,\n        val templateShowNotes: Boolean = true,\n    ) {''',
2)
replace_once(p,
'''                debtDescription = debtDescription,\n                identity = identity(),\n            )''',
'''                debtDescription = debtDescription,\n                identity = identity(),\n                template = template(),\n            )''')
replace_once(p,
'''        private fun identity() = DocumentIdentitySnapshot(\n            displayName = issuerDisplayName,\n            activityName = issuerActivityName,\n            phone = issuerPhone,\n            footerText = footerText,\n        )''',
'''        private fun identity() = DocumentIdentitySnapshot(\n            displayName = issuerDisplayName,\n            activityName = issuerActivityName,\n            phone = issuerPhone,\n            footerText = footerText,\n        )\n\n        private fun template() = DocumentTemplateSnapshot(\n            id = templateId,\n            displayName = templateDisplayName,\n            style = DocumentTemplateStyle.valueOf(templateStyle),\n            showPhone = templateShowPhone,\n            showFooter = templateShowFooter,\n            showBalance = templateShowBalance,\n            showNotes = templateShowNotes,\n        )''')
replace_once(p,
'''                footerText = snapshot.identity.footerText,\n            )''',
'''                footerText = snapshot.identity.footerText,\n                templateId = snapshot.template.id,\n                templateDisplayName = snapshot.template.displayName,\n                templateStyle = snapshot.template.style.name,\n                templateShowPhone = snapshot.template.showPhone,\n                templateShowFooter = snapshot.template.showFooter,\n                templateShowBalance = snapshot.template.showBalance,\n                templateShowNotes = snapshot.template.showNotes,\n            )''')
replace_once(p,
'''                identity = DocumentIdentitySnapshot(\n                    displayName = issuerDisplayName,\n                    activityName = issuerActivityName,\n                    phone = issuerPhone,\n                    footerText = footerText,\n                ),\n            )''',
'''                identity = DocumentIdentitySnapshot(\n                    displayName = issuerDisplayName,\n                    activityName = issuerActivityName,\n                    phone = issuerPhone,\n                    footerText = footerText,\n                ),\n                template = DocumentTemplateSnapshot(\n                    id = templateId,\n                    displayName = templateDisplayName,\n                    style = DocumentTemplateStyle.valueOf(templateStyle),\n                    showPhone = templateShowPhone,\n                    showFooter = templateShowFooter,\n                    showBalance = templateShowBalance,\n                    showNotes = templateShowNotes,\n                ),\n            )''')
# second footer serialization belongs to account statement
text = read(p)
old = '''                footerText = snapshot.identity.footerText,\n            )'''
if text.count(old) != 1:
    raise SystemExit(f'{p}: expected remaining account-statement footer serialization once, got {text.count(old)}')
write(p, text.replace(old, '''                footerText = snapshot.identity.footerText,\n                templateId = snapshot.template.id,\n                templateDisplayName = snapshot.template.displayName,\n                templateStyle = snapshot.template.style.name,\n                templateShowPhone = snapshot.template.showPhone,\n                templateShowFooter = snapshot.template.showFooter,\n                templateShowBalance = snapshot.template.showBalance,\n                templateShowNotes = snapshot.template.showNotes,\n            )''', 1))

# Store/service surfaces expose templates with defaults so test doubles remain source-compatible.
p = 'app/src/main/java/com/wasl/app/data/PaymentReceiptStore.kt'
replace_once(p,
'''interface PaymentReceiptStore {\n    suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord?''',
'''interface PaymentReceiptStore {\n    suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord?\n\n    suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> = emptyList()\n\n    suspend fun getDefaultDocumentTemplate(): DocumentTemplateRecord? = null''')

p = 'app/src/main/java/com/wasl/app/document/PaymentReceiptService.kt'
replace_once(p,
'''import com.wasl.app.data.DocumentIdentityRecord\nimport com.wasl.app.data.DocumentStatus''',
'''import com.wasl.app.data.DocumentIdentityRecord\nimport com.wasl.app.data.DocumentTemplateRecord\nimport com.wasl.app.data.DocumentStatus''')
replace_once(p,
'''interface PaymentReceiptService {\n    suspend fun getDefaultIdentity(): DocumentIdentityRecord?''',
'''interface PaymentReceiptService {\n    suspend fun getDefaultIdentity(): DocumentIdentityRecord?\n\n    suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> = emptyList()\n\n    suspend fun getDefaultTemplate(): DocumentTemplateRecord? = null''')
replace_once(p,
'''    override suspend fun getDefaultIdentity(): DocumentIdentityRecord? =\n        store.getDefaultDocumentIdentity()''',
'''    override suspend fun getDefaultIdentity(): DocumentIdentityRecord? =\n        store.getDefaultDocumentIdentity()\n\n    override suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> =\n        store.getDocumentTemplates()\n\n    override suspend fun getDefaultTemplate(): DocumentTemplateRecord? =\n        store.getDefaultDocumentTemplate()''')

# Room repository: seed/catalog templates lazily for fresh DBs, snapshot selected template, version=2.
p = 'app/src/main/java/com/wasl/app/data/local/RoomWaslRepository.kt'
replace_once(p,
'''import com.wasl.app.data.DocumentIdentityRecord\nimport com.wasl.app.data.DocumentIdentitySnapshot''',
'''import com.wasl.app.data.DocumentIdentityRecord\nimport com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentTemplateCatalog\nimport com.wasl.app.data.DocumentTemplateRecord\nimport com.wasl.app.data.DocumentTemplateSnapshot\nimport com.wasl.app.data.DocumentTemplateStyle''')
replace_once(p,
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.IssuedDocumentEntity''',
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.DocumentTemplateEntity\nimport com.wasl.app.data.local.entity.IssuedDocumentEntity''')
replace_once(p,
'''    private val documentIdentityDao = database.documentIdentityDao()\n    private val issuedDocumentDao = database.issuedDocumentDao()''',
'''    private val documentIdentityDao = database.documentIdentityDao()\n    private val documentTemplateDao = database.documentTemplateDao()\n    private val issuedDocumentDao = database.issuedDocumentDao()''')
replace_once(p,
'''    override suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord? =\n        documentIdentityDao.findDefault()?.toRecord()''',
'''    override suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord? =\n        documentIdentityDao.findDefault()?.toRecord()\n\n    override suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> {\n        ensureBuiltInDocumentTemplates()\n        return documentTemplateDao.findAll().map { it.toRecord() }\n    }\n\n    override suspend fun getDefaultDocumentTemplate(): DocumentTemplateRecord? {\n        ensureBuiltInDocumentTemplates()\n        return documentTemplateDao.findDefault()?.toRecord()\n    }''')
replace_once(p,
'''        saveDefaultIdentity(command, normalizedIdentity)\n\n        val issueYear''',
'''        saveDefaultIdentity(command, normalizedIdentity)\n        val template = requireDocumentTemplate(command.templateId).toSnapshot()\n\n        val issueYear''')
replace_once(p,
'''            identity = normalizedIdentity,\n        )''',
'''            identity = normalizedIdentity,\n            template = template,\n        )''')
replace_once(p,
'''            snapshot.issueZoneId == command.issueZoneId &&\n            snapshot.identity == DocumentIdentitySnapshot(''',
'''            snapshot.issueZoneId == command.issueZoneId &&\n            snapshot.template.id == command.templateId &&\n            snapshot.identity == DocumentIdentitySnapshot(''')
replace_once(p,
'''    private fun DocumentIdentityEntity.toRecord(): DocumentIdentityRecord =''',
'''    private suspend fun ensureBuiltInDocumentTemplates() {\n        DocumentTemplateCatalog.builtIns.forEach { record ->\n            documentTemplateDao.insert(record.toEntity())\n        }\n    }\n\n    private suspend fun requireDocumentTemplate(id: String): DocumentTemplateRecord {\n        ensureBuiltInDocumentTemplates()\n        return documentTemplateDao.findById(id)?.toRecord()\n            ?: throw RecordNotFoundException("Document template $id was not found.")\n    }\n\n    private fun DocumentTemplateRecord.toEntity() = DocumentTemplateEntity(\n        id = id,\n        displayName = displayName,\n        style = style.name,\n        showPhone = showPhone,\n        showFooter = showFooter,\n        showBalance = showBalance,\n        showNotes = showNotes,\n        isDefault = isDefault,\n        isBuiltIn = isBuiltIn,\n        createdAt = createdAt.toEpochMilli(),\n        updatedAt = updatedAt.toEpochMilli(),\n    )\n\n    private fun DocumentTemplateEntity.toRecord() = DocumentTemplateRecord(\n        id = id,\n        displayName = displayName,\n        style = DocumentTemplateStyle.valueOf(style),\n        showPhone = showPhone,\n        showFooter = showFooter,\n        showBalance = showBalance,\n        showNotes = showNotes,\n        isDefault = isDefault,\n        isBuiltIn = isBuiltIn,\n        createdAt = Instant.ofEpochMilli(createdAt),\n        updatedAt = Instant.ofEpochMilli(updatedAt),\n    )\n\n    private fun DocumentIdentityEntity.toRecord(): DocumentIdentityRecord =''')
replace_once(p,
'''        const val PAYMENT_RECEIPT_SNAPSHOT_VERSION = 1''',
'''        const val PAYMENT_RECEIPT_SNAPSHOT_VERSION = 2''')

# Account document store mirrors template snapshot behavior.
p = 'app/src/main/java/com/wasl/app/data/local/RoomAccountDocumentStore.kt'
replace_once(p,
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentStatus''',
'''import com.wasl.app.data.DocumentIdentitySnapshot\nimport com.wasl.app.data.DocumentTemplateCatalog\nimport com.wasl.app.data.DocumentTemplateRecord\nimport com.wasl.app.data.DocumentTemplateStyle\nimport com.wasl.app.data.DocumentStatus''')
replace_once(p,
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.IssuedDocumentEntity''',
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.DocumentTemplateEntity\nimport com.wasl.app.data.local.entity.IssuedDocumentEntity''')
replace_once(p,
'''    private val identityDao = database.documentIdentityDao()\n    private val documentDao = database.issuedDocumentDao()''',
'''    private val identityDao = database.documentIdentityDao()\n    private val templateDao = database.documentTemplateDao()\n    private val documentDao = database.issuedDocumentDao()''')
replace_all_exact(p,
'''        saveDefaultIdentity(command.identityId, command.issuedAt, identity)\n        val issueYear''',
'''        saveDefaultIdentity(command.identityId, command.issuedAt, identity)\n        val template = requireDocumentTemplate(command.templateId).toSnapshot()\n        val issueYear''',
2)
replace_once(p,
'''            identity = identity,\n        )\n        val entity = IssuedDocumentEntity(''',
'''            identity = identity,\n            template = template,\n        )\n        val entity = IssuedDocumentEntity(''')
# account statement has entries before identity; patch its identity separately
replace_once(p,
'''            },\n            identity = identity,\n        )\n        val entity = IssuedDocumentEntity(''',
'''            },\n            identity = identity,\n            template = template,\n        )\n        val entity = IssuedDocumentEntity(''')
replace_all_exact(p,
'''            snapshot.issueZoneId == command.issueZoneId &&\n            snapshot.identity == command.toIdentitySnapshot()''',
'''            snapshot.issueZoneId == command.issueZoneId &&\n            snapshot.template.id == command.templateId &&\n            snapshot.identity == command.toIdentitySnapshot()''',
2)
replace_once(p,
'''    private fun PrepareDebtReceiptCommand.toIdentitySnapshot() = DocumentIdentitySnapshot(''',
'''    private suspend fun ensureBuiltInDocumentTemplates() {\n        DocumentTemplateCatalog.builtIns.forEach { record ->\n            templateDao.insert(record.toEntity())\n        }\n    }\n\n    private suspend fun requireDocumentTemplate(id: String): DocumentTemplateRecord {\n        ensureBuiltInDocumentTemplates()\n        return templateDao.findById(id)?.toRecord()\n            ?: throw RecordNotFoundException("Document template $id was not found.")\n    }\n\n    private fun DocumentTemplateRecord.toEntity() = DocumentTemplateEntity(\n        id = id,\n        displayName = displayName,\n        style = style.name,\n        showPhone = showPhone,\n        showFooter = showFooter,\n        showBalance = showBalance,\n        showNotes = showNotes,\n        isDefault = isDefault,\n        isBuiltIn = isBuiltIn,\n        createdAt = createdAt.toEpochMilli(),\n        updatedAt = updatedAt.toEpochMilli(),\n    )\n\n    private fun DocumentTemplateEntity.toRecord() = DocumentTemplateRecord(\n        id = id,\n        displayName = displayName,\n        style = DocumentTemplateStyle.valueOf(style),\n        showPhone = showPhone,\n        showFooter = showFooter,\n        showBalance = showBalance,\n        showNotes = showNotes,\n        isDefault = isDefault,\n        isBuiltIn = isBuiltIn,\n        createdAt = Instant.ofEpochMilli(createdAt),\n        updatedAt = Instant.ofEpochMilli(updatedAt),\n    )\n\n    private fun PrepareDebtReceiptCommand.toIdentitySnapshot() = DocumentIdentitySnapshot(''')
replace_once(p,
'''        const val DEBT_RECEIPT_SNAPSHOT_VERSION = 1\n        const val ACCOUNT_STATEMENT_SNAPSHOT_VERSION = 1''',
'''        const val DEBT_RECEIPT_SNAPSHOT_VERSION = 2\n        const val ACCOUNT_STATEMENT_SNAPSHOT_VERSION = 2''')

# Room v11: one mutable template catalog table. Issued docs deliberately snapshot rather than FK to live template.
p = 'app/src/main/java/com/wasl/app/data/local/WaslDatabase.kt'
replace_once(p,
'''import com.wasl.app.data.local.dao.DocumentIdentityDao\nimport com.wasl.app.data.local.dao.GroupExpenseDao''',
'''import com.wasl.app.data.local.dao.DocumentIdentityDao\nimport com.wasl.app.data.local.dao.DocumentTemplateDao\nimport com.wasl.app.data.local.dao.GroupExpenseDao''')
replace_once(p,
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.GroupExpenseEntity''',
'''import com.wasl.app.data.local.entity.DocumentIdentityEntity\nimport com.wasl.app.data.local.entity.DocumentTemplateEntity\nimport com.wasl.app.data.local.entity.GroupExpenseEntity''')
replace_once(p,
'''        DocumentIdentityEntity::class,\n        IssuedDocumentEntity::class,''',
'''        DocumentIdentityEntity::class,\n        DocumentTemplateEntity::class,\n        IssuedDocumentEntity::class,''')
replace_once(p, '    version = 10,', '    version = 11,')
replace_once(p,
'''    abstract fun documentIdentityDao(): DocumentIdentityDao\n    abstract fun issuedDocumentDao(): IssuedDocumentDao''',
'''    abstract fun documentIdentityDao(): DocumentIdentityDao\n    abstract fun documentTemplateDao(): DocumentTemplateDao\n    abstract fun issuedDocumentDao(): IssuedDocumentDao''')
migration = '''\n        val MIGRATION_10_11 = object : Migration(10, 11) {\n            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {\n                db.execSQL(\n                    \"\"\"\n                    CREATE TABLE IF NOT EXISTS `document_templates` (\n                        `id` TEXT NOT NULL,\n                        `display_name` TEXT NOT NULL,\n                        `style` TEXT NOT NULL,\n                        `show_phone` INTEGER NOT NULL,\n                        `show_footer` INTEGER NOT NULL,\n                        `show_balance` INTEGER NOT NULL,\n                        `show_notes` INTEGER NOT NULL,\n                        `is_default` INTEGER NOT NULL,\n                        `is_built_in` INTEGER NOT NULL,\n                        `created_at` INTEGER NOT NULL,\n                        `updated_at` INTEGER NOT NULL,\n                        PRIMARY KEY(`id`)\n                    )\n                    \"\"\".trimIndent(),\n                )\n                db.execSQL(\"CREATE INDEX IF NOT EXISTS `index_document_templates_is_default` ON `document_templates` (`is_default`)\")\n                db.execSQL(\"CREATE INDEX IF NOT EXISTS `index_document_templates_style` ON `document_templates` (`style`)\")\n                db.execSQL(\"INSERT OR IGNORE INTO document_templates VALUES ('builtin-minimal','بسيط','MINIMAL',0,0,1,0,0,1,0,0)\")\n                db.execSQL(\"INSERT OR IGNORE INTO document_templates VALUES ('builtin-business','عملي','BUSINESS',1,1,1,1,1,1,0,0)\")\n                db.execSQL(\"INSERT OR IGNORE INTO document_templates VALUES ('builtin-classic','كلاسيكي','CLASSIC',1,1,1,1,0,1,0,0)\")\n                db.execSQL(\"INSERT OR IGNORE INTO document_templates VALUES ('builtin-compact','مضغوط','COMPACT',0,0,1,0,0,1,0,0)\")\n                db.execSQL(\"INSERT OR IGNORE INTO document_templates VALUES ('builtin-modern','حديث','MODERN',1,1,1,1,0,1,0,0)\")\n            }\n        }\n\n'''
replace_once(p, '        val ALL_MIGRATIONS = arrayOf(', migration + '        val ALL_MIGRATIONS = arrayOf(')
replace_once(p,
'''            MIGRATION_8_9,\n            MIGRATION_9_10,\n        )''',
'''            MIGRATION_8_9,\n            MIGRATION_9_10,\n            MIGRATION_10_11,\n        )''')

# Backup follows DB v11 in the same foundation batch.
p = 'app/src/main/java/com/wasl/app/backup/BackupService.kt'
replace_once(p, '        const val SCHEMA_VERSION = 10', '        const val SCHEMA_VERSION = 11')
replace_once(p,
'''            "document_identities",\n            "issued_documents",''',
'''            "document_identities",\n            "document_templates",\n            "issued_documents",''')
replace_once(p,
'''            "document_identities" to "SELECT * FROM document_identities ORDER BY created_at, id",\n            "issued_documents" to''',
'''            "document_identities" to "SELECT * FROM document_identities ORDER BY created_at, id",\n            "document_templates" to "SELECT * FROM document_templates ORDER BY is_default DESC, display_name, id",\n            "issued_documents" to''')

# Existing encrypted-backup acceptance assertions now expect the schema they actually export/restore.
for test in (ROOT / 'app/src/androidTest').rglob('*.kt'):
    text = test.read_text(encoding='utf-8')
    updated = re.sub(r'assertEquals\(10, (backup|restored)\.schemaVersion\)', r'assertEquals(11, \1.schemaVersion)', text)
    if updated != text:
        test.write_text(updated, encoding='utf-8')

write('app/src/androidTest/java/com/wasl/app/data/local/DocumentTemplateMigrationInstrumentedTest.kt', '''package com.wasl.app.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTemplateMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionTenMigratesToElevenWithFiveBuiltInTemplatesAndOneDefault() {
        val databaseName = "wasl-schema-v10-document-templates.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        migrationHelper.createDatabase(databaseName, 10).close()

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            11,
            true,
            WaslDatabase.MIGRATION_10_11,
        ).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM document_templates").use {
                check(it.moveToFirst())
                assertEquals(5L, it.getLong(0))
            }
            migrated.query("SELECT id, style FROM document_templates WHERE is_default = 1").use {
                check(it.moveToFirst())
                assertEquals("builtin-business", it.getString(0))
                assertEquals("BUSINESS", it.getString(1))
                assertEquals(false, it.moveToNext())
            }
            assertIndex(migrated, "index_document_templates_is_default")
            assertIndex(migrated, "index_document_templates_style")
        }
        context.deleteDatabase(databaseName)
    }

    private fun assertIndex(database: androidx.sqlite.db.SupportSQLiteDatabase, name: String) {
        database.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name),
        ).use {
            check(it.moveToFirst())
            assertEquals(1L, it.getLong(0), "Missing index $name")
        }
    }
}
''')

write('app/src/androidTest/java/com/wasl/app/data/local/DocumentTemplateRepositoryInstrumentedTest.kt', '''package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentTemplateCatalog
import com.wasl.app.data.DocumentTemplateStyle
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTemplateRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-document-template-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshDatabaseSeedsFiveTemplatesAndBusinessIsDefault() = kotlinx.coroutines.runBlocking {
        val templates = repository.getDocumentTemplates()
        assertEquals(5, templates.size)
        assertEquals(5, templates.map { it.id }.distinct().size)
        val default = requireNotNull(repository.getDefaultDocumentTemplate())
        assertEquals(DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID, default.id)
        assertEquals(DocumentTemplateStyle.BUSINESS, default.style)
        assertTrue(default.isDefault)
    }

    @Test
    fun issuedReceiptKeepsTemplateSnapshotWhenLiveTemplateRowChanges() = kotlinx.coroutines.runBlocking {
        val openedAt = Instant.parse("2026-08-28T10:00:00Z")
        val personId = PersonId("template-person")
        val debtId = DebtId("template-debt")
        val paymentId = LedgerEntryId("template-payment")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = personId,
                debtId = debtId,
                personName = "عميل القالب",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000, CurrencyCode.of("YER")),
                openedAt = openedAt,
                createdAt = openedAt,
            ),
        )
        repository.recordPayment(
            RecordPaymentCommand(
                commandId = "template-payment-command",
                entryId = paymentId,
                debtId = debtId,
                amount = Money(20_000, CurrencyCode.of("YER")),
                paidAt = openedAt.plusSeconds(60),
                recordedAt = openedAt.plusSeconds(60),
            ),
        )
        val issued = repository.preparePaymentReceipt(
            PreparePaymentReceiptCommand(
                commandId = "template-document-command",
                documentId = "template-document",
                identityId = "template-identity",
                debtId = debtId,
                paymentId = paymentId,
                issuerDisplayName = "وَصل",
                issuedAt = openedAt.plusSeconds(120),
                issueZoneId = ZoneId.of("Asia/Aden"),
                templateId = DocumentTemplateCatalog.MINIMAL_ID,
            ),
        )
        val snapshot = issued.snapshot as PaymentReceiptSnapshot
        assertEquals(DocumentTemplateStyle.MINIMAL, snapshot.template.style)
        assertEquals("بسيط", snapshot.template.displayName)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE document_templates SET display_name = 'متغير' WHERE id = ?",
            arrayOf(DocumentTemplateCatalog.MINIMAL_ID),
        )
        val reread = requireNotNull(repository.getIssuedDocument(issued.id)).snapshot as PaymentReceiptSnapshot
        assertEquals("بسيط", reread.template.displayName)
        assertEquals(DocumentTemplateStyle.MINIMAL, reread.template.style)
    }
}
''')

# Guardrails: no accidental schema-v10 backup acceptance remains.
for test in (ROOT / 'app/src/androidTest').rglob('*.kt'):
    text = test.read_text(encoding='utf-8')
    if 'schemaVersion' in text and re.search(r'assertEquals\(10,\s*(?:backup|restored)\.schemaVersion\)', text):
        raise SystemExit(f'stale backup schema assertion in {test}')

print('Document Template v11 foundation patch applied successfully.')
