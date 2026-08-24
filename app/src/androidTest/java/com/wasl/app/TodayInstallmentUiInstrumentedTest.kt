package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreateInstallmentPlanCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.InstallmentAwareWaslRepository
import com.wasl.app.data.InstallmentPlanItemInput
import com.wasl.app.data.local.RoomInstallmentPlanStore
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
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayInstallmentUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var roomRepository: RoomWaslRepository
    private lateinit var installmentStore: RoomInstallmentPlanStore
    private lateinit var repository: InstallmentAwareWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-today-installments-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        roomRepository = RoomWaslRepository(database)
        installmentStore = RoomInstallmentPlanStore(database, roomRepository)
        repository = InstallmentAwareWaslRepository(roomRepository, installmentStore)

        runBlocking {
            roomRepository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-installment-today"),
                    debtId = DebtId("debt-installment-today"),
                    personName = "فهد",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(120_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-20T08:00:00Z"),
                    description = "حساب بأقساط",
                ),
            )
            installmentStore.createInstallmentPlan(
                CreateInstallmentPlanCommand(
                    commandId = "installment-command-today",
                    planId = "installment-plan-today",
                    debtId = DebtId("debt-installment-today"),
                    installments = listOf(
                        InstallmentPlanItemInput(
                            id = "installment-overdue",
                            sequenceNumber = 1,
                            dueDate = LocalDate.parse("2026-08-22"),
                            amount = Money(40_000L, CurrencyCode.YER),
                        ),
                        InstallmentPlanItemInput(
                            id = "installment-today",
                            sequenceNumber = 2,
                            dueDate = LocalDate.parse("2026-08-24"),
                            amount = Money(40_000L, CurrencyCode.YER),
                        ),
                        InstallmentPlanItemInput(
                            id = "installment-future",
                            sequenceNumber = 3,
                            dueDate = LocalDate.parse("2026-08-25"),
                            amount = Money(40_000L, CurrencyCode.YER),
                        ),
                    ),
                    createdAt = Instant.parse("2026-08-20T09:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun todayShowsOverdueAndDueInstallmentsButNotFutureInstallment() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "today-installment-ui-test",
                todayClock = Clock.fixed(
                    Instant.parse("2026-08-24T10:00:00Z"),
                    ZoneOffset.UTC,
                ),
                todayZoneIdProvider = { ZoneOffset.UTC },
            )
        }

        waitForTag("nav-today")
        composeRule.onNodeWithTag("nav-today").performClick()

        scrollToTag("today-installment-installment-overdue")
        composeRule.onNodeWithTag("today-installment-installment-overdue").assertIsDisplayed()
        composeRule.onNodeWithText("أقساط متأخرة").assertIsDisplayed()

        scrollToTag("today-installment-installment-today")
        composeRule.onNodeWithTag("today-installment-installment-today").assertIsDisplayed()
        composeRule.onNodeWithText("أقساط اليوم").assertIsDisplayed()

        composeRule.onNodeWithTag("today-installment-installment-future").assertDoesNotExist()

        scrollToTag("today-open-installment-installment-today")
        composeRule.onNodeWithTag("today-open-installment-installment-today").performClick()
        waitForTag("account-remaining")
        composeRule.onNodeWithText("فهد").assertIsDisplayed()
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
