package com.wasl.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.local.RoomAccountDocumentStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AndroidAccountDocumentService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.app.document.sha256Hex
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDocumentBackupInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var documentStore: RoomAccountDocumentStore
    private lateinit var backupService: BackupService
    private lateinit var documentService: AndroidAccountDocumentService
    private lateinit var testFilesDir: File

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "account-document-backup-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testFilesDir
        }
        database = Room.inMemoryDatabaseBuilder(baseContext, WaslDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWaslRepository(database)
        documentStore = RoomAccountDocumentStore(database, repository)
        val clock = Clock.fixed(Instant.parse("2026-08-25T15:00:00Z"), ZoneOffset.UTC)
        documentService = AndroidAccountDocumentService(
            context = isolatedContext,
            store = documentStore,
            clock = clock,
        )
        backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = clock,
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun encryptedBackupRestoresReadyDebtReceiptRecordAndPdfIntegrity() = runTest {
        val debtId = DebtId("backup-document-debt")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("backup-document-person"),
                debtId = debtId,
                personName = "عميل مستند النسخة",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-24T09:00:00Z"),
                createdAt = Instant.parse("2026-08-24T09:00:00Z"),
                description = "اختبار حفظ مستند داخل النسخة الاحتياطية",
            ),
        )
        repository.recordPayment(
            RecordPaymentCommand(
                commandId = "backup-document-payment-command",
                entryId = LedgerEntryId("backup-document-payment"),
                debtId = debtId,
                amount = Money(20_000L, CurrencyCode.YER),
                paidAt = Instant.parse("2026-08-25T10:00:00Z"),
                recordedAt = Instant.parse("2026-08-25T10:00:00Z"),
                note = "دفعة قبل إصدار إيصال الدين",
            ),
        )

        val issued = documentService.issueDebtReceipt(
            PrepareDebtReceiptCommand(
                commandId = "backup-debt-receipt-command",
                documentId = "backup-debt-receipt-document",
                identityId = "backup-document-identity",
                debtId = debtId,
                issuerDisplayName = "مؤسسة وَصل",
                issuerActivityName = "تجارة عامة",
                issuerPhone = "+967 777 000 000",
                footerText = "مستند محفوظ داخل النسخة",
                issuedAt = Instant.parse("2026-08-25T14:00:00Z"),
                issueZoneId = ZoneId.of("Asia/Aden"),
            ),
        )
        assertEquals(DocumentStatus.READY, issued.status)
        assertNull(issued.ledgerEntryId)
        assertEquals(Money(80_000L, CurrencyCode.YER), (issued.snapshot as DebtReceiptSnapshot).balanceAtIssue)
        val originalFile = ReceiptFileAccess.resolve(testFilesDir, issued.pdfRelativePath)
        assertTrue(originalFile.isFile)
        assertEquals(issued.pdfSha256, originalFile.sha256Hex())

        val schemaVersion = database.openHelper.readableDatabase.version
        val password = "account-document-backup-123".toCharArray()
        val backup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
        assertEquals(schemaVersion, backup.schemaVersion)
        assertEquals(1, backup.documentCount)

        originalFile.writeText("corrupted after backup")
        val extraDebtId = DebtId("backup-document-extra-debt")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("backup-document-extra-person"),
                debtId = extraDebtId,
                personName = "بيانات مؤقتة بعد النسخة",
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(10_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-25T14:30:00Z"),
                createdAt = Instant.parse("2026-08-25T14:30:00Z"),
            ),
        )
        assertNotNull(repository.getAccount(extraDebtId))

        val restorePassword = "account-document-backup-123".toCharArray()
        try {
            backupService.restore(backup.bytes, restorePassword)
        } finally {
            restorePassword.fill('\u0000')
        }

        assertNull(repository.getAccount(extraDebtId))
        val restoredDocument = assertNotNull(
            documentStore.getAccountDocument("backup-debt-receipt-document"),
        )
        assertEquals(DocumentStatus.READY, restoredDocument.status)
        assertNull(restoredDocument.ledgerEntryId)
        val restoredSnapshot = restoredDocument.snapshot as DebtReceiptSnapshot
        assertEquals(Money(80_000L, CurrencyCode.YER), restoredSnapshot.balanceAtIssue)
        assertEquals("مؤسسة وَصل", restoredSnapshot.identity.displayName)

        val restoredFile = ReceiptFileAccess.resolve(testFilesDir, restoredDocument.pdfRelativePath)
        assertTrue(restoredFile.isFile)
        assertEquals(restoredDocument.pdfSha256, restoredFile.sha256Hex())
        assertTrue(restoredFile.length() > 3_000L)
    }
}
