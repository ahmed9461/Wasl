package com.wasl.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.backup.AndroidBackupService
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AndroidPaymentReceiptService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.app.document.sha256Hex
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.DebtState
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
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
class MvpAcceptanceInstrumentedTest {
    private val baseContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var filesDir: File
    private lateinit var isolatedContext: Context

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-mvp-acceptance-${UUID.randomUUID()}.db"
        filesDir = File(baseContext.cacheDir, "wasl-mvp-files-${UUID.randomUUID()}").apply {
            check(mkdirs())
        }
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = this@MvpAcceptanceInstrumentedTest.filesDir
        }
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        baseContext.deleteDatabase(databaseName)
        if (::filesDir.isInitialized) filesDir.deleteRecursively()
    }

    @Test
    fun requiredMvpJourneySurvivesRestartReceiptBackupAndRestoreOffline() = runTest {
        val debtId = DebtId("mvp-debt")
        val zoneId = ZoneId.of("Asia/Aden")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("mvp-person"),
                debtId = debtId,
                personName = "عميل اختبار القبول",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-25T10:00:00Z"),
                createdAt = Instant.parse("2026-08-25T10:00:00Z"),
                dueDate = LocalDate.parse("2026-08-31"),
                description = "سيناريو قبول MVP",
                dueReminder = DueReminderRequest(
                    id = "mvp-reminder",
                    triggerAt = Instant.parse("2026-08-31T06:00:00Z"),
                    zoneId = zoneId,
                ),
            ),
        )

        reopenDatabase()
        val afterCreateRestart = assertNotNull(repository.getAccount(debtId))
        assertEquals(100_000L, afterCreateRestart.ledger.header.originalAmount.minorUnits)
        assertEquals(100_000L, afterCreateRestart.ledger.balance.minorUnits)
        assertNotNull(afterCreateRestart.dueReminder)

        repository.recordPayment(
            payment(
                commandId = "mvp-pay-20-command",
                entryId = "mvp-pay-20",
                amount = 20_000L,
                at = "2026-08-25T11:00:00Z",
            ),
        )
        repository.recordPayment(
            payment(
                commandId = "mvp-pay-5-command",
                entryId = "mvp-pay-5",
                amount = 5_000L,
                at = "2026-08-25T12:00:00Z",
            ),
        )

        var account = assertNotNull(repository.getAccount(debtId))
        assertEquals(100_000L, account.ledger.header.originalAmount.minorUnits)
        assertEquals(75_000L, account.ledger.balance.minorUnits)
        assertEquals(2, account.ledger.entries.size)

        reopenDatabase()
        account = assertNotNull(repository.getAccount(debtId))
        assertEquals(75_000L, account.ledger.balance.minorUnits)
        assertEquals(2, account.ledger.entries.size)

        repository.recordPayment(
            payment(
                commandId = "mvp-final-original-command",
                entryId = "mvp-final-original",
                amount = 75_000L,
                at = "2026-08-25T13:00:00Z",
            ),
        )
        account = assertNotNull(repository.getAccount(debtId))
        assertEquals(DebtState.SETTLED, account.ledger.state)
        assertEquals(0L, account.ledger.balance.minorUnits)

        repository.reversePayment(
            ReversePaymentCommand(
                commandId = "mvp-reverse-final-command",
                entryId = LedgerEntryId("mvp-reversal-final"),
                debtId = debtId,
                paymentId = LedgerEntryId("mvp-final-original"),
                recordedAt = Instant.parse("2026-08-25T14:00:00Z"),
                reason = "تصحيح دفعة نهائية للاختبار",
            ),
        )
        account = assertNotNull(repository.getAccount(debtId))
        assertEquals(75_000L, account.ledger.balance.minorUnits)
        assertTrue(account.ledger.reversedPaymentIds.contains(LedgerEntryId("mvp-final-original")))

        repository.recordPayment(
            payment(
                commandId = "mvp-final-replacement-command",
                entryId = "mvp-final-replacement",
                amount = 75_000L,
                at = "2026-08-25T15:00:00Z",
            ),
        )
        account = assertNotNull(repository.getAccount(debtId))
        assertEquals(DebtState.SETTLED, account.ledger.state)
        assertEquals(0L, account.ledger.balance.minorUnits)
        assertEquals(5, account.ledger.entries.size)

        val receiptService = AndroidPaymentReceiptService(
            context = isolatedContext,
            store = repository,
            clock = Clock.fixed(Instant.parse("2026-08-25T16:01:00Z"), ZoneOffset.UTC),
        )
        val receipt = receiptService.issue(
            PreparePaymentReceiptCommand(
                commandId = "mvp-receipt-command",
                documentId = "mvp-receipt-document",
                identityId = "mvp-identity",
                debtId = debtId,
                paymentId = LedgerEntryId("mvp-final-replacement"),
                issuerDisplayName = "وَصل للاختبار",
                issuerActivityName = "اختبار قبول",
                issuerPhone = "+967 000 000 000",
                footerText = "نسخة اختبارية",
                issuedAt = Instant.parse("2026-08-25T16:00:00Z"),
                issueZoneId = zoneId,
            ),
        )
        assertEquals(DocumentStatus.READY, receipt.status)
        val receiptFile = ReceiptFileAccess.resolve(filesDir, receipt.pdfRelativePath)
        assertTrue(receiptFile.isFile)
        val originalReceiptHash = assertNotNull(receipt.pdfSha256)
        assertEquals(originalReceiptHash, receiptFile.sha256Hex())

        val backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-25T17:00:00Z"), ZoneOffset.UTC),
        )
        val password = "mvp-portable-secret".toCharArray()
        val backup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
        assertEquals(7, backup.schemaVersion)
        assertEquals(1, backup.documentCount)

        val extraDebtId = DebtId("mvp-extra-debt")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("mvp-extra-person"),
                debtId = extraDebtId,
                personName = "بيانات بعد النسخة",
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(1_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-25T18:00:00Z"),
                createdAt = Instant.parse("2026-08-25T18:00:00Z"),
            ),
        )
        receiptFile.writeText("corrupted after backup")
        assertNotNull(repository.getAccount(extraDebtId))
        assertTrue(receiptFile.sha256Hex() != originalReceiptHash)

        val restorePassword = "mvp-portable-secret".toCharArray()
        try {
            backupService.restore(backup.bytes, restorePassword)
        } finally {
            restorePassword.fill('\u0000')
        }

        val restored = assertNotNull(repository.getAccount(debtId))
        assertEquals(100_000L, restored.ledger.header.originalAmount.minorUnits)
        assertEquals(0L, restored.ledger.balance.minorUnits)
        assertEquals(DebtState.SETTLED, restored.ledger.state)
        assertEquals(5, restored.ledger.entries.size)
        assertNotNull(restored.dueReminder)
        assertNull(repository.getAccount(extraDebtId))

        val restoredReceipt = restored.issuedDocuments.single { it.id == receipt.id }
        assertEquals(DocumentStatus.READY, restoredReceipt.status)
        assertEquals(originalReceiptHash, restoredReceipt.pdfSha256)
        assertTrue(receiptFile.isFile)
        assertEquals(originalReceiptHash, receiptFile.sha256Hex())
    }

    private fun payment(
        commandId: String,
        entryId: String,
        amount: Long,
        at: String,
    ) = RecordPaymentCommand(
        commandId = commandId,
        entryId = LedgerEntryId(entryId),
        debtId = DebtId("mvp-debt"),
        amount = Money(amount, CurrencyCode.YER),
        paidAt = Instant.parse(at),
        recordedAt = Instant.parse(at),
        note = "دفعة اختبار $entryId",
    )

    private fun openDatabase() {
        database = Room.databaseBuilder(baseContext, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }
}
