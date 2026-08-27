package com.wasl.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentMessageSectionUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun receivableShowsThreeTonesAndChangesPreviewWithoutSending() {
        composeRule.setContent {
            WaslTheme { PaymentMessageSection(account(DebtDirection.RECEIVABLE)) }
        }

        composeRule.onNodeWithTag("payment-message-section").assertIsDisplayed()
        composeRule.onNodeWithTag("message-tone-gentle").assertIsDisplayed()
        composeRule.onNodeWithTag("message-tone-standard").assertIsDisplayed()
        composeRule.onNodeWithTag("message-tone-formal").assertIsDisplayed()
        composeRule.onNodeWithTag("copy-payment-message").assertIsDisplayed()
        composeRule.onNodeWithTag("share-payment-message").assertIsDisplayed()
        composeRule.onNodeWithText("وَصل لا يرسل الرسائل تلقائيًا.", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("message-tone-formal").performClick()
        composeRule.onNodeWithText("نود تذكيركم", substring = true).assertIsDisplayed()
    }

    @Test
    fun payableDoesNotExposeCollectionMessages() {
        composeRule.setContent {
            WaslTheme { PaymentMessageSection(account(DebtDirection.PAYABLE)) }
        }
        assertTrue(
            composeRule.onAllNodesWithTag("payment-message-section").fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun account(direction: DebtDirection): AccountOverview = AccountOverview(
        person = PersonRecord(
            id = PersonId("message-ui-person"),
            displayName = "عميل الرسائل",
            createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-20T08:00:00Z"),
        ),
        ledger = DebtLedger(
            DebtHeader(
                id = DebtId("message-ui-debt"),
                personId = PersonId("message-ui-person"),
                direction = direction,
                originalAmount = Money(15_000L, CurrencyCode.SAR),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                dueDate = LocalDate.parse("2026-09-01"),
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
    )
}
