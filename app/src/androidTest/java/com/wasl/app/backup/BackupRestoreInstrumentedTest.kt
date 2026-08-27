package com.wasl.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.UpsertGeneralReminderCommand
import com.wasl.app.data.local.RoomGeneralReminderStore
import com.wasl.app.data.local.RoomPaymentClaimStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var backupService: BackupService
    private lateinit var testFilesDir: File

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "backup-restore-test-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testFilesDir
        }
        database = Room.inMemoryDatabaseBuilder(
            baseContext,
            WaslDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomWaslRepository(database)
        backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-25T14:00:00Z"), ZoneOffset.UTC),
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun encryptedBackupRestoresLedgerGeneralReminderClaimAndWrongPasswordDoesNotMutateDatabase() = runTest {
        val primaryDebtId = DebtId("backup-debt-primary")
        val primaryPaymentId = LedgerEntryId("backup-payment-primary")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("backup-person-primary"),
                debtId = primaryDebtId,
                personName = "عميل النسخة",
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-20T10:00:00Z"),
                createdAt = Instant.parse("2026-08-20T10:00:00Z"),
                description = "حساب اختبار النسخ الاحتياطي",
            ),
        )
        repository.recordPayment(
            RecordPaymentCommand(
                commandId = "backup-payment-command-primary",
                entryId = primaryPaymentId,
                debtId = primaryDebtId,
                amount = Money(25_000L, CurrencyCode.YER),
                paidAt = Instant.parse("2026-08-21T10:00:00Z"),
                recordedAt = Instant.parse("2026-08-21T10:00:00Z"),
                note = "دفعة اختبار",
            ),
        )
        val reminderStore = RoomGeneralReminderStore(database)
        val reminderBeforeBackup = reminderStore.upsertReminder(
            UpsertGeneralReminderCommand(
                reminderId = "backup-general-reminder",
                debtId = primaryDebtId,
                triggerAt = Instant.parse("2026-08-30T06:00:00Z"),
                zoneId = ZoneId.of("Asia/Aden"),
                repeatRule = GeneralReminderRepeatRule(GeneralReminderFrequency.WEEKLY),
                updatedAt = Instant.parse("2026-08-25T13:00:00Z"),
            ),
        )
        assertEquals(ReminderStatus.SCHEDULED, reminderBeforeBackup.status)
        assertEquals(GeneralReminderFrequency.WEEKLY, reminderBeforeBackup.repeatRule?.frequency)

        val claimStore = RoomPaymentClaimStore(database)
        val claimBeforeBackup = claimStore.createClaim(
            CreatePaymentClaimCommand(
                commandId = "backup-claim-command",
                claimId = "backup-claim",
                debtId = primaryDebtId,
                claimedAt = Instant.parse("2026-08-25T13:15:00Z"),
                followUpKind = PaymentClaimFollowUpKind.TOMORROW,
                followUpDate = LocalDate.parse("2026-08-26"),
                note = "طالبني بالسداد قبل أخذ النسخة",
                createdAt = Instant.parse("2026-08-25T13:15:00Z"),
            ),
        )
        assertEquals(PaymentClaimStatus.ACTIVE, claimBeforeBackup.status)

        val beforeBackup = assertNotNull(repository.getAccount(primaryDebtId))
        assertEquals(75_000L, beforeBackup.ledger.balance.minorUnits)
        assertEquals(1, beforeBackup.ledger.entries.size)

        val password = "portable-secret-123".toCharArray()
        val backup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
        assertEquals(9, backup.schemaVersion)
        assertEquals(0, backup.documentCount)

        reminderStore.cancelReminder(
            reminderId = reminderBeforeBackup.id,
            updatedAt = Instant.parse("2026-08-25T13:30:00Z"),
        )
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM payment_claims WHERE id = 'backup-claim'",
        )
        assertEquals(0, claimStore.observeClaims(primaryDebtId).first().size)

        val extraDebtId = DebtId("backup-debt-extra")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("backup-person-extra"),
                debtId = extraDebtId,
                personName = "بيانات بعد النسخة",
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(50_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-22T10:00:00Z"),
                createdAt = Instant.parse("2026-08-22T10:00:00Z"),
            ),
        )
        assertNotNull(repository.getAccount(extraDebtId))
        assertEquals(
            ReminderStatus.CANCELLED,
            assertNotNull(reminderStore.getReminderForDebt(primaryDebtId)).status,
        )

        assertFailsWith<SecurityException> {
            backupService.restore(backup.bytes, "definitely-wrong".toCharArray())
        }
        assertNotNull(repository.getAccount(extraDebtId))
        assertEquals(
            75_000L,
            assertNotNull(repository.getAccount(primaryDebtId)).ledger.balance.minorUnits,
        )
        assertEquals(0, claimStore.observeClaims(primaryDebtId).first().size)
        assertEquals(
            ReminderStatus.CANCELLED,
            assertNotNull(reminderStore.getReminderForDebt(primaryDebtId)).status,
        )

        val restorePassword = "portable-secret-123".toCharArray()
        val restored = try {
            backupService.restore(backup.bytes, restorePassword)
        } finally {
            restorePassword.fill('\u0000')
        }
        assertEquals(9, restored.schemaVersion)

        val afterRestore = assertNotNull(repository.getAccount(primaryDebtId))
        assertEquals(beforeBackup.person.displayName, afterRestore.person.displayName)
        assertEquals(beforeBackup.ledger.header, afterRestore.ledger.header)
        assertEquals(beforeBackup.ledger.balance, afterRestore.ledger.balance)
        assertEquals(beforeBackup.ledger.entries, afterRestore.ledger.entries)
        assertNull(repository.getAccount(extraDebtId))

        val reminderAfterRestore = assertNotNull(reminderStore.getReminderForDebt(primaryDebtId))
        assertEquals(reminderBeforeBackup.id, reminderAfterRestore.id)
        assertEquals(reminderBeforeBackup.triggerAt, reminderAfterRestore.triggerAt)
        assertEquals(reminderBeforeBackup.zoneId, reminderAfterRestore.zoneId)
        assertEquals(GeneralReminderFrequency.WEEKLY, reminderAfterRestore.repeatRule?.frequency)
        assertEquals(ReminderStatus.SCHEDULED, reminderAfterRestore.status)

        val claimsAfterRestore = claimStore.observeClaims(primaryDebtId).first()
        assertEquals(1, claimsAfterRestore.size)
        assertEquals(claimBeforeBackup, claimsAfterRestore.single())
    }
}
