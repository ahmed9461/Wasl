package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AdvancedSearchResult
import com.wasl.app.data.AdvancedSearchResultType
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
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveSearchUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontScaleStacksDenseSearchRowsAndKeepsResultsAccessible() {
        val account = accountOverview()
        val advanced = AdvancedSearchResult(
            id = "payment-accessible",
            type = AdvancedSearchResultType.PAYMENT,
            debtId = account.ledger.header.id,
            personName = account.person.displayName,
            description = "دفعة اختبار الإتاحة",
            amount = Money(20_000L, CurrencyCode.YER),
            date = LocalDate.parse("2026-08-20"),
        )
        var opened = 0

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 2f,
                ),
            ) {
                WaslTheme {
                    SearchScreen(
                        state = SearchUiState(
                            query = "اختبار",
                            normalizedQuery = "اختبار",
                            results = listOf(account),
                            advancedResults = listOf(advanced),
                        ),
                        onQueryChange = {},
                        onClearQuery = {},
                        onRetryLoad = {},
                        onOpenAccount = { opened += 1 },
                        onOpenHome = {},
                        onOpenToday = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("البحث")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "محددة",
                ),
            )
        composeRule.onNodeWithContentDescription("الرئيسية")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "غير محددة",
                ),
            )

        composeRule.onNodeWithTag("search-result-header-stacked-debt-accessible")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("search-result-balance-stacked-debt-accessible")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("search-advanced-header-stacked-payment-accessible")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("search-advanced-metadata-stacked-payment-accessible")
            .assertIsDisplayed()

        val accountDescription =
            "نتيجة حساب عميل الإتاحة، لي عنده، المتبقي 100,000 YER. افتح الحساب."
        composeRule.onNodeWithContentDescription(accountDescription)
            .assertHasClickAction()
            .performClick()

        val advancedDescription =
            "نتيجة دفعة مرتبطة بـعميل الإتاحة، المبلغ 20,000 YER، التاريخ 20/08/2026. افتح الحساب المرتبط."
        composeRule.onNodeWithContentDescription(advancedDescription)
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(2, opened) }
    }

    private fun accountOverview(): AccountOverview {
        val createdAt = Instant.parse("2026-08-01T00:00:00Z")
        val personId = PersonId("person-accessible")
        return AccountOverview(
            person = PersonRecord(
                id = personId,
                displayName = "عميل الإتاحة",
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
            ledger = DebtLedger(
                DebtHeader(
                    id = DebtId("debt-accessible"),
                    personId = personId,
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = createdAt,
                    description = "نص طويل لاختبار إعادة التدفق",
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }
}
