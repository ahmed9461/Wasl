package com.wasl.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrongAlarmStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-strong-alarm-${UUID.randomUUID()}.db"
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
    fun smartReminderAndStrongAlarmCoexistAndAreCancelledWithDueDate() = runBlocking {
        val zone = ZoneId.of("Asia/Aden")
        val createdAt = Instant.parse("2026-08-25T06:00:00Z")
        val account = repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("person-strong"),
                debtId = DebtId("debt-strong"),
                personName = "أحمد",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(10_000L, CurrencyCode.YER),
                openedAt = createdAt,
                createdAt = createdAt,
                dueDate = LocalDate.parse("2026-08-30"),
                dueReminder = DueReminderRequest(
                    id = "due-strong-test",
                    triggerAt = Instant.parse("2026-08-30T06:00:00Z"),
                    zoneId = zone,
                ),
                strongAlarm = StrongAlarmRequest(
                    id = "alarm-strong-test",
                    triggerAt = Instant.parse("2026-08-30T06:00:00Z"),
                    zoneId = zone,
                ),
            ),
        )
        assertNotNull(account.dueReminder)
        assertNotNull(account.strongAlarm)
        assertEquals(ReminderType.DUE_DATE, account.dueReminder?.type)
        assertEquals(ReminderType.STRONG_ALARM, account.strongAlarm?.type)

        val updated = repository.updateDueSchedule(
            UpdateDueScheduleCommand(
                commandId = "remove-due-and-alarm",
                auditEventId = "audit-remove-due-and-alarm",
                debtId = DebtId("debt-strong"),
                dueDate = null,
                dueReminder = null,
                strongAlarm = null,
                updatedAt = createdAt.plusSeconds(60),
            ),
        )
        assertEquals(ReminderStatus.CANCELLED, updated.dueReminder?.status)
        assertEquals(ReminderStatus.CANCELLED, updated.strongAlarm?.status)
        assertEquals(null, updated.ledger.header.dueDate)
        assertEquals(null, updated.dueScheduleAuditEvents.single().after.strongAlarm)
    }
}
