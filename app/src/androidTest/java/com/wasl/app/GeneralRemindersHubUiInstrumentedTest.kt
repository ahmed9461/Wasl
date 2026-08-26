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
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.local.RoomGeneralReminderStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.reminder.GeneralReminderScheduler
import com.wasl.app.reminder.GeneralReminderService
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneralRemindersHubUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomGeneralReminderStore
    private lateinit var scheduler: RecordingGeneralReminderScheduler
    private lateinit var service: GeneralReminderService
    private val debtId = DebtId("general-reminder-ui-debt")

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-general-reminder-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomGeneralReminderStore(database)
        scheduler = RecordingGeneralReminderScheduler()
        service = GeneralReminderService(store, scheduler)
        runBlocking {
            val createdAt = Instant.parse("2026-08-25T10:00:00Z")
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("general-reminder-ui-person"),
                    debtId = debtId,
                    personName = "عميل التذكيرات",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(75_000L, CurrencyCode.YER),
                    openedAt = createdAt,
                    createdAt = createdAt,
                    description = "متابعة أسبوعية",
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
    fun createsWeeklyReminderThenCancelsItFromHub() {
        composeRule.setContent {
            WaslTheme {
                GeneralRemindersHubRoute(
                    repository = repository,
                    store = store,
                    service = service,
                    onBack = {},
                )
            }
        }

        waitForTag("edit-general-reminder")
        composeRule.onNodeWithText("عميل التذكيرات").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-general-reminder").performClick()
        waitForTag("general-reminder-frequency-weekly")
        composeRule.onNodeWithTag("general-reminder-frequency-weekly").performClick()
        composeRule.onNodeWithTag("save-general-reminder").performClick()

        waitForText("أسبوعي")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                store.getReminderForDebt(debtId)?.let { reminder ->
                    reminder.status == ReminderStatus.SCHEDULED &&
                        reminder.repeatRule?.frequency == GeneralReminderFrequency.WEEKLY &&
                        scheduler.replaced.any { scheduled -> scheduled.id == reminder.id }
                } == true
            }
        }
        val stored = runBlocking { assertNotNull(store.getReminderForDebt(debtId)) }
        assertEquals(GeneralReminderFrequency.WEEKLY, stored.repeatRule?.frequency)
        assertEquals(ReminderStatus.SCHEDULED, stored.status)
        assertEquals(stored.id, scheduler.replaced.single().id)

        composeRule.onNodeWithTag("edit-general-reminder").performClick()
        waitForTag("cancel-general-reminder")
        composeRule.onNodeWithTag("cancel-general-reminder")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                store.getReminderForDebt(debtId)?.status == ReminderStatus.CANCELLED &&
                    scheduler.cancelled.contains(stored.id)
            }
        }
        assertEquals(stored.id, scheduler.cancelled.single())
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

    private class RecordingGeneralReminderScheduler : GeneralReminderScheduler {
        val replaced = CopyOnWriteArrayList<GeneralReminderRecord>()
        val cancelled = CopyOnWriteArrayList<String>()

        override fun replace(reminder: GeneralReminderRecord) {
            replaced += reminder
        }

        override fun scheduleNext(reminder: GeneralReminderRecord) = Unit

        override fun cancel(reminderId: String) {
            cancelled += reminderId
        }

        override fun requestRecovery() = Unit
    }
}
