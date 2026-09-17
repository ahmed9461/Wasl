package com.wasl.app

import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun preservesDecimalAndArabicGroupedAmounts() {
        for (raw in listOf("1,234.50", "١٬٢٣٤٫٥٠", "۱٬۲۳۴٫۵۰", "1234,50")) {
            val draft = parser.parse("سلفت خالد $raw سعودي اليوم")
            assertEquals(123_450L, draft.amountMinorUnits, raw)
            assertTrue(draft.canPreviewAsDebt, raw)
            assertTrue(draft.requiresExplicitConfirmation)
        }
        assertEquals(12550L, parser.parse("سلفت خالد 125.50 سعودي").amountMinorUnits)
        assertEquals(1234L, parser.parse("سلفت خالد ١٬٢٣٤ يمني").amountMinorUnits)
    }

    @Test
    fun invalidAmountsCannotBecomeReadyOrCrashDuringPreview() {
        for (raw in listOf("9223372036854775807", "9".repeat(200), "1,2.50", "1e3", "1.001", "-50", "0", "١٢٬٣٤")) {
            val draft = parser.parse("سلفت خالد $raw سعودي اليوم")
            assertNull(draft.amountMinorUnits, raw)
            assertTrue(NaturalDraftField.AMOUNT in draft.missingRequiredFields, raw)
            assertFalse(draft.canPreviewAsDebt, raw)
            assertTrue(draft.warnings.isNotEmpty(), raw)
            assertTrue(draft.requiresExplicitConfirmation)
        }
    }

    @Test
    fun refusesAmbiguousNumbersRatherThanChoosingAnAmountOrDate() {
        for (text in listOf(
            "سلفت خالد 100 سعودي و200 سعودي",
            "سلفت خالد 2026-09-17 بمبلغ 100 سعودي",
            "سلفت خالد 1 234 سعودي",
        )) {
            val draft = parser.parse(text)
            assertNull(draft.amountMinorUnits)
            assertTrue(NaturalDraftField.AMOUNT in draft.missingRequiredFields)
            assertFalse(draft.canPreviewAsDebt)
        }
    }

    @Test
    fun unsupportedYemeniFractionIsNotSilentlyTruncated() {
        val draft = parser.parse("سلفت خالد 125.50 يمني")
        assertNull(draft.amountMinorUnits)
        assertFalse(draft.canPreviewAsDebt)
    }

    @Test
    fun mixedWordAmountsCannotBeSilentlyReducedToTheirNumericPrefix() {
        for (text in listOf(
            "سلفت خالد 5 آلاف سعودي",
            "سلفت خالد 5 مليون دولار",
            "سلفت خالد 100 ونصف سعودي",
        )) {
            val draft = parser.parse(text)
            assertNull(draft.amountMinorUnits, text)
            assertTrue(NaturalDraftField.AMOUNT in draft.missingRequiredFields, text)
            assertFalse(draft.canPreviewAsDebt, text)
        }
    }

    @Test
    fun compoundOrMultipleWordAmountsRequireCorrection() {
        for (text in listOf(
            "سلفت خالد خمسة آلاف وخمسمئة سعودي",
            "سلفت خالد خمسة الفين سعودي",
            "سلفت خالد خمسة آلاف سعودي وعشرة آلاف سعودي",
        )) {
            val draft = parser.parse(text)
            assertNull(draft.amountMinorUnits, text)
            assertFalse(draft.canPreviewAsDebt, text)
        }
    }

    @Test
    fun currencyMustBeExplicitAndUnambiguousRatherThanPartOfAPersonName() {
        for (text in listOf(
            "سلفت sara 100",
            "سلفت خالد 100 سعودي أو يمني",
            "سلفت خالد 100 SAR USD",
        )) {
            val draft = parser.parse(text)
            assertNull(draft.currency, text)
            assertNull(draft.amountMinorUnits, text)
            assertTrue(NaturalDraftField.CURRENCY in draft.missingRequiredFields, text)
            assertFalse(draft.canPreviewAsDebt, text)
        }
        assertEquals(CurrencyCode.SAR, parser.parse("سلفت sara 100 SAR").currency)
        assertEquals(10_000L, parser.parse("سلفت sara 100 SAR").amountMinorUnits)
    }
}
