package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DueDateUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-due-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-due"),
                    debtId = DebtId("debt-due"),
                    personName = "أحمد",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-13T00:00:00Z"),
                    createdAt = Instant.parse("2026-08-13T00:00:00Z"),
                    dueDate = LocalDate.parse("2026-08-14"),
                    dueReminder = DueReminderRequest(
                        id = "reminder-due",
                        triggerAt = Instant.parse("2026-08-14T06:00:00Z"),
                        zoneId = ZoneId.of("Asia/Riyadh"),
                    ),
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
    fun deepLinkedDetailsShowDueDateAndScheduledReminder() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "due-date-ui-test",
                requestedDebtId = "debt-due",
            )
        }

        waitForText("تاريخ الاستحقاق")
        composeRule.onNodeWithText("2026-08-14", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("موعد التذكير")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("مجدول")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun removingDueDateCancelsReminderAndShowsAuditInTimeline() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "due-date-edit-ui-test",
                requestedDebtId = "debt-due",
            )
        }

        waitForTag("edit-due-schedule")
        composeRule.onNodeWithTag("edit-due-schedule")
            .performScrollTo()
            .performClick()
        waitForTag("remove-due-date")
        composeRule.onNodeWithTag("remove-due-date").performClick()
        composeRule.onNodeWithTag("save-due-schedule").performClick()

        composeRule.onNode(
            hasScrollAction() and hasAnyDescendant(hasText("سجل العمليات")),
        ).performScrollToNode(hasText("تم إلغاء تاريخ الاستحقاق"))
        composeRule.onNodeWithText("تم إلغاء تاريخ الاستحقاق").assertIsDisplayed()
        runBlocking {
            val account = requireNotNull(repository.getAccount(DebtId("debt-due")))
            assertNull(account.ledger.header.dueDate)
            assertEquals(com.wasl.app.data.ReminderStatus.CANCELLED, account.dueReminder?.status)
            assertEquals(1, account.dueScheduleAuditEvents.size)
        }
    }

    @Test
    fun strongAlarmToggleShowsExactAlarmPermissionGuidance() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "strong-alarm-ui-test",
                exactAlarmAccessOverride = false,
                requestedDebtId = "debt-due",
            )
        }

        waitForTag("edit-due-schedule")
        composeRule.onNodeWithTag("edit-due-schedule")
            .performScrollTo()
            .performClick()
        waitForTag("edit-strong-alarm")
        composeRule.onNodeWithTag("edit-strong-alarm").performClick()
        waitForTag("exact-alarm-permission-warning")
        composeRule.onNodeWithTag("exact-alarm-permission-warning").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
