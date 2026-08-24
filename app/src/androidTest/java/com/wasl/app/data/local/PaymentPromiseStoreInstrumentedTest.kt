package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.ResolvePaymentPromiseCommand
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentPromiseStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomPaymentPromiseStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-promise-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomPaymentPromiseStore(database)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-promise"),
                    debtId = DebtId("debt-promise"),
                    personName = "محمد",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-01T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-01T08:00:00Z"),
                    dueDate = LocalDate.parse("2026-08-10"),
                    description = "دين تجريبي للوعود",
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun promisesPreserveHistoryWithoutChangingDebtOrLedger() = runBlocking {
        val firstCommand = CreatePaymentPromiseCommand(
            commandId = "promise-create-1",
            promiseId = "promise-1",
            debtId = DebtId("debt-promise"),
            promisedDate = LocalDate.parse("2026-08-15"),
            note = "قال إنه سيسدد بعد الراتب",
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
        )
        val first = store.createPaymentPromise(firstCommand)
        val replay = store.createPaymentPromise(firstCommand)
        assertEquals(first, replay)
        assertEquals(PaymentPromiseStatus.PENDING, first.status)
        assertTrue(first.isOverdue(LocalDate.parse("2026-08-16")))

        assertFailsWith<CommandConflictException> {
            store.createPaymentPromise(
                firstCommand.copy(promisedDate = LocalDate.parse("2026-08-17")),
            )
        }

        val missed = store.resolvePaymentPromise(
            ResolvePaymentPromiseCommand(
                commandId = "promise-resolve-1",
                promiseId = first.id,
                debtId = first.debtId,
                status = PaymentPromiseStatus.MISSED,
                resolvedAt = Instant.parse("2026-08-16T10:00:00Z"),
                note = "لم يتم السداد في الموعد المتفق عليه",
            ),
        )
        assertEquals(PaymentPromiseStatus.MISSED, missed.status)
        assertFalse(missed.isOverdue(LocalDate.parse("2026-08-20")))

        val second = store.createPaymentPromise(
            CreatePaymentPromiseCommand(
                commandId = "promise-create-2",
                promiseId = "promise-2",
                debtId = DebtId("debt-promise"),
                promisedDate = LocalDate.parse("2026-08-25"),
                note = "وعد جديد بعد فوات الوعد السابق",
                createdAt = Instant.parse("2026-08-16T11:00:00Z"),
            ),
        )
        assertEquals(PaymentPromiseStatus.PENDING, second.status)

        val promises = store.observePaymentPromises(DebtId("debt-promise")).first()
        assertEquals(listOf("promise-1", "promise-2"), promises.map { it.id })
        assertEquals(
            listOf(PaymentPromiseStatus.MISSED, PaymentPromiseStatus.PENDING),
            promises.map { it.status },
        )

        val account = requireNotNull(repository.getAccount(DebtId("debt-promise")))
        assertEquals(LocalDate.parse("2026-08-10"), account.ledger.header.dueDate)
        assertEquals(Money(100_000L, CurrencyCode.YER), account.ledger.balance)
        assertTrue(account.ledger.entries.isEmpty())
    }

    @Test
    fun resolvingPromiseIsIdempotentAndCannotBeRewritten() = runBlocking {
        val created = store.createPaymentPromise(
            CreatePaymentPromiseCommand(
                commandId = "promise-create-kept",
                promiseId = "promise-kept",
                debtId = DebtId("debt-promise"),
                promisedDate = LocalDate.parse("2026-08-18"),
                createdAt = Instant.parse("2026-08-12T09:00:00Z"),
            ),
        )
        val resolution = ResolvePaymentPromiseCommand(
            commandId = "promise-resolve-kept",
            promiseId = created.id,
            debtId = created.debtId,
            status = PaymentPromiseStatus.KEPT,
            resolvedAt = Instant.parse("2026-08-18T12:00:00Z"),
            note = "تم الوفاء بالوعد",
        )
        val kept = store.resolvePaymentPromise(resolution)
        assertEquals(kept, store.resolvePaymentPromise(resolution))
        assertEquals(PaymentPromiseStatus.KEPT, kept.status)

        assertFailsWith<IllegalArgumentException> {
            store.resolvePaymentPromise(
                ResolvePaymentPromiseCommand(
                    commandId = "promise-resolve-second",
                    promiseId = created.id,
                    debtId = created.debtId,
                    status = PaymentPromiseStatus.CANCELLED,
                    resolvedAt = Instant.parse("2026-08-18T13:00:00Z"),
                ),
            )
        }
        Unit
    }
}
