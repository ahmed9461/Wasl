package com.wasl.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AddAttachmentCommand
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.local.RoomAttachmentStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentBackupRestoreInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var attachmentStore: RoomAttachmentStore
    private lateinit var backupService: BackupService
    private lateinit var testFilesDir: File
    private lateinit var isolatedContext: Context

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "attachment-backup-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testFilesDir
        }
        database = Room.inMemoryDatabaseBuilder(baseContext, WaslDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWaslRepository(database)
        attachmentStore = RoomAttachmentStore(isolatedContext, database)
        backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-27T14:00:00Z"), ZoneOffset.UTC),
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun encryptedBackupRestoresAttachmentMetadataAndExactPrivateFileBytes() = runTest {
        val debtId = createDebt("attachment-backup", 75_000L)
        val bytes = "%PDF-1.4\nWasl attachment evidence\n%%EOF".encodeToByteArray()
        val before = attachmentStore.importAttachment(
            AddAttachmentCommand(
                id = "attachment-backup-id",
                debtId = debtId,
                displayName = "proof.pdf",
                mimeType = "application/pdf",
                createdAt = Instant.parse("2026-08-27T09:00:00Z"),
                note = "مرفق تجريبي للنسخة",
            ),
            ByteArrayInputStream(bytes),
        )
        assertEquals(AttachmentIntegrity.OK, before.integrity)

        val backup = createBackup("attachment-backup-secret")
        assertEquals(11, backup.schemaVersion)
        assertEquals(1, backup.documentCount)

        val liveFile = File(testFilesDir, before.relativePath)
        assertContentEquals(bytes, liveFile.readBytes())
        database.openHelper.writableDatabase.execSQL("DELETE FROM attachments")
        check(liveFile.delete())
        assertEquals(null, attachmentStore.findById(before.id))

        val restored = restoreBackup(backup.bytes, "attachment-backup-secret")
        assertEquals(11, restored.schemaVersion)
        assertEquals(1, restored.documentCount)

        val after = assertNotNull(attachmentStore.findById(before.id))
        assertEquals(AttachmentIntegrity.OK, after.integrity)
        assertEquals(before.copy(integrity = AttachmentIntegrity.OK), after)
        assertContentEquals(bytes, File(testFilesDir, after.relativePath).readBytes())
    }

    @Test
    fun backupRefusesTamperedAttachmentBeforeProducingArchive() = runTest {
        val debtId = createDebt("attachment-corrupt", 25_000L)
        val attachment = attachmentStore.importAttachment(
            AddAttachmentCommand(
                id = "attachment-corrupt-id",
                debtId = debtId,
                displayName = "evidence.txt",
                mimeType = "text/plain",
                createdAt = Instant.parse("2026-08-27T09:00:00Z"),
            ),
            ByteArrayInputStream("original evidence".encodeToByteArray()),
        )
        File(testFilesDir, attachment.relativePath).writeText("tampered evidence")
        assertEquals(AttachmentIntegrity.HASH_MISMATCH, attachmentStore.findById(attachment.id)?.integrity)

        assertFailsWith<IllegalArgumentException> {
            backupService.create("attachment-backup-secret".toCharArray())
        }
        assertNotNull(database.attachmentDao().findById(attachment.id))
    }

    @Test
    fun restoreRejectsAttachmentPathTraversalBeforeMutatingLiveData() = runTest {
        val debtId = createDebt("attachment-path", 40_000L)
        val attachment = attachmentStore.importAttachment(
            AddAttachmentCommand(
                id = "attachment-path-id",
                debtId = debtId,
                displayName = "safe.txt",
                mimeType = "text/plain",
                createdAt = Instant.parse("2026-08-27T09:00:00Z"),
            ),
            ByteArrayInputStream("safe evidence".encodeToByteArray()),
        )
        val password = "attachment-path-secret".toCharArray()
        val validBackup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }

        val openPassword = "attachment-path-secret".toCharArray()
        val opened = try {
            BackupEnvelope.open(validBackup.bytes, openPassword)
        } finally {
            openPassword.fill('\u0000')
        }
        val maliciousPayload = opened.payload.copy(
            attachmentFiles = opened.payload.attachmentFiles.mapIndexed { index, file ->
                if (index == 0) file.copy(relativePath = "attachments/../outside.blob") else file
            },
        )
        val sealPassword = "attachment-path-secret".toCharArray()
        val maliciousBackup = try {
            BackupEnvelope.seal(maliciousPayload, opened.createdAt, sealPassword)
        } finally {
            sealPassword.fill('\u0000')
        }

        val restorePassword = "attachment-path-secret".toCharArray()
        try {
            assertFailsWith<IllegalArgumentException> {
                backupService.restore(maliciousBackup, restorePassword)
            }
        } finally {
            restorePassword.fill('\u0000')
        }

        val stillLive = assertNotNull(attachmentStore.findById(attachment.id))
        assertEquals(AttachmentIntegrity.OK, stillLive.integrity)
        assertContentEquals(
            "safe evidence".encodeToByteArray(),
            File(testFilesDir, stillLive.relativePath).readBytes(),
        )
    }

    private suspend fun createDebt(prefix: String, amount: Long): DebtId {
        val debtId = DebtId("$prefix-debt")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("$prefix-person"),
                debtId = debtId,
                personName = "اختبار $prefix",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(amount, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-27T08:00:00Z"),
                createdAt = Instant.parse("2026-08-27T08:00:00Z"),
            ),
        )
        return debtId
    }

    private suspend fun createBackup(passwordText: String): BackupCreated {
        val password = passwordText.toCharArray()
        return try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun restoreBackup(bytes: ByteArray, passwordText: String): BackupRestored {
        val password = passwordText.toCharArray()
        return try {
            backupService.restore(bytes, password)
        } finally {
            password.fill('\u0000')
        }
    }
}
