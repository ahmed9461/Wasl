package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertEquals

class PaymentClaimModelsTest {
    private val debtId = DebtId("debt-claim-test")
    private val requestedAt = Instant.parse("2026-08-27T06:00:00Z")

    @Test
    fun `open claim preserves follow up without mutating financial concepts`() {
        val record = PaymentClaimRecord(
            id = "claim-1",
            debtId = debtId,
            requestedAt = requestedAt,
            followUpKind = PaymentClaimFollowUpKind.TOMORROW,
            followUpDate = LocalDate.parse("2026-08-28"),
            note = "طلب السداد عبر رسالة",
            status = PaymentClaimStatus.OPEN,
            createdAt = requestedAt,
        )

        assertEquals(PaymentClaimStatus.OPEN, record.status)
        assertEquals(PaymentClaimFollowUpKind.TOMORROW, record.followUpKind)
        assertNull(record.resolvedAt)
    }

    @Test
    fun `resolved claim requires a resolution timestamp`() {
        assertFailsWith<IllegalArgumentException> {
            PaymentClaimRecord(
                id = "claim-2",
                debtId = debtId,
                requestedAt = requestedAt,
                followUpKind = PaymentClaimFollowUpKind.TODAY,
                followUpDate = LocalDate.parse("2026-08-27"),
                status = PaymentClaimStatus.COMPLETED,
                createdAt = requestedAt,
                resolvedAt = null,
            )
        }
    }

    @Test
    fun `resolution command cannot keep claim open`() {
        assertFailsWith<IllegalArgumentException> {
            ResolvePaymentClaimCommand(
                commandId = "resolve-1",
                claimId = "claim-1",
                debtId = debtId,
                status = PaymentClaimStatus.OPEN,
                resolvedAt = requestedAt.plusSeconds(60),
            )
        }
    }

    @Test
    fun `create command cannot predate request`() {
        assertFailsWith<IllegalArgumentException> {
            CreatePaymentClaimCommand(
                commandId = "create-1",
                claimId = "claim-1",
                debtId = debtId,
                requestedAt = requestedAt,
                followUpKind = PaymentClaimFollowUpKind.TODAY,
                followUpDate = LocalDate.parse("2026-08-27"),
                createdAt = requestedAt.minusSeconds(1),
            )
        }
    }
}
