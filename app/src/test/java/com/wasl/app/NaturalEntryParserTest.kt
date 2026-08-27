package com.wasl.app

import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaturalEntryParserTest {
    private val parser = NaturalEntryParser {
        LocalDate.parse("2026-08-27") // Thursday
    }

    @Test
    fun parsesReceivableSaudiDebtAndNextThursdayPromise() {
        val draft = parser.parse(
            "سلفت عبدالله خمسة آلاف ريال سعودي اليوم وقال بيرجعها الخميس.",
        )

        assertEquals(NaturalEntryKind.DEBT, draft.kind)
        assertEquals("عبدالله", draft.personName)
        assertEquals(DebtDirection.RECEIVABLE, draft.direction)
        assertEquals(CurrencyCode.SAR, draft.currency)
        assertEquals(500_000L, draft.amountMinorUnits)
        assertEquals(LocalDate.parse("2026-08-27"), draft.entryDate)
        assertEquals(LocalDate.parse("2026-09-03"), draft.promisedDate)
        assertTrue(draft.missingRequiredFields.isEmpty())
        assertTrue(draft.canPreviewAsDebt)
        assertTrue(draft.requiresExplicitConfirmation)
    }

    @Test
    fun parsesPayableYemeniDebtFromYesterdayAndEndOfMonthPromise() {
        val draft = parser.parse(
            "علي لمحمد 3000 يمني من أمس وبرجعها آخر الشهر.",
        )

        assertEquals(NaturalEntryKind.DEBT, draft.kind)
        assertEquals("محمد", draft.personName)
        assertEquals(DebtDirection.PAYABLE, draft.direction)
        assertEquals(CurrencyCode.YER, draft.currency)
        assertEquals(3_000L, draft.amountMinorUnits)
        assertEquals(LocalDate.parse("2026-08-26"), draft.entryDate)
        assertEquals(LocalDate.parse("2026-08-31"), draft.promisedDate)
        assertTrue(draft.canPreviewAsDebt)
    }

    @Test
    fun normalizesArabicDigitsButRefusesIncompleteDraft() {
        val draft = parser.parse("سلفت خالد ١٢٠٠ اليوم")

        assertEquals("خالد", draft.personName)
        assertEquals(DebtDirection.RECEIVABLE, draft.direction)
        assertEquals(NaturalEntryKind.DEBT, draft.kind)
        assertTrue(NaturalDraftField.CURRENCY in draft.missingRequiredFields)
        assertFalse(draft.canPreviewAsDebt)
        assertTrue(draft.requiresExplicitConfirmation)
    }

    @Test
    fun paymentTextNeverBecomesDebtReady() {
        val draft = parser.parse("دفعت لمحمد 500 ريال سعودي اليوم")

        assertEquals(NaturalEntryKind.PAYMENT, draft.kind)
        assertFalse(draft.canPreviewAsDebt)
        assertTrue(draft.requiresExplicitConfirmation)
        assertTrue(draft.warnings.isNotEmpty())
    }
}
