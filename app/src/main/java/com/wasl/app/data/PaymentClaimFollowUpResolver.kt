package com.wasl.app.data

import java.time.LocalDate

/** Resolves only deterministic follow-up choices. Salary remains unresolved until the user supplies a policy/date. */
object PaymentClaimFollowUpResolver {
    fun resolve(
        kind: PaymentClaimFollowUpKind,
        today: LocalDate,
        customDate: LocalDate? = null,
    ): LocalDate? = when (kind) {
        PaymentClaimFollowUpKind.TODAY -> today
        PaymentClaimFollowUpKind.TOMORROW -> today.plusDays(1)
        PaymentClaimFollowUpKind.SALARY -> null
        PaymentClaimFollowUpKind.CUSTOM -> {
            val selected = requireNotNull(customDate) {
                "Custom payment claim follow-up requires a date."
            }
            require(!selected.isBefore(today)) {
                "Custom payment claim follow-up cannot be in the past."
            }
            selected
        }
    }
}
