package com.wasl.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
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
        waitForText("ملخص اليوم")

        scrollToText("متأخر 3 أيام")
        composeRule.onNodeWithText("متأخر 3 أيام").assertIsDisplayed()
        scrollToText("التذكير متوقف حتى تسمح بإشعارات وَصل.")
        composeRule.onNodeWithText(
            "التذكير متوقف حتى تسمح بإشعارات وَصل.",
        ).assertIsDisplayed()
        scrollToText("مستحق اليوم")
        composeRule.onNodeWithText("مستحق اليوم").assertIsDisplayed()
        composeRule.onNodeWithText("شخص قادم").assertDoesNotExist()

        scrollToTag("today-open-debt-today")
        composeRule.onNodeWithTag("today-open-debt-today").performClick()
        waitForText("شخص اليوم")
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

        scrollToTag("today-enable-notifications-debt-blocked")
        composeRule.onNodeWithTag("today-enable-notifications-debt-blocked").performClick()
        scrollToTag("today-retry-reminder-debt-failed")
        composeRule.onNodeWithTag("today-retry-reminder-debt-failed").performClick()

        composeRule.runOnIdle {
            assertEquals(1, permissionActions)
            assertEquals(1, retryActions)
        }
    }


    @Test
    fun largeFontStacksDenseTodayRowsAndKeepsReminderActionReachable() {
        val blocked = uiItem("large-font", ReminderStatus.BLOCKED_PERMISSION)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                TodayScreen(
                    state = TodayUiState(
                        today = LocalDate.parse("2026-08-14"),
                        isLoading = false,
                        items = listOf(blocked),
                    ),
                    notificationsAvailable = false,
                    onOpenHome = {},
                    onOpenSearch = {},
                    onOpenAccount = {},
                    onRefreshDate = {},
                    onRetryLoad = {},
                    onResolveNotificationPermission = {},
                    onRetryReminders = {},
                    onNoticeShown = {},
                )
            }
        }

        composeRule.onNodeWithTag("today-summary-metrics-stacked").assertIsDisplayed()
        scrollToTag("today-section-heading-stacked-مستحقة اليوم")
        composeRule.onNodeWithTag("today-section-heading-stacked-مستحقة اليوم").assertIsDisplayed()
        scrollToTag("today-amount-status-debt-large-font-stacked")
        composeRule.onNodeWithTag("today-amount-status-debt-large-font-stacked").assertIsDisplayed()
        scrollToTag("today-actions-stacked-debt-large-font")
        composeRule.onNodeWithTag("today-actions-stacked-debt-large-font").assertIsDisplayed()
        composeRule.onNodeWithTag("today-enable-notifications-debt-large-font").assertIsDisplayed()
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

    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
