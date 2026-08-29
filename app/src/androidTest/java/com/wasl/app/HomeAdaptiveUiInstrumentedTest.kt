package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAdaptiveUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontKeepsCompactAccountCardReachableAndClickable() {
        val account = partialAccount()
        var clicks = 0

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    AccountCard(
                        account = account,
                        onClick = { clicks += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account-debt-1", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-debt-1-header-inline", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-debt-1-balance-inline", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-debt-1-original-inline", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-debt-1").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun largeFontStacksEverySummaryCurrencyRow() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    SummaryCard(
                        title = "لي عند الناس",
                        subtitle = "حقوقك المفتوحة",
                        values = listOf(
                            formatMoney(Money(100_000L, CurrencyCode.YER)),
                            formatMoney(Money(25_000L, CurrencyCode.SAR)),
                            formatMoney(Money(12_345L, CurrencyCode.USD)),
                        ),
                        receivable = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("summary-receivable-YER-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-receivable-SAR-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("summary-receivable-USD-stacked").assertIsDisplayed()
    }

    private fun partialAccount(): AccountOverview {
        val openedAt = Instant.parse("2026-01-01T00:00:00Z")
        val personId = PersonId("person-1")
        val header = DebtHeader(
            id = DebtId("debt-1"),
            personId = personId,
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(250_000L, CurrencyCode.SAR),
            openedAt = openedAt,
            description = "شراء أجهزة ومستلزمات مع وصف أطول لاختبار تكبير النص داخل البطاقة.",
        )
        val ledger = DebtLedger(header).recordPayment(
            id = LedgerEntryId("payment-1"),
            amount = Money(50_000L, CurrencyCode.SAR),
            paidAt = openedAt.plusSeconds(60),
        )
        return AccountOverview(
            person = PersonRecord(
                id = personId,
                displayName = "محمد عبدالله الطويل للاختبار",
                createdAt = openedAt,
                updatedAt = openedAt,
            ),
            ledger = ledger,
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }
}
