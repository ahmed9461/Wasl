package com.wasl.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.DebtState
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWaslRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private var database: WaslDatabase? = null
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-test-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun confirmedDebtAndPaymentsSurviveDatabaseReopen() = runTest {
        repository.createPersonWithDebt(baseCommand())
        reopenDatabase()

        val created = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(Money(100_000L, CurrencyCode.YER), created.ledger.balance)
        assertEquals("أحمد", created.person.displayName)

        repository.recordPayment(paymentCommand("command-1", "payment-1", 20_000L, 1))
        repository.recordPayment(paymentCommand("command-2", "payment-2", 5_000L, 2))
        reopenDatabase()

        val restored = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(Money(100_000L, CurrencyCode.YER), restored.ledger.header.originalAmount)
        assertEquals(Money(75_000L, CurrencyCode.YER), restored.ledger.balance)
        assertEquals(2, restored.ledger.entries.size)
        assertEquals(DebtState.PARTIALLY_PAID, restored.ledger.state)
    }

    @Test
    fun duplicateCompositeCreateUsesDebtIdAsItsIdempotencyKey() = runTest {
        val command = baseCommand()

        repository.createPersonWithDebt(command)
        repository.createPersonWithDebt(command)

        assertEquals(1, database!!.personDao().count())
        assertEquals(1, database!!.debtDao().count())
    }

    @Test
    fun duplicatePaymentCommandIsIdempotentAndPayloadConflictIsRejected() = runTest {
        repository.createPersonWithDebt(baseCommand())
        val command = paymentCommand("same-command", "payment-1", 20_000L, 1)

        repository.recordPayment(command)
        repository.recordPayment(command)

        val account = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(Money(80_000L, CurrencyCode.YER), account.ledger.balance)
        assertEquals(1, account.ledger.entries.size)

        assertFailsWith<CommandConflictException> {
            repository.recordPayment(
                paymentCommand("same-command", "payment-2", 10_000L, 2),
            )
        }
        assertEquals(1, database!!.ledgerDao().countForDebt("debt-1"))
    }

    @Test
    fun overpaymentRollsBackWithoutLedgerEntry() = runTest {
        repository.createPersonWithDebt(baseCommand())

        assertFailsWith<IllegalArgumentException> {
            repository.recordPayment(
                paymentCommand("overpay-command", "payment-1", 100_001L, 1),
            )
        }

        val account = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(Money(100_000L, CurrencyCode.YER), account.ledger.balance)
        assertEquals(0, database!!.ledgerDao().countForDebt("debt-1"))
    }

    @Test
    fun concurrentPaymentsCannotOverdraw() = runTest {
        repository.createPersonWithDebt(baseCommand())

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) {
                    runCatching {
                        repository.recordPayment(
                            paymentCommand("concurrent-1", "payment-1", 60_000L, 1),
                        )
                    }
                },
                async(Dispatchers.Default) {
                    runCatching {
                        repository.recordPayment(
                            paymentCommand("concurrent-2", "payment-2", 60_000L, 2),
                        )
                    }
                },
            ).awaitAll()
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        val account = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(Money(40_000L, CurrencyCode.YER), account.ledger.balance)
        assertEquals(1, account.ledger.entries.size)
    }

    @Test
    fun finalPaymentAndReversalUpdateClosureProjectionAcrossReopen() = runTest {
        repository.createPersonWithDebt(baseCommand())
        repository.recordPayment(paymentCommand("final-command", "payment-final", 100_000L, 1))

        val settled = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(DebtState.SETTLED, settled.ledger.state)
        assertNotNull(settled.closedAt)

        val reversal = ReversePaymentCommand(
            commandId = "reverse-command",
            entryId = LedgerEntryId("reversal-1"),
            debtId = DebtId("debt-1"),
            paymentId = LedgerEntryId("payment-final"),
            recordedAt = Instant.parse("2026-08-13T00:02:00Z"),
            reason = "سُجلت الدفعة بالخطأ",
        )
        repository.reversePayment(reversal)
        repository.reversePayment(reversal)
        reopenDatabase()

        val reopened = assertNotNull(repository.getAccount(DebtId("debt-1")))
        assertEquals(DebtState.OPEN, reopened.ledger.state)
        assertEquals(Money(100_000L, CurrencyCode.YER), reopened.ledger.balance)
        assertNull(reopened.closedAt)
        assertEquals(2, reopened.ledger.entries.size)
    }

    @Test
    fun compositeCreateConflictDoesNotLeaveHalfWrittenPerson() = runTest {
        repository.createPersonWithDebt(baseCommand())
        val conflicting = baseCommand().copy(
            personId = PersonId("person-2"),
            personName = "شخص آخر",
        )

        assertFailsWith<CommandConflictException> {
            repository.createPersonWithDebt(conflicting)
        }

        assertEquals(1, database!!.personDao().count())
        assertEquals(1, database!!.debtDao().count())
    }

    @Test
    fun foreignKeyRejectsOrphanDebt() = runTest {
        val now = Instant.parse("2026-08-13T00:00:00Z").toEpochMilli()
        assertFailsWith<SQLiteConstraintException> {
            database!!.debtDao().insert(
                DebtEntity(
                    id = "orphan-debt",
                    personId = "missing-person",
                    direction = DebtDirection.RECEIVABLE.name,
                    originalAmountMinor = 1L,
                    currencyCode = CurrencyCode.YER.value,
                    openedAt = now,
                    dueDateEpochDay = null,
                    description = null,
                    notes = null,
                    lifecycleState = "ACTIVE",
                    createdAt = now,
                    updatedAt = now,
                    closedAt = null,
                ),
            )
        }
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(
            context,
            WaslDatabase::class.java,
            databaseName,
        )
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database!!)
    }

    private fun reopenDatabase() {
        database!!.close()
        openDatabase()
    }

    private fun baseCommand() = CreatePersonWithDebtCommand(
        personId = PersonId("person-1"),
        debtId = DebtId("debt-1"),
        personName = "أحمد",
        direction = DebtDirection.RECEIVABLE,
        originalAmount = Money(100_000L, CurrencyCode.YER),
        openedAt = Instant.parse("2026-08-13T00:00:00Z"),
        createdAt = Instant.parse("2026-08-13T00:00:00Z"),
        description = "دين تجريبي",
    )

    private fun paymentCommand(
        commandId: String,
        paymentId: String,
        amount: Long,
        minute: Int,
    ) = RecordPaymentCommand(
        commandId = commandId,
        entryId = LedgerEntryId(paymentId),
        debtId = DebtId("debt-1"),
        amount = Money(amount, CurrencyCode.YER),
        paidAt = Instant.parse("2026-08-13T00:${minute.toString().padStart(2, '0')}:00Z"),
        recordedAt = Instant.parse("2026-08-13T00:${minute.toString().padStart(2, '0')}:00Z"),
    )
}
