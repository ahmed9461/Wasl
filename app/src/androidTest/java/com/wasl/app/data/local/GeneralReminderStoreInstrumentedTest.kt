package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.UpsertGeneralReminderCommand
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneralReminderStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomGeneralReminderStore

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WaslDatabase::class.java).build()
        repository = RoomWaslRepository(database)
        store = RoomGeneralReminderStore(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun generalReminderPersistsUpdatesAndKeepsOneStableIdentityPerDebt() = runTest {
        val debtId = DebtId("general-reminder-debt")
        val createdAt = Instant.parse("2026-08-25T10:00:00Z")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("general-reminder-person"),
                debtId = debtId,
                personName = "عميل التذكير العام",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(50_000L, CurrencyCode.YER),
                openedAt = createdAt,
                createdAt = createdAt,
                description = "اختبار التذكير العام",
            ),
        )

        val zone = ZoneId.of("Asia/Aden")
        val first = store.upsertReminder(
            UpsertGeneralReminderCommand(
                reminderId = "general-reminder-1",
                debtId = debtId,
                triggerAt = Instant.parse("2026-08-26T06:00:00Z"),
                zoneId = zone,
                repeatRule = GeneralReminderRepeatRule(GeneralReminderFrequency.WEEKLY),
                updatedAt = Instant.parse("2026-08-25T10:05:00Z"),
            ),
        )
        assertEquals(ReminderStatus.SCHEDULED, first.status)
        assertEquals(GeneralReminderFrequency.WEEKLY, first.repeatRule?.frequency)
        assertEquals(1, database.reminderDao().count())

        val updated = store.upsertReminder(
            UpsertGeneralReminderCommand(
                reminderId = first.id,
                debtId = debtId,
                triggerAt = Instant.parse("2026-08-27T06:00:00Z"),
                zoneId = zone,
                repeatRule = GeneralReminderRepeatRule.forTrigger(
                    frequency = GeneralReminderFrequency.MONTHLY,
                    triggerAt = Instant.parse("2026-08-27T06:00:00Z"),
                    zoneId = zone,
                ),
                updatedAt = Instant.parse("2026-08-25T10:10:00Z"),
            ),
        )
        assertEquals(first.id, updated.id)
        assertEquals(27, updated.repeatRule?.monthlyDayOfMonth)
        assertEquals(1, database.reminderDao().count())
        assertEquals(updated, assertNotNull(store.getReminderForDebt(debtId)))

        assertFailsWith<CommandConflictException> {
            store.upsertReminder(
                UpsertGeneralReminderCommand(
                    reminderId = "another-general-reminder-id",
                    debtId = debtId,
                    triggerAt = Instant.parse("2026-08-28T06:00:00Z"),
                    zoneId = zone,
                    repeatRule = null,
                    updatedAt = Instant.parse("2026-08-25T10:15:00Z"),
                ),
            )
        }
        assertEquals(1, database.reminderDao().count())

        val cancelled = store.cancelReminder(
            reminderId = updated.id,
            updatedAt = Instant.parse("2026-08-25T10:20:00Z"),
        )
        assertEquals(ReminderStatus.CANCELLED, cancelled.status)
        assertEquals(0, store.getRecoverableReminders().size)
    }
}
