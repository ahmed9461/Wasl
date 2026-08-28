package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaymentClaimModelsTest {
    private val debtId = DebtId("debt-claim-test")
    private val claimedAt = Instant.parse("2026-08-27T06:00:00Z")

    @Test
    fun `active claim preserves follow up without mutating financial concepts`() {
        val record = PaymentClaimRecord(
            id = "claim-1",
            debtId = debtId,
            claimedAt = claimedAt,
            followUpKind = PaymentClaimFollowUpKind.TOMORROW,
            followUpDate = LocalDate.parse("2026-08-28"),
            note = "طلب السداد عبر رسالة",
            status = PaymentClaimStatus.ACTIVE,
            createdAt = claimedAt,
        )

        assertEquals(PaymentClaimStatus.ACTIVE, record.status)
        assertEquals(PaymentClaimFollowUpKind.TOMORROW, record.followUpKind)
        assertNull(record.resolvedAt)
    }

    @Test
    fun `resolved claim requires a resolution timestamp`() {
        assertFailsWith<IllegalArgumentException> {
            PaymentClaimRecord(
                id = "claim-2",
                debtId = debtId,
                claimedAt = claimedAt,
                followUpKind = PaymentClaimFollowUpKind.TODAY,
                followUpDate = LocalDate.parse("2026-08-27"),
                status = PaymentClaimStatus.RESOLVED,
                createdAt = claimedAt,
                resolvedAt = null,
            )
        }
    }

    @Test
    fun `resolution command cannot keep claim active`() {
        assertFailsWith<IllegalArgumentException> {
            ResolvePaymentClaimCommand(
                commandId = "resolve-1",
                claimId = "claim-1",
                debtId = debtId,
                status = PaymentClaimStatus.ACTIVE,
                resolvedAt = claimedAt.plusSeconds(60),
            )
        }
    }

    @Test
    fun `create command cannot predate claim`() {
        assertFailsWith<IllegalArgumentException> {
            CreatePaymentClaimCommand(
                commandId = "create-1",
                claimId = "claim-1",
                debtId = debtId,
                claimedAt = claimedAt,
                followUpKind = PaymentClaimFollowUpKind.TODAY,
                followUpDate = LocalDate.parse("2026-08-27"),
                createdAt = claimedAt.minusSeconds(1),
            )
        }
    }

    @Test
    fun `salary follow up does not invent a date`() {
        val record = PaymentClaimRecord(
            id = "claim-salary",
            debtId = debtId,
            claimedAt = claimedAt,
            followUpKind = PaymentClaimFollowUpKind.SALARY,
            followUpDate = null,
            status = PaymentClaimStatus.ACTIVE,
            createdAt = claimedAt,
        )

        assertNull(record.followUpDate)
    }

    @Test
    fun `custom follow up requires explicit date`() {
        assertFailsWith<IllegalArgumentException> {
            CreatePaymentClaimCommand(
                commandId = "create-custom",
                claimId = "claim-custom",
                debtId = debtId,
                claimedAt = claimedAt,
                followUpKind = PaymentClaimFollowUpKind.CUSTOM,
                followUpDate = null,
                createdAt = claimedAt,
            )
        }
    }
}
