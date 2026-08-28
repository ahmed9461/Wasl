package com.wasl.app.data

import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PersonContactDetailsCommandTest {
    private val now = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun blankExplicitPhoneAndEmailAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            command(personPhone = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            command(personEmail = "  ")
        }
    }

    private fun command(
        personPhone: String? = null,
        personEmail: String? = null,
    ) = CreatePersonWithDebtCommand(
        personId = PersonId("person-contact-command"),
        debtId = DebtId("debt-contact-command"),
        personName = "أحمد",
        personPhone = personPhone,
        personEmail = personEmail,
        direction = DebtDirection.RECEIVABLE,
        originalAmount = Money(100_000L, CurrencyCode.YER),
        openedAt = now,
        createdAt = now,
    )
}
