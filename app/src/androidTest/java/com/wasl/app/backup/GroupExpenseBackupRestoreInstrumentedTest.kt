package com.wasl.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreateGroupExpenseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.GroupExpense
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.GroupExpenseShare
import com.wasl.domain.GroupExpenseShareId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseBackupRestoreInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var backupService: BackupService
    private lateinit var testFilesDir: File

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "group-backup-test-${System.nanoTime()}").apply {
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
        backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC),
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun encryptedBackupRestoresOriginalGroupAndItsOrdinaryDebts() = runTest {
        seedPerson("group-backup-person-a", "أحمد", "group-backup-seed-a")
        seedPerson("group-backup-person-b", "سارة", "group-backup-seed-b")

        val expense = GroupExpense(
            id = GroupExpenseId("group-backup"),
            direction = DebtDirection.RECEIVABLE,
            totalAmount = Money(30_000L, CurrencyCode.YER),
            occurredAt = Instant.parse("2026-08-28T00:30:00Z"),
            description = "مطعم مشترك",
            notes = "يجب أن يعود أصل العملية مع الحصص",
            shares = listOf(
                GroupExpenseShare(
                    id = GroupExpenseShareId("group-backup-share-a"),
                    debtId = DebtId("group-backup-debt-a"),
                    personId = PersonId("group-backup-person-a"),
                    amount = Money(12_000L, CurrencyCode.YER),
                ),
                GroupExpenseShare(
                    id = GroupExpenseShareId("group-backup-share-b"),
                    debtId = DebtId("group-backup-debt-b"),
                    personId = PersonId("group-backup-person-b"),
                    amount = Money(18_000L, CurrencyCode.YER),
                ),
            ),
        )
        val before = repository.createGroupExpense(
            CreateGroupExpenseCommand(
                commandId = "group-backup-command",
                expense = expense,
                createdAt = Instant.parse("2026-08-28T00:31:00Z"),
            ),
        )

        val password = "group-backup-secret".toCharArray()
        val backup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
        assertEquals(11, backup.schemaVersion)

        database.openHelper.writableDatabase.execSQL("DELETE FROM group_expense_shares")
        database.openHelper.writableDatabase.execSQL("DELETE FROM group_expenses")
        check(repository.getGroupExpense(GroupExpenseId("group-backup")) == null)

        val restorePassword = "group-backup-secret".toCharArray()
        val restored = try {
            backupService.restore(backup.bytes, restorePassword)
        } finally {
            restorePassword.fill('\u0000')
        }
        assertEquals(11, restored.schemaVersion)

        val after = assertNotNull(repository.getGroupExpense(GroupExpenseId("group-backup")))
        assertEquals(before, after)
        after.expense.shares.forEach { share ->
            val account = assertNotNull(repository.getAccount(share.debtId))
            assertEquals(share.personId, account.person.id)
            assertEquals(share.amount, account.ledger.header.originalAmount)
            assertEquals(share.amount, account.ledger.balance)
        }
    }

    private suspend fun seedPerson(personId: String, name: String, debtId: String) {
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId(personId),
                debtId = DebtId(debtId),
                personName = name,
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(1_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-01T00:00:00Z"),
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                description = "حساب تأسيسي",
            ),
        )
    }
}
