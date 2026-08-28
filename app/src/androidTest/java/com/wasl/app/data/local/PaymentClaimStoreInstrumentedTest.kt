package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.app.data.ResolvePaymentClaimCommand
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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentClaimStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomPaymentClaimStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-claim-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomPaymentClaimStore(database)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-payable-claim"),
                    debtId = DebtId("debt-payable-claim"),
                    personName = "صاحب الحق",
                    direction = DebtDirection.PAYABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-01T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-01T08:00:00Z"),
                    dueDate = LocalDate.parse("2026-08-30"),
                    description = "دين عليّ لاختبار طالبني",
                ),
            )
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-receivable-claim"),
                    debtId = DebtId("debt-receivable-claim"),
                    personName = "مدين لي",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(80_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-01T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-01T08:00:00Z"),
                    dueDate = LocalDate.parse("2026-08-30"),
                    description = "دين لي لا ينبغي أن يقبل طالبني",
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
    fun claimIsHistoricalIdempotentAndDoesNotChangeLedgerOrDueDate() = runBlocking {
        val before = requireNotNull(repository.getAccount(DebtId("debt-payable-claim")))
        val command = CreatePaymentClaimCommand(
            commandId = "claim-create-1",
            claimId = "claim-1",
            debtId = DebtId("debt-payable-claim"),
            claimedAt = Instant.parse("2026-08-27T06:00:00Z"),
            followUpKind = PaymentClaimFollowUpKind.TOMORROW,
            followUpDate = LocalDate.parse("2026-08-28"),
            note = "طالبني بالسداد",
            createdAt = Instant.parse("2026-08-27T06:00:00Z"),
        )

        val created = store.createClaim(command)
        assertEquals(created, store.createClaim(command))
        assertEquals(PaymentClaimStatus.ACTIVE, created.status)

        assertFailsWith<CommandConflictException> {
            store.createClaim(command.copy(followUpDate = LocalDate.parse("2026-08-29")))
        }

        val visible = store.observeClaims(DebtId("debt-payable-claim")).first()
        assertEquals(listOf("claim-1"), visible.map { it.id })
        val due = store.observeOpenClaims(LocalDate.parse("2026-08-28")).first()
        assertEquals(listOf("claim-1"), due.map { it.id })

        val after = requireNotNull(repository.getAccount(DebtId("debt-payable-claim")))
        assertEquals(before.ledger.balance, after.ledger.balance)
        assertEquals(before.ledger.header.dueDate, after.ledger.header.dueDate)
        assertEquals(before.ledger.entries, after.ledger.entries)
    }

    @Test
    fun receivableDebtRejectsPaymentClaim() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            store.createClaim(
                CreatePaymentClaimCommand(
                    commandId = "claim-receivable",
                    claimId = "claim-receivable",
                    debtId = DebtId("debt-receivable-claim"),
                    claimedAt = Instant.parse("2026-08-27T06:00:00Z"),
                    followUpKind = PaymentClaimFollowUpKind.TODAY,
                    followUpDate = LocalDate.parse("2026-08-27"),
                    createdAt = Instant.parse("2026-08-27T06:00:00Z"),
                ),
            )
        }
        assertTrue(store.observeClaims(DebtId("debt-receivable-claim")).first().isEmpty())
    }

    @Test
    fun resolutionIsIdempotentAndPreservesClaimHistory() = runBlocking {
        val claim = store.createClaim(
            CreatePaymentClaimCommand(
                commandId = "claim-create-resolve",
                claimId = "claim-resolve",
                debtId = DebtId("debt-payable-claim"),
                claimedAt = Instant.parse("2026-08-27T06:00:00Z"),
                followUpKind = PaymentClaimFollowUpKind.TODAY,
                followUpDate = LocalDate.parse("2026-08-27"),
                createdAt = Instant.parse("2026-08-27T06:00:00Z"),
            ),
        )
        val resolution = ResolvePaymentClaimCommand(
            commandId = "claim-resolution-1",
            claimId = claim.id,
            debtId = claim.debtId,
            status = PaymentClaimStatus.RESOLVED,
            resolvedAt = Instant.parse("2026-08-27T07:00:00Z"),
            note = "تمت معالجة المطالبة",
        )

        val resolved = store.resolveClaim(resolution)
        assertEquals(resolved, store.resolveClaim(resolution))
        assertEquals(PaymentClaimStatus.RESOLVED, resolved.status)
        assertTrue(store.observeOpenClaims(LocalDate.parse("2026-08-27")).first().isEmpty())
        assertEquals(1, store.observeClaims(DebtId("debt-payable-claim")).first().size)
    }
}
