package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaymentMessageTemplatesTest {
    @Test
    fun receivableProducesThreeExplicitUserShareDraftsWithBalanceAndDueDate() {
        val account = account(DebtDirection.RECEIVABLE)

        val drafts = PaymentMessageTemplates.forAccount(account)

        assertEquals(
            listOf(PaymentMessageTone.GENTLE, PaymentMessageTone.STANDARD, PaymentMessageTone.FORMAL),
            drafts.map { it.tone },
        )
        assertTrue(drafts.all { it.body.contains("أحمد العميل") })
        assertTrue(drafts.all { it.body.contains("75,000 YER") })
        assertTrue(drafts.all { it.body.contains("30/08/2026") })
        assertTrue(drafts.none { it.body.contains("تم الإرسال") })
    }

    @Test
    fun payableDoesNotGenerateCollectionMessage() {
        assertFailsWith<IllegalArgumentException> {
            PaymentMessageTemplates.forAccount(account(DebtDirection.PAYABLE))
        }
    }

    private fun account(direction: DebtDirection): AccountOverview = AccountOverview(
        person = PersonRecord(
            id = PersonId("message-person"),
            displayName = "أحمد العميل",
            phone = "+967777000000",
            createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-20T08:00:00Z"),
        ),
        ledger = DebtLedger(
            DebtHeader(
                id = DebtId("message-debt"),
                personId = PersonId("message-person"),
                direction = direction,
                originalAmount = Money(75_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                dueDate = LocalDate.parse("2026-08-30"),
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
    )
}
