package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayPaymentPromiseUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var promiseStore: RoomPaymentPromiseStore

    private val fixedNow = Instant.parse("2026-08-24T10:00:00Z")
    private val today = LocalDate.parse("2026-08-24")

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-today-promises-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        promiseStore = RoomPaymentPromiseStore(database)

        runBlocking {
            createDebt("overdue", "سالم", 120_000L)
            createDebt("today", "عبدالله", 90_000L)
            createDebt("future", "ناصر", 70_000L)

            createPromise(
                debtSuffix = "overdue",
                promiseId = "promise-overdue",
                promisedDate = LocalDate.parse("2026-08-22"),
                note = "وعد بعد نزول الراتب",
            )
            createPromise(
                debtSuffix = "today",
                promiseId = "promise-today",
                promisedDate = today,
                note = "سداد اليوم",
            )
            createPromise(
                debtSuffix = "future",
                promiseId = "promise-future",
                promisedDate = LocalDate.parse("2026-08-25"),
                note = "وعد غدًا",
            )
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun todayShowsOverdueAndDuePromisesButNotFuturePromise() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                paymentPromiseStore = promiseStore,
                instanceKey = "today-promise-ui-test",
                todayClock = Clock.fixed(fixedNow, ZoneOffset.UTC),
                todayZoneIdProvider = { ZoneId.of("UTC") },
            )
        }

        waitForTag("nav-today")
        composeRule.onNodeWithTag("nav-today").performClick()

        scrollToTag("today-promise-promise-overdue")
        composeRule.onNodeWithTag("today-promise-promise-overdue").assertIsDisplayed()
        composeRule.onNodeWithText("وعود متأخرة").assertIsDisplayed()

        scrollToTag("today-promise-promise-today")
        composeRule.onNodeWithTag("today-promise-promise-today").assertIsDisplayed()
        composeRule.onNodeWithText("وعود اليوم").assertIsDisplayed()

        composeRule.onNodeWithTag("today-promise-promise-future").assertDoesNotExist()

        composeRule.onNodeWithTag("today-open-promise-promise-today").performClick()
        waitForTag("account-remaining")
        composeRule.onNodeWithText("عبدالله").assertIsDisplayed()
    }

    private suspend fun createDebt(suffix: String, name: String, amountMinor: Long) {
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("person-$suffix"),
                debtId = DebtId("debt-$suffix"),
                personName = name,
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(amountMinor, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                createdAt = Instant.parse("2026-08-20T08:00:00Z"),
            ),
        )
    }

    private suspend fun createPromise(
        debtSuffix: String,
        promiseId: String,
        promisedDate: LocalDate,
        note: String,
    ) {
        promiseStore.createPaymentPromise(
            CreatePaymentPromiseCommand(
                commandId = "command-$promiseId",
                promiseId = promiseId,
                debtId = DebtId("debt-$debtSuffix"),
                promisedDate = promisedDate,
                note = note,
                createdAt = Instant.parse("2026-08-21T09:00:00Z"),
            ),
        )
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
