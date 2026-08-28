package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonContactDetailsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-person-contact-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun contactDetailsPersistNormalizeAndParticipateInReplayIdentity() = runTest {
        val now = Instant.parse("2026-08-28T12:00:00Z")
        val command = CreatePersonWithDebtCommand(
            personId = PersonId("person-contact"),
            debtId = DebtId("debt-contact"),
            personName = "  أحمد محمد  ",
            personPhone = "  +967 777 123 456  ",
            personEmail = "  ahmed@example.com  ",
            personNotes = "  مورد رئيسي  ",
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(150_000L, CurrencyCode.YER),
            openedAt = now,
            createdAt = now,
        )

        val created = repository.createPersonWithDebt(command)
        assertEquals("أحمد محمد", created.person.displayName)
        assertEquals("+967 777 123 456", created.person.phone)
        assertEquals("ahmed@example.com", created.person.email)
        assertEquals("مورد رئيسي", created.person.notes)
        assertNull(created.person.photoUri)

        repository.createPersonWithDebt(command)
        assertEquals(1, database.personDao().count())
        assertEquals(1, database.debtDao().count())

        assertFailsWith<CommandConflictException> {
            repository.createPersonWithDebt(command.copy(personPhone = "+967 700 000 000"))
        }

        repository.createDebtForExistingPerson(
            CreateDebtForExistingPersonCommand(
                personId = command.personId,
                debtId = DebtId("debt-contact-second"),
                direction = DebtDirection.PAYABLE,
                originalAmount = Money(50_000L, CurrencyCode.YER),
                openedAt = now.plusSeconds(60),
                createdAt = now.plusSeconds(60),
            ),
        )

        database.close()
        openDatabase()
        val reopened = requireNotNull(repository.getAccount(command.debtId))
        assertEquals("+967 777 123 456", reopened.person.phone)
        assertEquals("ahmed@example.com", reopened.person.email)
        assertEquals("مورد رئيسي", reopened.person.notes)
        assertEquals(1, database.personDao().count())
        assertEquals(2, database.debtDao().count())
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }
}
