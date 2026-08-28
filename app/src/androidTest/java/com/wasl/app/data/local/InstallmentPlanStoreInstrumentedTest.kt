package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateInstallmentPlanCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.InstallmentPlanItemInput
import com.wasl.app.data.InstallmentPlanStatus
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.ReviseInstallmentPlanCommand
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.InstallmentSchedule
import com.wasl.domain.LedgerEntryId
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
class InstallmentPlanStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomInstallmentPlanStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-installments-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomInstallmentPlanStore(database, repository)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-installments"),
                    debtId = DebtId("debt-installments"),
                    personName = "سالم",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(120_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-01T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-01T08:00:00Z"),
                    description = "دين بخطة أقساط",
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
    fun planProgressAlwaysComesFromTheDebtLedger() = runBlocking {
        repository.recordPayment(
            payment(
                commandId = "payment-before-plan",
                entryId = "payment-entry-before-plan",
                amountMinor = 30_000L,
                at = "2026-08-10T09:00:00Z",
            ),
        )

        val command = CreateInstallmentPlanCommand(
            commandId = "installment-create-1",
            planId = "plan-1",
            debtId = DebtId("debt-installments"),
            installments = equalItems("plan-1", 6, LocalDate.parse("2026-09-01")),
            createdAt = Instant.parse("2026-08-15T09:00:00Z"),
        )
        val created = store.createInstallmentPlan(command)
        assertEquals(created, store.createInstallmentPlan(command))
        assertEquals(20_000L, created.installments[0].paidAmount.minorUnits)
        assertEquals(10_000L, created.installments[1].paidAmount.minorUnits)
        assertEquals(90_000L, created.installments.sumOf { it.remainingAmount.minorUnits })

        assertFailsWith<CommandConflictException> {
            store.createInstallmentPlan(
                command.copy(
                    installments = equalItems("plan-1-conflict", 4, LocalDate.parse("2026-09-01")),
                ),
            )
        }

        repository.recordPayment(
            payment(
                commandId = "payment-after-plan",
                entryId = "payment-entry-after-plan",
                amountMinor = 15_000L,
                at = "2026-08-20T09:00:00Z",
            ),
        )
        var active = store.observeInstallmentPlans(DebtId("debt-installments"))
            .first { plans -> plans.firstOrNull()?.installments?.sumOf { it.paidAmount.minorUnits } == 45_000L }
            .first()
        assertEquals(75_000L, active.installments.sumOf { it.remainingAmount.minorUnits })

        repository.reversePayment(
            ReversePaymentCommand(
                commandId = "reverse-payment-after-plan",
                entryId = LedgerEntryId("reverse-entry-after-plan"),
                debtId = DebtId("debt-installments"),
                paymentId = LedgerEntryId("payment-entry-after-plan"),
                recordedAt = Instant.parse("2026-08-21T09:00:00Z"),
                reason = "تصحيح اختبار",
            ),
        )
        active = store.observeInstallmentPlans(DebtId("debt-installments"))
            .first { plans -> plans.firstOrNull()?.installments?.sumOf { it.paidAmount.minorUnits } == 30_000L }
            .first()
        assertEquals(90_000L, active.installments.sumOf { it.remainingAmount.minorUnits })
    }

    @Test
    fun revisionFreezesOldScheduleWhileActiveScheduleTracksLaterPaymentsAndReversals() = runBlocking {
        val initial = store.createInstallmentPlan(
            CreateInstallmentPlanCommand(
                commandId = "installment-create-initial",
                planId = "plan-initial",
                debtId = DebtId("debt-installments"),
                installments = equalItems("plan-initial", 6, LocalDate.parse("2026-09-01")),
                createdAt = Instant.parse("2026-08-05T09:00:00Z"),
            ),
        )
        assertEquals(InstallmentPlanStatus.ACTIVE, initial.status)

        repository.recordPayment(
            payment(
                commandId = "payment-before-revision",
                entryId = "payment-entry-before-revision",
                amountMinor = 30_000L,
                at = "2026-08-10T09:00:00Z",
            ),
        )

        val revised = store.reviseInstallmentPlan(
            ReviseInstallmentPlanCommand(
                commandId = "installment-revise-1",
                planId = "plan-revision-2",
                debtId = DebtId("debt-installments"),
                supersedesPlanId = "plan-initial",
                installments = equalItems("plan-revision-2", 4, LocalDate.parse("2026-09-01")),
                createdAt = Instant.parse("2026-08-15T09:00:00Z"),
                reason = "إعادة توزيع المواعيد",
            ),
        )
        assertEquals(2, revised.revisionNumber)
        assertEquals(30_000L, revised.installments.sumOf { it.paidAmount.minorUnits })

        repository.recordPayment(
            payment(
                commandId = "payment-after-revision",
                entryId = "payment-entry-after-revision",
                amountMinor = 20_000L,
                at = "2026-08-20T09:00:00Z",
            ),
        )

        var plans = store.observeInstallmentPlans(DebtId("debt-installments"))
            .first { records ->
                records.firstOrNull { it.status == InstallmentPlanStatus.ACTIVE }
                    ?.installments
                    ?.sumOf { it.paidAmount.minorUnits } == 50_000L
            }
        var historical = requireNotNull(plans.firstOrNull { it.status == InstallmentPlanStatus.SUPERSEDED })
        var current = requireNotNull(plans.firstOrNull { it.status == InstallmentPlanStatus.ACTIVE })
        assertEquals(1L, historical.supersededAfterLedgerSequence)
        assertEquals(30_000L, historical.installments.sumOf { it.paidAmount.minorUnits })
        assertEquals(50_000L, current.installments.sumOf { it.paidAmount.minorUnits })

        repository.reversePayment(
            ReversePaymentCommand(
                commandId = "reverse-payment-before-revision",
                entryId = LedgerEntryId("reverse-entry-before-revision"),
                debtId = DebtId("debt-installments"),
                paymentId = LedgerEntryId("payment-entry-before-revision"),
                recordedAt = Instant.parse("2026-08-25T09:00:00Z"),
                reason = "عكس دفعة تاريخية بعد تعديل الخطة",
            ),
        )

        plans = store.observeInstallmentPlans(DebtId("debt-installments"))
            .first { records ->
                records.firstOrNull { it.status == InstallmentPlanStatus.ACTIVE }
                    ?.installments
                    ?.sumOf { it.paidAmount.minorUnits } == 20_000L
            }
        historical = requireNotNull(plans.firstOrNull { it.status == InstallmentPlanStatus.SUPERSEDED })
        current = requireNotNull(plans.firstOrNull { it.status == InstallmentPlanStatus.ACTIVE })
        assertEquals(30_000L, historical.installments.sumOf { it.paidAmount.minorUnits })
        assertEquals(20_000L, current.installments.sumOf { it.paidAmount.minorUnits })

        val actionable = store.observeActionableInstallments(LocalDate.parse("2026-09-01"))
            .first { it.isNotEmpty() }
        assertEquals("plan-revision-2-1", actionable.first().id)
        assertEquals(10_000L, actionable.first().remainingAmount.minorUnits)
        assertTrue(actionable.first().isDueToday(LocalDate.parse("2026-09-01")))
    }

    private fun equalItems(
        prefix: String,
        count: Int,
        firstDueDate: LocalDate,
    ): List<InstallmentPlanItemInput> =
        InstallmentSchedule.equalMonthly(
            total = Money(120_000L, CurrencyCode.YER),
            count = count,
            firstDueDate = firstDueDate,
        ).map { item ->
            InstallmentPlanItemInput(
                id = "$prefix-${item.sequenceNumber}",
                sequenceNumber = item.sequenceNumber,
                dueDate = item.dueDate,
                amount = item.amount,
            )
        }

    private fun payment(
        commandId: String,
        entryId: String,
        amountMinor: Long,
        at: String,
    ): RecordPaymentCommand {
        val instant = Instant.parse(at)
        return RecordPaymentCommand(
            commandId = commandId,
            entryId = LedgerEntryId(entryId),
            debtId = DebtId("debt-installments"),
            amount = Money(amountMinor, CurrencyCode.YER),
            paidAt = instant,
            recordedAt = instant,
            note = "اختبار الأقساط",
        )
    }
}
