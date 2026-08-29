package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
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
class SearchUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-search-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun searchesByDescriptionOpensTheAccountAndReturnsToTheSameQuery() {
        runBlocking {
            repository.createPersonWithDebt(
                command(
                    suffix = "rent",
                    personName = "خالد",
                    description = "إيجار المنزل",
                ),
            )
        }
        showApp("search-open-ui")

        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("إيجار")
        waitForTag("search-result-debt-rent")

        composeRule.onNodeWithTag("search-result-debt-rent")
            .performScrollTo()
            .performClick()
        waitForText("خالد")
        composeRule.onNodeWithText("خالد").assertIsDisplayed()

        composeRule.onNodeWithTag("account-details-back").performClick()
        waitForTag("search-result-debt-rent")
        composeRule.onNodeWithTag("search-input").assertTextContains("إيجار")
    }

    @Test
    fun activeResultsReactAfterDebtCreationAndPayment() {
        showApp("search-reactive-ui")

        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("مشترك")
        waitForTag("search-empty")

        runBlocking {
            repository.createPersonWithDebt(
                command(
                    suffix = "reactive",
                    personName = "أحمد",
                    description = "حساب مشترك",
                ),
            )
        }
        waitForTag("search-result-debt-reactive")
        composeRule.onNodeWithTag(
            testTag = "search-balance-debt-reactive",
            useUnmergedTree = true,
        )
            .assertTextContains("100,000 YER", substring = true)

        runBlocking {
            repository.recordPayment(
                RecordPaymentCommand(
                    commandId = "search-ui-payment-command",
                    entryId = LedgerEntryId("search-ui-payment"),
                    debtId = DebtId("debt-reactive"),
                    amount = Money(20_000L, CurrencyCode.YER),
                    paidAt = Instant.parse("2026-08-13T00:01:00Z"),
                    recordedAt = Instant.parse("2026-08-13T00:01:00Z"),
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(
                    testTag = "search-balance-debt-reactive",
                    useUnmergedTree = true,
                )
                    .assertTextContains("80,000 YER", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag(
            testTag = "search-balance-debt-reactive",
            useUnmergedTree = true,
        )
            .assertTextContains("80,000 YER", substring = true)
    }

    private fun showApp(instanceKey: String) {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = instanceKey,
            )
        }
    }

    private fun command(
        suffix: String,
        personName: String,
        description: String,
    ): CreatePersonWithDebtCommand {
        val now = Instant.parse("2026-08-13T00:00:00Z")
        return CreatePersonWithDebtCommand(
            personId = PersonId("person-$suffix"),
            debtId = DebtId("debt-$suffix"),
            personName = personName,
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(100_000L, CurrencyCode.YER),
            openedAt = now,
            createdAt = now,
            description = description,
        )
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
