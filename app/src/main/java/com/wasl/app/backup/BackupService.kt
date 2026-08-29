package com.wasl.app.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AndroidDocumentBannerAssetStore
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.app.document.sha256Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface BackupService {
    suspend fun create(password: CharArray): BackupCreated

    suspend fun restore(backupBytes: ByteArray, password: CharArray): BackupRestored
}

data class BackupCreated(
    val bytes: ByteArray,
    val createdAt: Instant,
    val rowCount: Int,
    val documentCount: Int,
    val schemaVersion: Int,
)

data class BackupRestored(
    val createdAt: Instant,
    val restoredAt: Instant,
    val rowCount: Int,
    val documentCount: Int,
    val schemaVersion: Int,
)

class AndroidBackupService(
    context: Context,
    private val database: WaslDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : BackupService {
    private val appContext = context.applicationContext
    private val filesDir = appContext.filesDir

    override suspend fun create(password: CharArray): BackupCreated = withContext(Dispatchers.IO) {
        requirePassword(password)
        val snapshot = exportSnapshot(database.openHelper.writableDatabase)
        val documents = exportReadyDocuments(snapshot.readyDocuments)
        val attachments = exportAttachments(snapshot.attachments)
        val banners = exportBanners(snapshot.banners)
        val payload = BackupPayload(
            schemaVersion = SCHEMA_VERSION,
            tables = snapshot.tables,
            documentFiles = documents,
            attachmentFiles = attachments,
            bannerFiles = banners,
        )
        val createdAt = Instant.now(clock)
        val bytes = BackupEnvelope.seal(payload, createdAt, password)
        BackupCreated(
            bytes = bytes,
            createdAt = createdAt,
            rowCount = snapshot.tables.sumOf { it.rows.size },
            documentCount = documents.size + attachments.size + banners.size,
            schemaVersion = SCHEMA_VERSION,
        )
    }

    override suspend fun restore(
        backupBytes: ByteArray,
        password: CharArray,
    ): BackupRestored = withContext(Dispatchers.IO) {
        requirePassword(password)
        val opened = BackupEnvelope.open(backupBytes, password)
        val payload = opened.payload
        require(payload.schemaVersion == SCHEMA_VERSION) {
            "نسخة وَصل تستخدم إصدار قاعدة بيانات غير مدعوم (${payload.schemaVersion})."
        }
        validatePayloadShape(payload)

        val stagedRoot = File(filesDir, ".wasl-restore-${UUID.randomUUID()}")
        val stagedDocuments = File(stagedRoot, DOCUMENTS_DIRECTORY)
        val stagedAttachments = File(stagedRoot, ATTACHMENTS_DIRECTORY)
        val stagedBanners = File(stagedRoot, BANNERS_DIRECTORY)
        check(stagedDocuments.mkdirs() && stagedAttachments.mkdirs() && stagedBanners.mkdirs()) {
            "تعذر تجهيز مساحة الاستعادة الآمنة."
        }
        try {
            stageAndValidateDocuments(payload.documentFiles, stagedDocuments)
            stageAndValidateAttachments(payload.attachmentFiles, stagedAttachments)
            stageAndValidateBanners(payload.bannerFiles, stagedBanners)
            validateInTemporaryDatabase(payload)
            replaceFilesAndDatabase(payload, stagedDocuments, stagedAttachments, stagedBanners)
        } finally {
            stagedRoot.deleteRecursively()
        }

        BackupRestored(
            createdAt = opened.createdAt,
            restoredAt = Instant.now(clock),
            rowCount = payload.tables.sumOf { it.rows.size },
            documentCount = payload.documentFiles.size + payload.attachmentFiles.size + payload.bannerFiles.size,
            schemaVersion = payload.schemaVersion,
        )
    }

    private fun exportSnapshot(db: SupportSQLiteDatabase): SnapshotExport {
        val dumps = mutableListOf<TableDump>()
        val readyDocuments = mutableListOf<VaultFileRef>()
        val attachments = mutableListOf<VaultFileRef>()
        val banners = mutableListOf<VaultFileRef>()
        db.beginTransaction()
        try {
            TABLES.forEach { table ->
                val query = EXPORT_QUERIES.getValue(table)
                db.query(query).use { cursor ->
                    val columns = cursor.columnNames.toList()
                    val rows = ArrayList<List<BackupCell>>(cursor.count)
                    val statusIndex = if (table == "issued_documents") columns.indexOf("status") else -1
                    val documentPathIndex = if (table == "issued_documents") columns.indexOf("pdf_relative_path") else -1
                    val documentHashIndex = if (table == "issued_documents") columns.indexOf("pdf_sha256") else -1
                    val attachmentPathIndex = if (table == "attachments") columns.indexOf("relative_path") else -1
                    val attachmentHashIndex = if (table == "attachments") columns.indexOf("sha256") else -1
                    val bannerPathIndex = if (table == "document_identities") columns.indexOf("banner_relative_path") else -1
                    val bannerHashIndex = if (table == "document_identities") columns.indexOf("banner_sha256") else -1
                    while (cursor.moveToNext()) {
                        val row = columns.indices.map { index -> cursor.backupCell(index) }
                        rows += row
                        if (
                            table == "issued_documents" &&
                            statusIndex >= 0 &&
                            cursor.getString(statusIndex) == READY_STATUS
                        ) {
                            val relativePath = cursor.getString(documentPathIndex)
                            val sha256 = cursor.getString(documentHashIndex)
                            require(!relativePath.isNullOrBlank() && !sha256.isNullOrBlank()) {
                                "مستند READY يفتقد مسار الملف أو بصمة السلامة."
                            }
                            readyDocuments += VaultFileRef(relativePath, sha256)
                        }
                        if (table == "attachments") {
                            val relativePath = cursor.getString(attachmentPathIndex)
                            val sha256 = cursor.getString(attachmentHashIndex)
                            require(!relativePath.isNullOrBlank() && !sha256.isNullOrBlank()) {
                                "مرفق يفتقد مسار الملف أو بصمة السلامة."
                            }
                            attachments += VaultFileRef(relativePath, sha256)
                        }
                        if (table == "document_identities") {
                            val relativePath = if (cursor.isNull(bannerPathIndex)) null else cursor.getString(bannerPathIndex)
                            val sha256 = if (cursor.isNull(bannerHashIndex)) null else cursor.getString(bannerHashIndex)
                            require((relativePath == null) == (sha256 == null)) {
                                "هوية مستند تحتوي مرجع بانر غير مكتمل."
                            }
                            if (relativePath != null && sha256 != null) {
                                validateBannerReference(relativePath, sha256)
                                banners += VaultFileRef(relativePath, sha256)
                            }
                        }
                    }
                    dumps += TableDump(table, columns, rows)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return SnapshotExport(dumps, readyDocuments, attachments, banners)
    }

    private fun exportReadyDocuments(references: List<VaultFileRef>): List<BackupDocumentFile> =
        references.distinctBy { it.relativePath }.map { reference ->
            val file = ReceiptFileAccess.resolve(filesDir, reference.relativePath)
            exportVaultFile(reference, file, MAX_DOCUMENT_BYTES)
        }

    private fun exportAttachments(references: List<VaultFileRef>): List<BackupDocumentFile> =
        references.distinctBy { it.relativePath }.map { reference ->
            val file = resolveLiveAttachment(reference.relativePath)
            exportVaultFile(reference, file, MAX_ATTACHMENT_BYTES)
        }

    private fun exportBanners(references: List<VaultFileRef>): List<BackupDocumentFile> =
        references.distinctBy { it.relativePath }.map { reference ->
            validateBannerReference(reference.relativePath, reference.sha256)
            val file = resolveLiveBanner(reference.relativePath)
            val exported = exportVaultFile(reference, file, MAX_BANNER_BYTES)
            require(BitmapFactory.decodeFile(file.absolutePath) != null) {
                "صورة رأس مستند محفوظة غير قابلة للقراءة."
            }
            exported
        }

    private fun exportVaultFile(
        reference: VaultFileRef,
        file: File,
        maxBytes: Int,
    ): BackupDocumentFile {
        require(file.isFile) { "ملف محفوظ غير موجود: ${reference.relativePath}" }
        require(file.length() in 1..maxBytes.toLong()) { "حجم ملف محفوظ غير صالح." }
        val actualHash = file.sha256Hex()
        require(actualHash.equals(reference.sha256, ignoreCase = true)) {
            "فشل تحقق سلامة ملف قبل النسخ الاحتياطي."
        }
        return BackupDocumentFile(
            relativePath = reference.relativePath,
            sha256 = actualHash,
            contentBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
        )
    }

    private fun validatePayloadShape(payload: BackupPayload) {
        val byName = payload.tables.groupBy { it.name }
        require(byName.keys == TABLES.toSet() && byName.values.all { it.size == 1 }) {
            "محتوى النسخة الاحتياطية غير مكتمل أو يحتوي جداول غير متوقعة."
        }
        require(payload.tables.sumOf { it.rows.size } <= MAX_ROWS) {
            "النسخة الاحتياطية تتجاوز الحد الآمن لعدد السجلات."
        }
        payload.tables.forEach { table ->
            require(table.columns.isNotEmpty() && table.columns.distinct().size == table.columns.size) {
                "تعريف أعمدة غير صالح في ${table.name}."
            }
            require(table.rows.all { it.size == table.columns.size }) {
                "صف غير متوافق مع أعمدة الجدول ${table.name}."
            }
        }

        validateVaultFileSet(
            records = readyDocumentRefsFrom(payload),
            files = payload.documentFiles,
            label = "المستندات",
        )
        validateVaultFileSet(
            records = attachmentRefsFrom(payload),
            files = payload.attachmentFiles,
            label = "المرفقات",
        )
        validateVaultFileSet(
            records = bannerRefsFrom(payload),
            files = payload.bannerFiles,
            label = "صور رأس المستند",
        )
    }

    private fun validateVaultFileSet(
        records: Map<String, String>,
        files: List<BackupDocumentFile>,
        label: String,
    ) {
        val backupFiles = files.associateBy { it.relativePath }
        require(backupFiles.size == files.size) { "النسخة تحتوي ملفات $label مكررة." }
        require(backupFiles.keys == records.keys) { "ملفات $label لا تطابق سجلاتها." }
        records.forEach { (path, hash) ->
            require(backupFiles.getValue(path).sha256.equals(hash, ignoreCase = true)) {
                "بصمة ملف $label في النسخة لا تطابق السجل."
            }
        }
    }

    private fun readyDocumentRefsFrom(payload: BackupPayload): Map<String, String> {
        val table = payload.tables.single { it.name == "issued_documents" }
        val statusIndex = table.columns.indexOf("status")
        val pathIndex = table.columns.indexOf("pdf_relative_path")
        val hashIndex = table.columns.indexOf("pdf_sha256")
        require(statusIndex >= 0 && pathIndex >= 0 && hashIndex >= 0) {
            "جدول المستندات لا يحتوي الحقول المطلوبة."
        }
        return buildMap {
            table.rows.forEach { row ->
                if (row[statusIndex].asText() == READY_STATUS) {
                    val path = requireNotNull(row[pathIndex].asText()).also {
                        require(it.isNotBlank()) { "مسار مستند READY فارغ." }
                    }
                    val hash = requireNotNull(row[hashIndex].asText()).also {
                        require(SHA256_REGEX.matches(it)) { "بصمة مستند READY غير صالحة." }
                    }
                    require(put(path, hash) == null) { "مسار مستند READY مكرر." }
                }
            }
        }
    }

    private fun attachmentRefsFrom(payload: BackupPayload): Map<String, String> {
        val table = payload.tables.single { it.name == "attachments" }
        val pathIndex = table.columns.indexOf("relative_path")
        val hashIndex = table.columns.indexOf("sha256")
        require(pathIndex >= 0 && hashIndex >= 0) { "جدول المرفقات لا يحتوي الحقول المطلوبة." }
        return buildMap {
            table.rows.forEach { row ->
                val path = requireNotNull(row[pathIndex].asText()).also {
                    require(it.isNotBlank()) { "مسار مرفق فارغ." }
                }
                val hash = requireNotNull(row[hashIndex].asText()).also {
                    require(SHA256_REGEX.matches(it)) { "بصمة مرفق غير صالحة." }
                }
                require(put(path, hash) == null) { "مسار مرفق مكرر." }
            }
        }
    }

    private fun bannerRefsFrom(payload: BackupPayload): Map<String, String> {
        val table = payload.tables.single { it.name == "document_identities" }
        val pathIndex = table.columns.indexOf("banner_relative_path")
        val hashIndex = table.columns.indexOf("banner_sha256")
        require(pathIndex >= 0 && hashIndex >= 0) { "جدول هويات المستند لا يحتوي حقول البانر المطلوبة." }
        return buildMap {
            table.rows.forEach { row ->
                val path = row[pathIndex].asText()
                val hash = row[hashIndex].asText()
                require((path == null) == (hash == null)) { "مرجع بانر الهوية غير مكتمل." }
                if (path != null && hash != null) {
                    validateBannerReference(path, hash)
                    val previous = put(path, hash)
                    require(previous == null || previous.equals(hash, ignoreCase = true)) {
                        "مسار بانر واحد مرتبط ببصمات مختلفة."
                    }
                }
            }
        }
    }

    private fun stageAndValidateDocuments(
        files: List<BackupDocumentFile>,
        stagedDocuments: File,
    ) {
        files.forEach { document ->
            val safeTarget = resolveStagedDocument(stagedDocuments, document.relativePath)
            stageFile(document, safeTarget, MAX_DOCUMENT_BYTES, "المستند")
        }
    }

    private fun stageAndValidateAttachments(
        files: List<BackupDocumentFile>,
        stagedAttachments: File,
    ) {
        files.forEach { attachment ->
            val safeTarget = resolveStagedAttachment(stagedAttachments, attachment.relativePath)
            stageFile(attachment, safeTarget, MAX_ATTACHMENT_BYTES, "المرفق")
        }
    }

    private fun stageAndValidateBanners(
        files: List<BackupDocumentFile>,
        stagedBanners: File,
    ) {
        files.forEach { banner ->
            validateBannerReference(banner.relativePath, banner.sha256)
            val safeTarget = resolveStagedBanner(stagedBanners, banner.relativePath)
            stageFile(banner, safeTarget, MAX_BANNER_BYTES, "صورة رأس المستند")
            require(BitmapFactory.decodeFile(safeTarget.absolutePath) != null) {
                "صورة رأس المستند داخل النسخة غير قابلة للقراءة."
            }
        }
    }

    private fun stageFile(
        source: BackupDocumentFile,
        target: File,
        maxBytes: Int,
        label: String,
    ) {
        require(SHA256_REGEX.matches(source.sha256)) { "بصمة ملف $label غير صالحة." }
        val bytes = try {
            Base64.getDecoder().decode(source.contentBase64)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("ترميز ملف $label داخل النسخة غير صالح.", error)
        }
        require(bytes.isNotEmpty() && bytes.size <= maxBytes) { "حجم ملف $label غير آمن." }
        target.outputStream().buffered().use { it.write(bytes) }
        require(target.sha256Hex().equals(source.sha256, ignoreCase = true)) {
            "فشل تحقق سلامة ملف $label داخل النسخة الاحتياطية."
        }
    }

    private fun resolveStagedDocument(stagedDocuments: File, relativePath: String): File {
        val fileName = validateSingleChildPath(relativePath, DOCUMENTS_DIRECTORY, "مستند")
        require(fileName.endsWith(".pdf", ignoreCase = true)) { "ملف المستند يجب أن يكون PDF." }
        val target = File(stagedDocuments, fileName).canonicalFile
        require(target.parentFile == stagedDocuments.canonicalFile) { "مسار مستند غير آمن." }
        return target
    }

    private fun resolveStagedAttachment(stagedAttachments: File, relativePath: String): File {
        val fileName = validateSingleChildPath(relativePath, ATTACHMENTS_DIRECTORY, "مرفق")
        require(fileName.endsWith(".blob", ignoreCase = true)) { "امتداد ملف المرفق غير مدعوم." }
        val target = File(stagedAttachments, fileName).canonicalFile
        require(target.parentFile == stagedAttachments.canonicalFile) { "مسار مرفق غير آمن." }
        return target
    }

    private fun resolveStagedBanner(stagedBanners: File, relativePath: String): File {
        val fileName = validateSingleChildPath(relativePath, BANNERS_DIRECTORY, "صورة رأس المستند")
        require(fileName.matches(BANNER_FILE_REGEX)) { "اسم ملف صورة رأس المستند غير صالح." }
        val target = File(stagedBanners, fileName).canonicalFile
        require(target.parentFile == stagedBanners.canonicalFile) { "مسار صورة رأس المستند غير آمن." }
        return target
    }

    private fun validateSingleChildPath(relativePath: String, directory: String, label: String): String {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) { "مسار $label غير آمن." }
        val normalized = relativePath.replace('\\', '/')
        require(normalized.startsWith("$directory/") && normalized.count { it == '/' } == 1) {
            "مسار $label خارج المجلد المسموح."
        }
        return normalized.substringAfter('/').also {
            require(it.isNotBlank() && it != "." && it != "..") { "اسم ملف $label غير صالح." }
        }
    }

    private fun resolveLiveAttachment(relativePath: String): File {
        val fileName = validateSingleChildPath(relativePath, ATTACHMENTS_DIRECTORY, "مرفق")
        val root = File(filesDir, ATTACHMENTS_DIRECTORY).canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.parentFile == root) { "مسار مرفق غير آمن." }
        return file
    }

    private fun resolveLiveBanner(relativePath: String): File {
        val fileName = validateSingleChildPath(relativePath, BANNERS_DIRECTORY, "صورة رأس المستند")
        require(fileName.matches(BANNER_FILE_REGEX)) { "اسم ملف صورة رأس المستند غير صالح." }
        val root = File(filesDir, BANNERS_DIRECTORY).canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.parentFile == root) { "مسار صورة رأس المستند غير آمن." }
        return file
    }

    private fun validateBannerReference(relativePath: String, sha256: String) {
        require(SHA256_REGEX.matches(sha256)) { "بصمة صورة رأس المستند غير صالحة." }
        val fileName = validateSingleChildPath(relativePath, BANNERS_DIRECTORY, "صورة رأس المستند")
        require(fileName.matches(BANNER_FILE_REGEX)) { "اسم ملف صورة رأس المستند غير صالح." }
        require(fileName.substringBeforeLast('.').equals(sha256, ignoreCase = true)) {
            "مسار صورة رأس المستند لا يطابق بصمتها."
        }
    }

    private fun validateInTemporaryDatabase(payload: BackupPayload) {
        val validationDb = Room.inMemoryDatabaseBuilder(
            appContext,
            WaslDatabase::class.java,
        ).build()
        try {
            replaceDatabaseContents(validationDb.openHelper.writableDatabase, payload)
        } finally {
            validationDb.close()
        }
    }

    private fun replaceFilesAndDatabase(
        payload: BackupPayload,
        stagedDocuments: File,
        stagedAttachments: File,
        stagedBanners: File,
    ) {
        val liveDocuments = File(filesDir, DOCUMENTS_DIRECTORY)
        val liveAttachments = File(filesDir, ATTACHMENTS_DIRECTORY)
        val liveBanners = File(filesDir, BANNERS_DIRECTORY)
        val rollbackDocuments = File(filesDir, ".wasl-documents-rollback-${UUID.randomUUID()}")
        val rollbackAttachments = File(filesDir, ".wasl-attachments-rollback-${UUID.randomUUID()}")
        val rollbackBanners = File(filesDir, ".wasl-banners-rollback-${UUID.randomUUID()}")
        var documentsMoved = false
        var attachmentsMoved = false
        var bannersMoved = false
        var newDocumentsMoved = false
        var newAttachmentsMoved = false
        var newBannersMoved = false
        try {
            if (liveDocuments.exists()) {
                moveDirectory(liveDocuments, rollbackDocuments)
                documentsMoved = true
            }
            if (liveAttachments.exists()) {
                moveDirectory(liveAttachments, rollbackAttachments)
                attachmentsMoved = true
            }
            if (liveBanners.exists()) {
                moveDirectory(liveBanners, rollbackBanners)
                bannersMoved = true
            }
            moveDirectory(stagedDocuments, liveDocuments)
            newDocumentsMoved = true
            moveDirectory(stagedAttachments, liveAttachments)
            newAttachmentsMoved = true
            moveDirectory(stagedBanners, liveBanners)
            newBannersMoved = true

            replaceDatabaseContents(database.openHelper.writableDatabase, payload)

            if (documentsMoved) rollbackDocuments.deleteRecursively()
            if (attachmentsMoved) rollbackAttachments.deleteRecursively()
            if (bannersMoved) rollbackBanners.deleteRecursively()
        } catch (error: Exception) {
            if (newDocumentsMoved && liveDocuments.exists()) liveDocuments.deleteRecursively()
            if (newAttachmentsMoved && liveAttachments.exists()) liveAttachments.deleteRecursively()
            if (newBannersMoved && liveBanners.exists()) liveBanners.deleteRecursively()
            if (documentsMoved && rollbackDocuments.exists()) {
                runCatching { moveDirectory(rollbackDocuments, liveDocuments) }
            }
            if (attachmentsMoved && rollbackAttachments.exists()) {
                runCatching { moveDirectory(rollbackAttachments, liveAttachments) }
            }
            if (bannersMoved && rollbackBanners.exists()) {
                runCatching { moveDirectory(rollbackBanners, liveBanners) }
            }
            throw error
        } finally {
            rollbackDocuments.deleteRecursively()
            rollbackAttachments.deleteRecursively()
            rollbackBanners.deleteRecursively()
        }
    }

    private fun replaceDatabaseContents(db: SupportSQLiteDatabase, payload: BackupPayload) {
        val tableByName = payload.tables.associateBy { it.name }
        db.beginTransaction()
        try {
            validateSchemaColumns(db, tableByName)
            CLEAR_ORDER.forEach { table ->
                if (table == "ledger_entries") {
                    db.execSQL("DELETE FROM `ledger_entries` WHERE `reverses_entry_id` IS NOT NULL")
                }
                db.execSQL("DELETE FROM `$table`")
            }
            TABLES.forEach { tableName ->
                val table = tableByName.getValue(tableName)
                table.rows.forEach { row ->
                    val values = ContentValues(table.columns.size)
                    table.columns.forEachIndexed { index, column ->
                        values.putBackupCell(column, row[index])
                    }
                    val result = db.insert(tableName, SQLiteDatabase.CONFLICT_ABORT, values)
                    check(result != -1L) { "فشل إدراج سجل أثناء الاستعادة في $tableName." }
                }
            }
            validateDatabaseInvariants(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun validateSchemaColumns(
        db: SupportSQLiteDatabase,
        tableByName: Map<String, TableDump>,
    ) {
        TABLES.forEach { tableName ->
            val actual = mutableListOf<String>()
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) actual += cursor.getString(nameIndex)
            }
            require(actual == tableByName.getValue(tableName).columns) {
                "بنية الجدول $tableName لا تطابق إصدار النسخة الاحتياطية."
            }
        }
    }

    private fun validateDatabaseInvariants(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor ->
            require(!cursor.moveToFirst()) { "النسخة تحتوي مراجع بيانات غير متسقة." }
        }
        require(singleLong(db, "SELECT COUNT(*) FROM debts WHERE original_amount_minor <= 0") == 0L) {
            "النسخة تحتوي مبلغ دين غير صالح."
        }
        require(singleLong(db, "SELECT COUNT(*) FROM group_expenses WHERE total_amount_minor <= 0") == 0L) {
            "النسخة تحتوي إجمالي عملية جماعية غير صالح."
        }
        require(singleLong(db, "SELECT COUNT(*) FROM group_expense_shares WHERE amount_minor <= 0") == 0L) {
            "النسخة تحتوي حصة جماعية غير صالحة."
        }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expenses
                WHERE trim(id) = ''
                   OR trim(command_id) = ''
                   OR direction NOT IN ('RECEIVABLE', 'PAYABLE')
                   OR currency_code NOT GLOB '[A-Z][A-Z][A-Z]'
                   OR trim(description) = ''
                   OR (notes IS NOT NULL AND trim(notes) = '')
                   OR created_at < occurred_at
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي عملية جماعية ببيانات أساسية غير متسقة." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expense_shares
                WHERE trim(id) = ''
                   OR trim(group_expense_id) = ''
                   OR trim(debt_id) = ''
                   OR trim(person_id) = ''
                   OR sequence_number <= 0
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي حصة جماعية ببيانات تعريف غير صالحة." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM (
                    SELECT g.id
                    FROM group_expenses g
                    LEFT JOIN group_expense_shares s ON s.group_expense_id = g.id
                    GROUP BY g.id, g.total_amount_minor
                    HAVING COUNT(s.id) < 2 OR COALESCE(SUM(s.amount_minor), 0) != g.total_amount_minor
                )
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي عملية جماعية لا يطابق مجموع حصصها الإجمالي." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM (
                    SELECT group_expense_id
                    FROM group_expense_shares
                    GROUP BY group_expense_id
                    HAVING MIN(sequence_number) != 1
                       OR MAX(sequence_number) != COUNT(*)
                       OR COUNT(DISTINCT sequence_number) != COUNT(*)
                )
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي تسلسل حصص جماعية غير متصل." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expense_shares s
                JOIN group_expenses g ON g.id = s.group_expense_id
                JOIN debts d ON d.id = s.debt_id
                WHERE d.person_id != s.person_id
                   OR d.direction != g.direction
                   OR d.currency_code != g.currency_code
                   OR d.original_amount_minor != s.amount_minor
                   OR d.opened_at != g.occurred_at
                   OR d.created_at != g.created_at
                   OR d.description != g.description
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي حصة جماعية لا تطابق الدين المرتبط بها." }
        require(singleLong(db, "SELECT COUNT(*) FROM installments WHERE amount_minor <= 0") == 0L) {
            "النسخة تحتوي مبلغ قسط غير صالح."
        }
        require(singleLong(db, "SELECT COUNT(*) FROM attachments WHERE size_bytes <= 0") == 0L) {
            "النسخة تحتوي مرفقًا بحجم غير صالح."
        }
        require(
            singleLong(db, "SELECT COUNT(*) FROM attachments WHERE relative_path NOT LIKE 'attachments/%.blob'") == 0L,
        ) { "النسخة تحتوي مسار مرفق غير صالح." }
        require(
            singleLong(
                db,
                "SELECT COUNT(*) FROM document_identities WHERE (banner_relative_path IS NULL) != (banner_sha256 IS NULL)",
            ) == 0L,
        ) { "النسخة تحتوي مرجع بانر هوية غير مكتمل." }
        require(
            singleLong(
                db,
                "SELECT COUNT(*) FROM document_identities WHERE banner_relative_path IS NOT NULL AND banner_relative_path NOT LIKE 'document-banners/%.img'",
            ) == 0L,
        ) { "النسخة تحتوي مسار بانر هوية غير صالح." }
        require(
            singleLong(
                db,
                "SELECT COUNT(*) FROM ledger_entries WHERE amount_minor IS NOT NULL AND amount_minor <= 0",
            ) == 0L,
        ) { "النسخة تحتوي مبلغ حركة مالية غير صالح." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM ledger_entries l
                JOIN debts d ON d.id = l.debt_id
                WHERE l.currency_code IS NOT NULL AND l.currency_code != d.currency_code
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي حركة مالية بعملة لا تطابق الدين." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM installments i
                JOIN debts d ON d.id = i.debt_id
                WHERE i.currency_code != d.currency_code
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي قسطًا بعملة لا تطابق الدين." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM attachments a
                JOIN ledger_entries l ON l.id = a.ledger_entry_id
                WHERE a.ledger_entry_id IS NOT NULL AND l.debt_id != a.debt_id
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي مرفقًا مرتبطًا بحركة من حساب آخر." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM payment_claims c
                JOIN debts d ON d.id = c.debt_id
                WHERE d.direction != 'PAYABLE'
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي مطالبة سداد مرتبطة بدين ليس من نوع عليّ له." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM payment_claims
                WHERE follow_up_kind NOT IN ('TODAY', 'TOMORROW', 'SALARY', 'CUSTOM')
                   OR status NOT IN ('ACTIVE', 'RESOLVED', 'CANCELLED')
                   OR (follow_up_kind = 'CUSTOM' AND follow_up_date_epoch_day IS NULL)
                   OR (follow_up_kind = 'SALARY' AND follow_up_date_epoch_day IS NOT NULL)
                   OR (status = 'ACTIVE' AND (resolved_at IS NOT NULL OR resolution_command_id IS NOT NULL))
                   OR (status != 'ACTIVE' AND (resolved_at IS NULL OR resolution_command_id IS NULL))
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي سجل مطالبة سداد غير متسق." }
    }

    private fun singleLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "تعذر تنفيذ فحص سلامة قاعدة البيانات." }
            cursor.getLong(0)
        }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requirePassword(password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن $MIN_PASSWORD_LENGTH أحرف."
        }
    }

    private data class SnapshotExport(
        val tables: List<TableDump>,
        val readyDocuments: List<VaultFileRef>,
        val attachments: List<VaultFileRef>,
        val banners: List<VaultFileRef>,
    )

    private data class VaultFileRef(
        val relativePath: String,
        val sha256: String,
    )

    private companion object {
        const val SCHEMA_VERSION = 12
        const val MIN_PASSWORD_LENGTH = 8
        const val READY_STATUS = "READY"
        const val DOCUMENTS_DIRECTORY = "documents"
        const val ATTACHMENTS_DIRECTORY = "attachments"
        const val BANNERS_DIRECTORY = AndroidDocumentBannerAssetStore.DIRECTORY
        const val MAX_ROWS = 1_000_000
        const val MAX_DOCUMENT_BYTES = 64 * 1024 * 1024
        const val MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024
        const val MAX_BANNER_BYTES = AndroidDocumentBannerAssetStore.MAX_BANNER_BYTES
        val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        val BANNER_FILE_REGEX = Regex("[0-9a-f]{64}\\.img")

        val TABLES = listOf(
            "persons",
            "debts",
            "group_expenses",
            "group_expense_shares",
            "ledger_entries",
            "reminders",
            "audit_events",
            "document_identities",
            "document_templates",
            "issued_documents",
            "payment_promises",
            "payment_claims",
            "installment_plans",
            "installments",
            "attachments",
        )

        val CLEAR_ORDER = TABLES.asReversed()

        val EXPORT_QUERIES = mapOf(
            "persons" to "SELECT * FROM persons ORDER BY created_at, id",
            "debts" to "SELECT * FROM debts ORDER BY opened_at, id",
            "group_expenses" to "SELECT * FROM group_expenses ORDER BY occurred_at, id",
            "group_expense_shares" to "SELECT * FROM group_expense_shares ORDER BY group_expense_id, sequence_number, id",
            "ledger_entries" to "SELECT * FROM ledger_entries ORDER BY debt_id, sequence_number, id",
            "reminders" to "SELECT * FROM reminders ORDER BY created_at, id",
            "audit_events" to "SELECT * FROM audit_events ORDER BY occurred_at, id",
            "document_identities" to "SELECT * FROM document_identities ORDER BY created_at, id",
            "document_templates" to "SELECT * FROM document_templates ORDER BY is_default DESC, display_name, id",
            "issued_documents" to "SELECT * FROM issued_documents ORDER BY created_at, id",
            "payment_promises" to "SELECT * FROM payment_promises ORDER BY created_at, id",
            "payment_claims" to "SELECT * FROM payment_claims ORDER BY claimed_at, created_at, id",
            "installment_plans" to "SELECT * FROM installment_plans ORDER BY debt_id, revision_number, id",
            "installments" to "SELECT * FROM installments ORDER BY plan_id, sequence_number, id",
            "attachments" to "SELECT * FROM attachments ORDER BY debt_id, created_at, id",
        )
    }
}

@Serializable
internal data class BackupPayload(
    val schemaVersion: Int,
    val tables: List<TableDump>,
    val documentFiles: List<BackupDocumentFile>,
    val attachmentFiles: List<BackupDocumentFile> = emptyList(),
    val bannerFiles: List<BackupDocumentFile> = emptyList(),
)

@Serializable
internal data class TableDump(
    val name: String,
    val columns: List<String>,
    val rows: List<List<BackupCell>>,
)

@Serializable
internal data class BackupCell(
    val type: BackupCellType,
    val value: String? = null,
) {
    fun asText(): String? = when (type) {
        BackupCellType.NULL -> null
        BackupCellType.TEXT -> value
        else -> error("Expected TEXT or NULL cell, got $type")
    }
}

@Serializable
internal enum class BackupCellType {
    NULL,
    INTEGER,
    FLOAT,
    TEXT,
    BLOB,
}

@Serializable
internal data class BackupDocumentFile(
    val relativePath: String,
    val sha256: String,
    val contentBase64: String,
)

@Serializable
private data class BackupHeader(
    val format: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val kdf: String,
    val kdfIterations: Int,
    val saltBase64: String,
    val cipher: String,
    val ivBase64: String,
    val compression: String,
    val payloadSha256: String,
)

internal data class OpenedBackup(
    val createdAt: Instant,
    val payload: BackupPayload,
)

internal object BackupEnvelope {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val secureRandom = SecureRandom()

    fun seal(payload: BackupPayload, createdAt: Instant, password: CharArray): ByteArray {
        val payloadBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        require(payloadBytes.size <= MAX_PLAINTEXT_BYTES) { "محتوى النسخة الاحتياطية يتجاوز الحد الآمن." }
        val payloadHash = payloadBytes.sha256Hex()
        val compressed = gzip(payloadBytes)
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val header = BackupHeader(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            schemaVersion = payload.schemaVersion,
            createdAtEpochMillis = createdAt.toEpochMilli(),
            kdf = KDF,
            kdfIterations = KDF_ITERATIONS,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            cipher = CIPHER,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            compression = COMPRESSION,
            payloadSha256 = payloadHash,
        )
        val headerBytes = json.encodeToString(header).toByteArray(StandardCharsets.UTF_8)
        val encrypted = crypt(
            mode = Cipher.ENCRYPT_MODE,
            input = compressed,
            password = password,
            salt = salt,
            iv = iv,
            aad = headerBytes,
            iterations = KDF_ITERATIONS,
        )
        val output = ByteArrayOutputStream(MAGIC_BYTES.size + 4 + headerBytes.size + encrypted.size)
        DataOutputStream(output).use { data ->
            data.write(MAGIC_BYTES)
            data.writeInt(headerBytes.size)
            data.write(headerBytes)
            data.write(encrypted)
        }
        return output.toByteArray()
    }

    fun open(bytes: ByteArray, password: CharArray): OpenedBackup {
        require(bytes.size in MIN_BACKUP_BYTES..MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية غير صالح." }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC_BYTES.size)
        input.readFully(magic)
        require(magic.contentEquals(MAGIC_BYTES)) { "هذا الملف ليس نسخة وَصل احتياطية مدعومة." }
        val headerLength = input.readInt()
        require(headerLength in 2..MAX_HEADER_BYTES && headerLength < bytes.size - MAGIC_BYTES.size - 4) {
            "رأس النسخة الاحتياطية غير صالح."
        }
        val headerBytes = ByteArray(headerLength)
        input.readFully(headerBytes)
        val header = try {
            json.decodeFromString<BackupHeader>(headerBytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("تعذر قراءة معلومات النسخة الاحتياطية.", error)
        }
        validateHeader(header)
        val encrypted = input.readBytes()
        require(encrypted.isNotEmpty()) { "محتوى النسخة الاحتياطية فارغ." }
        val salt = decodeExactBase64(header.saltBase64, SALT_BYTES, "salt")
        val iv = decodeExactBase64(header.ivBase64, IV_BYTES, "iv")
        val compressed = try {
            crypt(
                mode = Cipher.DECRYPT_MODE,
                input = encrypted,
                password = password,
                salt = salt,
                iv = iv,
                aad = headerBytes,
                iterations = header.kdfIterations,
            )
        } catch (error: AEADBadTagException) {
            throw SecurityException("تعذر فتح النسخة: كلمة المرور غير صحيحة أو الملف تعرض للتغيير.", error)
        } catch (error: Exception) {
            throw SecurityException("تعذر فك تشفير النسخة الاحتياطية بأمان.", error)
        }
        val payloadBytes = gunzipLimited(compressed, MAX_PLAINTEXT_BYTES)
        require(payloadBytes.sha256Hex().equals(header.payloadSha256, ignoreCase = true)) {
            "فشل تحقق سلامة محتوى النسخة الاحتياطية."
        }
        val payload = try {
            json.decodeFromString<BackupPayload>(payloadBytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("محتوى النسخة الاحتياطية غير صالح.", error)
        }
        require(payload.schemaVersion == header.schemaVersion) {
            "إصدار قاعدة البيانات داخل النسخة غير متسق."
        }
        return OpenedBackup(
            createdAt = Instant.ofEpochMilli(header.createdAtEpochMillis),
            payload = payload,
        )
    }

    private fun validateHeader(header: BackupHeader) {
        require(header.format == FORMAT && header.formatVersion == FORMAT_VERSION) {
            "إصدار ملف النسخة الاحتياطية غير مدعوم."
        }
        require(header.schemaVersion > 0) { "إصدار قاعدة البيانات في النسخة غير صالح." }
        require(header.kdf == KDF && header.kdfIterations == KDF_ITERATIONS) {
            "إعداد اشتقاق المفتاح في النسخة غير مدعوم."
        }
        require(header.cipher == CIPHER && header.compression == COMPRESSION) {
            "خوارزمية حماية النسخة غير مدعومة."
        }
        require(SHA256_REGEX.matches(header.payloadSha256)) { "بصمة محتوى النسخة غير صالحة." }
        require(header.createdAtEpochMillis > 0) { "وقت إنشاء النسخة غير صالح." }
    }

    private fun crypt(
        mode: Int,
        input: ByteArray,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val derived = try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(mode, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(input)
        } finally {
            derived.fill(0)
        }
    }

    private fun decodeExactBase64(value: String, expectedBytes: Int, label: String): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("ترميز $label في النسخة غير صالح.", error)
        }
        require(decoded.size == expectedBytes) { "طول $label في النسخة غير صالح." }
        return decoded
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
        output.toByteArray()
    }

    private fun gunzipLimited(bytes: ByteArray, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(bytes.size * 2, limit))
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "محتوى النسخة بعد فك الضغط يتجاوز الحد الآمن." }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val FORMAT = "WASL_BACKUP"
    private const val FORMAT_VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 210_000
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val COMPRESSION = "gzip"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_BACKUP_BYTES = 256 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 256 * 1024 * 1024
    private const val MIN_BACKUP_BYTES = 64
    private val MAGIC_BYTES = "WASLBAK1".toByteArray(StandardCharsets.US_ASCII)
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
}

private fun Cursor.backupCell(index: Int): BackupCell = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> BackupCell(BackupCellType.NULL)
    Cursor.FIELD_TYPE_INTEGER -> BackupCell(BackupCellType.INTEGER, getLong(index).toString())
    Cursor.FIELD_TYPE_FLOAT -> BackupCell(BackupCellType.FLOAT, getDouble(index).toString())
    Cursor.FIELD_TYPE_STRING -> BackupCell(BackupCellType.TEXT, getString(index))
    Cursor.FIELD_TYPE_BLOB -> BackupCell(
        BackupCellType.BLOB,
        Base64.getEncoder().encodeToString(getBlob(index)),
    )
    else -> error("Unsupported SQLite value type ${getType(index)}")
}

private fun ContentValues.putBackupCell(column: String, cell: BackupCell) {
    when (cell.type) {
        BackupCellType.NULL -> putNull(column)
        BackupCellType.INTEGER -> put(column, requireNotNull(cell.value).toLong())
        BackupCellType.FLOAT -> put(column, requireNotNull(cell.value).toDouble())
        BackupCellType.TEXT -> put(column, requireNotNull(cell.value))
        BackupCellType.BLOB -> put(column, Base64.getDecoder().decode(requireNotNull(cell.value)))
    }
}
