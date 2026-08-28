package com.wasl.app.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaymentClaimFollowUpResolverTest {
    private val today = LocalDate.parse("2026-08-27")

    @Test
    fun `today and tomorrow are deterministic`() {
        assertEquals(
            today,
            PaymentClaimFollowUpResolver.resolve(PaymentClaimFollowUpKind.TODAY, today),
        )
        assertEquals(
            today.plusDays(1),
            PaymentClaimFollowUpResolver.resolve(PaymentClaimFollowUpKind.TOMORROW, today),
        )
    }

    @Test
    fun `salary never guesses a date`() {
        assertNull(
            PaymentClaimFollowUpResolver.resolve(PaymentClaimFollowUpKind.SALARY, today),
        )
    }

    @Test
    fun `custom requires non past date`() {
        assertFailsWith<IllegalArgumentException> {
            PaymentClaimFollowUpResolver.resolve(
                PaymentClaimFollowUpKind.CUSTOM,
                today,
                today.minusDays(1),
            )
        }
        assertEquals(
            today.plusDays(5),
            PaymentClaimFollowUpResolver.resolve(
                PaymentClaimFollowUpKind.CUSTOM,
                today,
                today.plusDays(5),
            ),
        )
    }
}
