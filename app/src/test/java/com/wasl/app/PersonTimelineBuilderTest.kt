package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStatus
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonTimelineBuilderTest {
    private val personId = PersonId("person-shared")
    private val person = PersonRecord(
        id = personId,
        displayName = "عميل متعدد الحسابات",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun balanceGroupsNeverNetDifferentCurrenciesOrDirections() {
        val accounts = listOf(
            account("yer-receivable-1", DebtDirection.RECEIVABLE, CurrencyCode.YER, 100_000, 20_000),
            account("yer-receivable-2", DebtDirection.RECEIVABLE, CurrencyCode.YER, 50_000, 10_000),
            account("yer-payable", DebtDirection.PAYABLE, CurrencyCode.YER, 40_000, 5_000),
            account("sar-receivable", DebtDirection.RECEIVABLE, CurrencyCode.SAR, 12_500, 2_500),
        )

        val groups = PersonTimelineBuilder.balanceGroups(accounts)

        assertEquals(3, groups.size)
        val yerReceivable = groups.single {
            it.currency == CurrencyCode.YER && it.direction == DebtDirection.RECEIVABLE
        }
        assertEquals(150_000L, yerReceivable.originalAmount.minorUnits)
        assertEquals(30_000L, yerReceivable.paidAmount.minorUnits)
        assertEquals(120_000L, yerReceivable.balance.minorUnits)
        assertEquals(2, yerReceivable.accountCount)

        val yerPayable = groups.single {
            it.currency == CurrencyCode.YER && it.direction == DebtDirection.PAYABLE
        }
        assertEquals(35_000L, yerPayable.balance.minorUnits)

        val sarReceivable = groups.single {
            it.currency == CurrencyCode.SAR && it.direction == DebtDirection.RECEIVABLE
        }
        assertEquals(10_000L, sarReceivable.balance.minorUnits)
    }

    @Test
    fun timelineIncludesFinancialAndFollowUpEventsInDeterministicDescendingOrder() {
        val debtId = DebtId("timeline-debt")
        var ledger = DebtLedger(
            DebtHeader(
                id = debtId,
                personId = personId,
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(100_000, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
            ),
        )
        ledger = ledger.recordPayment(
            id = LedgerEntryId("p1"),
            amount = Money(20_000, CurrencyCode.YER),
            paidAt = Instant.parse("2026-08-21T08:00:00Z"),
            recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
        )
        ledger = ledger.reversePayment(
            id = LedgerEntryId("r1"),
            paymentId = LedgerEntryId("p1"),
            recordedAt = Instant.parse("2026-08-22T08:00:00Z"),
            reason = "تصحيح",
        )
        val account = AccountOverview(
            person = person,
            ledger = ledger,
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
        val promise = PaymentPromiseRecord(
            id = "promise-1",
            debtId = debtId,
            promisedDate = LocalDate.parse("2026-08-25"),
            status = PaymentPromiseStatus.KEPT,
            createdAt = Instant.parse("2026-08-23T08:00:00Z"),
            resolvedAt = Instant.parse("2026-08-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-25T08:00:00Z"),
        )
        val claim = PaymentClaimRecord(
            id = "claim-1",
            debtId = debtId,
            claimedAt = Instant.parse("2026-08-24T08:00:00Z"),
            followUpKind = PaymentClaimFollowUpKind.TODAY,
            followUpDate = LocalDate.parse("2026-08-24"),
            status = PaymentClaimStatus.RESOLVED,
            createdAt = Instant.parse("2026-08-24T08:00:00Z"),
            resolvedAt = Instant.parse("2026-08-26T08:00:00Z"),
        )
        val attachment = AttachmentRecord(
            id = "attachment-1",
            debtId = debtId,
            ledgerEntryId = null,
            displayName = "proof.pdf",
            mimeType = "application/pdf",
            sizeBytes = 100,
            relativePath = "attachments/attachment-1.pdf",
            sha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T08:00:00Z"),
            note = null,
            integrity = AttachmentIntegrity.OK,
        )

        val timeline = PersonTimelineBuilder.timeline(
            accounts = listOf(account),
            extras = listOf(
                PersonAccountExtras(
                    debtId = debtId,
                    promises = listOf(promise),
                    claims = listOf(claim),
                    attachments = listOf(attachment),
                ),
            ),
        )

        assertEquals(PersonTimelineEventType.ATTACHMENT_ADDED, timeline.first().type)
        assertEquals(PersonTimelineEventType.ACCOUNT_OPENED, timeline.last().type)
        assertTrue(timeline.zipWithNext().all { (a, b) -> !a.occurredAt.isBefore(b.occurredAt) })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.PAYMENT_RECORDED })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.PAYMENT_REVERSED })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.PROMISE_CREATED })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.PROMISE_RESOLVED })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.CLAIM_CREATED })
        assertTrue(timeline.any { it.type == PersonTimelineEventType.CLAIM_RESOLVED })
    }

    private fun account(
        id: String,
        direction: DebtDirection,
        currency: CurrencyCode,
        original: Long,
        paid: Long,
    ): AccountOverview {
        val debtId = DebtId(id)
        var ledger = DebtLedger(
            DebtHeader(
                id = debtId,
                personId = personId,
                direction = direction,
                originalAmount = Money(original, currency),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
            ),
        )
        if (paid > 0) {
            ledger = ledger.recordPayment(
                id = LedgerEntryId("payment-$id"),
                amount = Money(paid, currency),
                paidAt = Instant.parse("2026-08-21T08:00:00Z"),
                recordedAt = Instant.parse("2026-08-21T08:00:00Z"),
            )
        }
        return AccountOverview(
            person = person,
            ledger = ledger,
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }
}
