package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.DueState
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
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-today-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        runBlocking {
            repository.createPersonWithDebt(
                command(
                    suffix = "overdue",
                    personName = "شخص متأخر",
                    dueDate = LocalDate.parse("2026-08-11"),
                    reminderStatus = ReminderStatus.BLOCKED_PERMISSION,
                ),
            )
            repository.markReminderBlockedByPermission(
                reminderId = "reminder-overdue",
                updatedAt = Instant.parse("2026-08-14T07:00:00Z"),
            )
            repository.createPersonWithDebt(
                command(
                    suffix = "today",
                    personName = "شخص اليوم",
                    dueDate = LocalDate.parse("2026-08-14"),
                ),
            )
            repository.createPersonWithDebt(
                command(
                    suffix = "upcoming",
                    personName = "شخص قادم",
                    dueDate = LocalDate.parse("2026-08-15"),
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
    fun todayDestinationShowsOnlyDueWorkAndOpensItsAccount() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "today-ui-test",
                todayClock = Clock.fixed(
                    Instant.parse("2026-08-14T08:00:00Z"),
                    ZoneOffset.UTC,
                ),
                todayZoneIdProvider = { ZoneOffset.UTC },
            )
        }

        composeRule.onNodeWithTag("nav-today").performClick()
        waitForText("اليوم لديك 2 أمور")

        composeRule.onNodeWithText("متأخر 3 أيام").assertIsDisplayed()
        composeRule.onNodeWithText("مستحق اليوم")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("شخص قادم").assertDoesNotExist()
        composeRule.onNodeWithText(
            "التذكير متوقف حتى تسمح بإشعارات وَصل.",
        )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("today-open-debt-today")
            .performScrollTo()
            .performClick()
        waitForText("سجل العمليات")
        composeRule.onNodeWithText("شخص اليوم").assertIsDisplayed()
    }

    @Test
    fun blockedAndFailedReminderButtonsDispatchTheCorrectActions() {
        var permissionActions = 0
        var retryActions = 0
        val blocked = uiItem("blocked", ReminderStatus.BLOCKED_PERMISSION)
        val failed = uiItem("failed", ReminderStatus.FAILED)

        composeRule.setContent {
            TodayScreen(
                state = TodayUiState(
                    today = LocalDate.parse("2026-08-14"),
                    isLoading = false,
                    items = listOf(blocked, failed),
                ),
                notificationsAvailable = false,
                onOpenHome = {},
                onOpenSearch = {},
                onOpenAccount = {},
                onRefreshDate = {},
                onRetryLoad = {},
                onResolveNotificationPermission = { permissionActions += 1 },
                onRetryReminders = { retryActions += 1 },
                onNoticeShown = {},
            )
        }

        composeRule.onNodeWithTag("today-enable-notifications-debt-blocked")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("today-retry-reminder-debt-failed")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, permissionActions)
            assertEquals(1, retryActions)
        }
    }

    private fun command(
        suffix: String,
        personName: String,
        dueDate: LocalDate,
        reminderStatus: ReminderStatus? = null,
    ): CreatePersonWithDebtCommand {
        val openedAt = Instant.parse("2026-08-01T00:00:00Z")
        return CreatePersonWithDebtCommand(
            personId = PersonId("person-$suffix"),
            debtId = DebtId("debt-$suffix"),
            personName = personName,
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(100_000L, CurrencyCode.YER),
            openedAt = openedAt,
            createdAt = openedAt,
            dueDate = dueDate,
            dueReminder = reminderStatus?.let {
                DueReminderRequest(
                    id = "reminder-$suffix",
                    triggerAt = dueDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    zoneId = ZoneOffset.UTC,
                )
            },
        )
    }

    private fun uiItem(suffix: String, status: ReminderStatus): TodayItem {
        val openedAt = Instant.parse("2026-08-01T00:00:00Z")
        val debtId = DebtId("debt-$suffix")
        return TodayItem(
            account = AccountOverview(
                person = PersonRecord(
                    id = PersonId("person-$suffix"),
                    displayName = "شخص $suffix",
                    createdAt = openedAt,
                    updatedAt = openedAt,
                ),
                ledger = DebtLedger(
                    DebtHeader(
                        id = debtId,
                        personId = PersonId("person-$suffix"),
                        direction = DebtDirection.RECEIVABLE,
                        originalAmount = Money(10_000L, CurrencyCode.YER),
                        openedAt = openedAt,
                        dueDate = LocalDate.parse("2026-08-14"),
                    ),
                ),
                lifecycleState = DebtLifecycleState.ACTIVE,
                dueReminder = ReminderRecord(
                    id = "reminder-$suffix",
                    debtId = debtId,
                    triggerAt = Instant.parse("2026-08-14T06:00:00Z"),
                    zoneId = ZoneOffset.UTC,
                    status = status,
                    createdAt = openedAt,
                    updatedAt = openedAt,
                ),
            ),
            dueState = DueState.DUE_TODAY,
            daysOverdue = 0,
        )
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
