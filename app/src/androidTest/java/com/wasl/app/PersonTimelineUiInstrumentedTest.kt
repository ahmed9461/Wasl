package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonTimelineUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multiCurrencyPersonPageKeepsAccountsSeparateAndCanOpenAnotherAccount() {
        val person = PersonRecord(
            id = PersonId("person-ui"),
            displayName = "شخص متعدد العملات",
            phone = "+967777000000",
            createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-20T08:00:00Z"),
        )
        val first = account(person, "debt-yer", CurrencyCode.YER, DebtDirection.RECEIVABLE, 50_000)
        val second = account(person, "debt-sar", CurrencyCode.SAR, DebtDirection.PAYABLE, 10_000)
        val groups = PersonTimelineBuilder.balanceGroups(listOf(first, second))
        val timeline = PersonTimelineBuilder.timeline(listOf(first, second), emptyList())
        var opened: DebtId? = null

        composeRule.setContent {
            WaslTheme {
                PersonTimelineScreen(
                    state = PersonTimelineUiState(
                        isLoading = false,
                        person = person,
                        accounts = listOf(first, second),
                        balanceGroups = groups,
                        timeline = timeline,
                    ),
                    onBack = {},
                    onRetry = {},
                    onOpenAccount = { opened = it },
                )
            }
        }

        composeRule.onNodeWithTag("person-profile-header").assertExists()
        composeRule.onNodeWithTag("person-multi-currency-note").assertExists()
        composeRule.onNodeWithTag("person-account-debt-sar")
            .performScrollTo()
            .assertExists()
            .performClick()
        assertEquals(DebtId("debt-sar"), opened)
    }

    @Test
    fun largeFontScaleKeepsPersonHeaderAndTimelineReachable() {
        val person = PersonRecord(
            id = PersonId("person-large-font"),
            displayName = "اسم شخص طويل لاختبار تكبير الخط والوصول",
            notes = "ملاحظة طويلة يجب أن تبقى قابلة للقراءة دون قص المعنى الأساسي.",
            createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-20T08:00:00Z"),
        )
        val account = account(
            person,
            "large-font-debt",
            CurrencyCode.USD,
            DebtDirection.RECEIVABLE,
            12_345,
        )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    PersonTimelineScreen(
                        state = PersonTimelineUiState(
                            isLoading = false,
                            person = person,
                            accounts = listOf(account),
                            balanceGroups = PersonTimelineBuilder.balanceGroups(listOf(account)),
                            timeline = PersonTimelineBuilder.timeline(listOf(account), emptyList()),
                        ),
                        onBack = {},
                        onRetry = {},
                        onOpenAccount = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("person-profile-header").assertExists()
        composeRule.onNodeWithTag("person-account-large-font-debt")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag("person-timeline-opened:large-font-debt")
            .performScrollTo()
            .assertExists()
    }

    private fun account(
        person: PersonRecord,
        id: String,
        currency: CurrencyCode,
        direction: DebtDirection,
        amount: Long,
    ): AccountOverview = AccountOverview(
        person = person,
        ledger = DebtLedger(
            DebtHeader(
                id = DebtId(id),
                personId = person.id,
                direction = direction,
                originalAmount = Money(amount, currency),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                description = "حساب $id",
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
    )
}
