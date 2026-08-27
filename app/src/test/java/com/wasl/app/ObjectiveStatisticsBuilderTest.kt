package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.PersonRecord
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectiveStatisticsBuilderTest {
    private val zone = ZoneId.of("Asia/Aden")

    @Test
    fun computesSettlementDelayAndPromiseCountsWithoutMoneyAggregation() {
        val accounts = listOf(
            settledAccount(
                id = "settled-on-time",
                currency = CurrencyCode.YER,
                openedAt = "2026-08-01T09:00:00Z",
                closedAt = "2026-08-11T09:00:00Z",
                dueDate = LocalDate.parse("2026-08-15"),
            ),
            settledAccount(
                id = "settled-late",
                currency = CurrencyCode.USD,
                openedAt = "2026-08-01T09:00:00Z",
                closedAt = "2026-08-21T09:00:00Z",
                dueDate = LocalDate.parse("2026-08-15"),
            ),
            openAccount("open-sar", CurrencyCode.SAR),
        )
        val promises = listOf(
            promise("kept", PaymentPromiseStatus.KEPT),
            promise("missed", PaymentPromiseStatus.MISSED),
            promise("pending", PaymentPromiseStatus.PENDING),
            promise("cancelled", PaymentPromiseStatus.CANCELLED),
        )

        val result = ObjectiveStatisticsBuilder.build(accounts, promises, zone)

        assertEquals(3, result.totalAccounts)
        assertEquals(2, result.settledAccounts)
        assertEquals(1, result.openAccounts)
        assertEquals(15.0, result.averageSettlementDays)
        assertEquals(2, result.settledAccountsWithDueDate)
        assertEquals(1, result.lateSettledAccounts)
        assertEquals(6.0, result.averageLateDays)
        assertEquals(1, result.keptPromises)
        assertEquals(1, result.missedPromises)
        assertEquals(1, result.pendingPromises)
        assertEquals(1, result.cancelledPromises)
    }

    @Test
    fun emptyHistoryReturnsNullAverages() {
        val result = ObjectiveStatisticsBuilder.build(emptyList(), emptyList(), zone)

        assertEquals(0, result.totalAccounts)
        assertEquals(0, result.settledAccounts)
        assertEquals(0, result.openAccounts)
        assertNull(result.averageSettlementDays)
        assertNull(result.averageLateDays)
    }

    private fun settledAccount(
        id: String,
        currency: CurrencyCode,
        openedAt: String,
        closedAt: String,
        dueDate: LocalDate,
    ): AccountOverview {
        val opened = Instant.parse(openedAt)
        val closed = Instant.parse(closedAt)
        val debtId = DebtId(id)
        val amount = Money(10_000L, currency)
        val ledger = DebtLedger(
            DebtHeader(
                id = debtId,
                personId = PersonId("person-$id"),
                direction = DebtDirection.RECEIVABLE,
                originalAmount = amount,
                openedAt = opened,
                dueDate = dueDate,
            ),
        ).recordPayment(
            id = LedgerEntryId("payment-$id"),
            amount = amount,
            paidAt = closed,
        )
        return AccountOverview(
            person = person(id, opened),
            ledger = ledger,
            lifecycleState = DebtLifecycleState.ACTIVE,
            closedAt = closed,
        )
    }

    private fun openAccount(id: String, currency: CurrencyCode): AccountOverview {
        val opened = Instant.parse("2026-08-01T09:00:00Z")
        return AccountOverview(
            person = person(id, opened),
            ledger = DebtLedger(
                DebtHeader(
                    id = DebtId(id),
                    personId = PersonId("person-$id"),
                    direction = DebtDirection.PAYABLE,
                    originalAmount = Money(25_000L, currency),
                    openedAt = opened,
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }

    private fun person(id: String, createdAt: Instant) = PersonRecord(
        id = PersonId("person-$id"),
        displayName = "Person $id",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun promise(id: String, status: PaymentPromiseStatus): PaymentPromiseRecord {
        val created = Instant.parse("2026-08-01T10:00:00Z")
        val resolved = if (status == PaymentPromiseStatus.PENDING) null else Instant.parse("2026-08-02T10:00:00Z")
        return PaymentPromiseRecord(
            id = id,
            debtId = DebtId("promise-debt-$id"),
            promisedDate = LocalDate.parse("2026-08-02"),
            status = status,
            createdAt = created,
            resolvedAt = resolved,
            updatedAt = resolved ?: created,
        )
    }
}
