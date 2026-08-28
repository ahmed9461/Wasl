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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseBackupInvariantInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var backupService: BackupService
    private lateinit var testFilesDir: File

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "group-backup-invariant-${System.nanoTime()}").apply {
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
            clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
        )
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun restoreRejectsBlankGroupCommandIdBeforeMutatingLiveData() = runTest {
        seedPerson("group-invariant-person-a", "أحمد", "group-invariant-seed-a")
        seedPerson("group-invariant-person-b", "سارة", "group-invariant-seed-b")

        val groupId = GroupExpenseId("group-invariant")
        val before = repository.createGroupExpense(
            CreateGroupExpenseCommand(
                commandId = "group-invariant-command",
                expense = GroupExpense(
                    id = groupId,
                    direction = DebtDirection.RECEIVABLE,
                    totalAmount = Money(30_000L, CurrencyCode.YER),
                    occurredAt = Instant.parse("2026-08-28T01:30:00Z"),
                    description = "عملية جماعية سليمة",
                    notes = "يجب أن تبقى دون تغيير عند رفض النسخة",
                    shares = listOf(
                        GroupExpenseShare(
                            id = GroupExpenseShareId("group-invariant-share-a"),
                            debtId = DebtId("group-invariant-debt-a"),
                            personId = PersonId("group-invariant-person-a"),
                            amount = Money(12_000L, CurrencyCode.YER),
                        ),
                        GroupExpenseShare(
                            id = GroupExpenseShareId("group-invariant-share-b"),
                            debtId = DebtId("group-invariant-debt-b"),
                            personId = PersonId("group-invariant-person-b"),
                            amount = Money(18_000L, CurrencyCode.YER),
                        ),
                    ),
                ),
                createdAt = Instant.parse("2026-08-28T01:31:00Z"),
            ),
        )

        val validPassword = "group-invariant-secret".toCharArray()
        val validBackup = try {
            backupService.create(validPassword)
        } finally {
            validPassword.fill('\u0000')
        }

        val openPassword = "group-invariant-secret".toCharArray()
        val opened = try {
            BackupEnvelope.open(validBackup.bytes, openPassword)
        } finally {
            openPassword.fill('\u0000')
        }
        val malformedTables = opened.payload.tables.map { table ->
            if (table.name != "group_expenses") {
                table
            } else {
                val commandIdIndex = table.columns.indexOf("command_id")
                check(commandIdIndex >= 0)
                table.copy(
                    rows = table.rows.map { row ->
                        row.toMutableList().also { cells ->
                            cells[commandIdIndex] = BackupCell(BackupCellType.TEXT, "")
                        }
                    },
                )
            }
        }
        val malformedPayload = opened.payload.copy(tables = malformedTables)
        val sealPassword = "group-invariant-secret".toCharArray()
        val malformedBackup = try {
            BackupEnvelope.seal(malformedPayload, opened.createdAt, sealPassword)
        } finally {
            sealPassword.fill('\u0000')
        }

        val restorePassword = "group-invariant-secret".toCharArray()
        try {
            assertFailsWith<IllegalArgumentException> {
                backupService.restore(malformedBackup, restorePassword)
            }
        } finally {
            restorePassword.fill('\u0000')
        }

        val after = assertNotNull(repository.getGroupExpense(groupId))
        assertEquals(before, after)
        before.expense.shares.forEach { share ->
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
