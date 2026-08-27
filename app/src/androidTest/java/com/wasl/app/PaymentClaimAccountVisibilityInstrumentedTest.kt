package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentClaimAccountVisibilityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-payment-claim-visibility-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        runBlocking {
            createDebt("payable-claim-ui", "سالم", DebtDirection.PAYABLE)
            createDebt("receivable-no-claim-ui", "مازن", DebtDirection.RECEIVABLE)
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun payableAccountShowsPaymentClaimAction() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "payment-claim-payable-ui",
                requestedDebtId = "payable-claim-ui",
            )
        }

        waitForTag("account-remaining")
        scrollToTag("add-payment-claim")
        composeRule.onNodeWithTag("add-payment-claim").assertIsDisplayed()
    }

    @Test
    fun receivableAccountDoesNotShowPaymentClaimAction() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "payment-claim-receivable-ui",
                requestedDebtId = "receivable-no-claim-ui",
            )
        }

        waitForTag("account-remaining")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("وعود السداد"))
        composeRule.onNodeWithTag("add-payment-claim").assertDoesNotExist()
    }

    private suspend fun createDebt(
        id: String,
        personName: String,
        direction: DebtDirection,
    ) {
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("person-$id"),
                debtId = DebtId(id),
                personName = personName,
                direction = direction,
                originalAmount = Money(50_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            ),
        )
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNode(hasScrollAction()).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }
}
