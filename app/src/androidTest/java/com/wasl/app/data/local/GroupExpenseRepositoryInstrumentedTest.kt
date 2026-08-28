package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateGroupExpenseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.RecordNotFoundException
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.GroupExpense
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.GroupExpenseShare
import com.wasl.domain.GroupExpenseShareId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseRepositoryInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WaslDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWaslRepository(database)
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun createsOneImmutableGroupOperationBackedByOrdinaryDebts() = runTest {
        seedPerson("person-a", "أحمد", "seed-debt-a")
        seedPerson("person-b", "سارة", "seed-debt-b")
        seedPerson("person-c", "خالد", "seed-debt-c")

        val command = command(
            commandId = "group-command-1",
            groupId = "group-1",
            allocations = listOf(
                Allocation("share-a", "group-debt-a", "person-a", 5_000L),
                Allocation("share-b", "group-debt-b", "person-b", 10_000L),
                Allocation("share-c", "group-debt-c", "person-c", 15_000L),
            ),
        )

        val created = repository.createGroupExpense(command)

        assertEquals(command.expense, created.expense)
        assertEquals(command.commandId, created.commandId)
        assertEquals(created, repository.getGroupExpense(GroupExpenseId("group-1")))
        assertEquals(listOf(created), repository.observeGroupExpenses().first())

        command.expense.shares.forEach { share ->
            val account = assertNotNull(repository.getAccount(share.debtId))
            assertEquals(share.personId, account.person.id)
            assertEquals(DebtDirection.RECEIVABLE, account.ledger.header.direction)
            assertEquals(share.amount, account.ledger.header.originalAmount)
            assertEquals(share.amount, account.ledger.balance)
            assertEquals("عشاء مشترك", account.ledger.header.description)
        }
    }

    @Test
    fun replayIsIdempotentAndConflictingCommandIdIsRejected() = runTest {
        seedPerson("person-a", "أحمد", "seed-debt-a")
        seedPerson("person-b", "سارة", "seed-debt-b")
        val original = command(
            commandId = "group-command-replay",
            groupId = "group-replay",
            allocations = listOf(
                Allocation("share-a", "group-debt-a", "person-a", 12_000L),
                Allocation("share-b", "group-debt-b", "person-b", 18_000L),
            ),
        )

        val first = repository.createGroupExpense(original)
        val replay = repository.createGroupExpense(original)
        assertEquals(first, replay)
        assertEquals(1, repository.observeGroupExpenses().first().size)

        val conflict = command(
            commandId = "group-command-replay",
            groupId = "group-conflict",
            allocations = listOf(
                Allocation("share-x", "group-debt-x", "person-a", 10_000L),
                Allocation("share-y", "group-debt-y", "person-b", 20_000L),
            ),
        )
        assertFailsWith<CommandConflictException> {
            repository.createGroupExpense(conflict)
        }
        assertNull(repository.getGroupExpense(GroupExpenseId("group-conflict")))
    }

    @Test
    fun missingParticipantRollsBackWholeGroupCreation() = runTest {
        seedPerson("person-a", "أحمد", "seed-debt-a")
        val command = command(
            commandId = "group-command-missing",
            groupId = "group-missing",
            allocations = listOf(
                Allocation("share-a", "group-debt-a", "person-a", 15_000L),
                Allocation("share-missing", "group-debt-missing", "person-missing", 15_000L),
            ),
        )

        assertFailsWith<RecordNotFoundException> {
            repository.createGroupExpense(command)
        }
        assertNull(repository.getGroupExpense(GroupExpenseId("group-missing")))
        assertNull(repository.getAccount(DebtId("group-debt-a")))
        assertNull(repository.getAccount(DebtId("group-debt-missing")))
    }

    private suspend fun seedPerson(personId: String, name: String, seedDebtId: String) {
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId(personId),
                debtId = DebtId(seedDebtId),
                personName = name,
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(1_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-01T10:00:00Z"),
                createdAt = Instant.parse("2026-08-01T10:00:00Z"),
                description = "حساب تأسيسي للاختبار",
            ),
        )
    }

    private fun command(
        commandId: String,
        groupId: String,
        allocations: List<Allocation>,
    ): CreateGroupExpenseCommand {
        val shares = allocations.map {
            GroupExpenseShare(
                id = GroupExpenseShareId(it.shareId),
                debtId = DebtId(it.debtId),
                personId = PersonId(it.personId),
                amount = Money(it.amountMinor, CurrencyCode.YER),
            )
        }
        return CreateGroupExpenseCommand(
            commandId = commandId,
            expense = GroupExpense(
                id = GroupExpenseId(groupId),
                direction = DebtDirection.RECEIVABLE,
                totalAmount = Money(30_000L, CurrencyCode.YER),
                occurredAt = Instant.parse("2026-08-28T00:00:00Z"),
                description = "عشاء مشترك",
                notes = "عملية جماعية محفوظة كأصل واحد",
                shares = shares,
            ),
            createdAt = Instant.parse("2026-08-28T00:05:00Z"),
        )
    }

    private data class Allocation(
        val shareId: String,
        val debtId: String,
        val personId: String,
        val amountMinor: Long,
    )
}
