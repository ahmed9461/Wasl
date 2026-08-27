package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ResolvePaymentPromiseCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertIs
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalDebtConfirmationServiceTest {
    private val now = Instant.parse("2026-08-27T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `reuses one exact existing person and creates promise only after confirmation`() = runTest {
        val existing = person("person-existing", "عبدالله")
        val repository = FakeRepository(listOf(existing))
        val promises = FakePromiseStore()
        val service = service(repository, promises)

        val result = service.confirmAndSave(validDraft(promisedDate = LocalDate.of(2026, 8, 30)))

        val saved = assertIs<NaturalDebtConfirmationResult.Saved>(result)
        assertEquals(existing.id, repository.existingCommand?.personId)
        assertEquals(null, repository.newPersonCommand)
        assertTrue(saved.promiseCreated)
        assertEquals(repository.existingCommand?.debtId, promises.created?.debtId)
    }

    @Test
    fun `does not save when more than one exact person matches until user chooses`() = runTest {
        val first = person("person-a", "محمد")
        val second = person("person-b", "محمد")
        val repository = FakeRepository(listOf(first, second))
        val promises = FakePromiseStore()
        val service = service(repository, promises)
        val draft = validDraft(personName = "محمد")

        val ambiguous = service.confirmAndSave(draft)

        val result = assertIs<NaturalDebtConfirmationResult.AmbiguousPerson>(ambiguous)
        assertEquals(listOf(first.id, second.id), result.matchingPeople.map { it.id })
        assertEquals(null, repository.existingCommand)
        assertEquals(null, repository.newPersonCommand)
        assertEquals(null, promises.created)

        val selected = service.confirmAndSave(draft, selectedPersonId = second.id)
        assertIs<NaturalDebtConfirmationResult.Saved>(selected)
        assertEquals(second.id, repository.existingCommand?.personId)
    }

    @Test
    fun `creates a new person only when no exact person exists`() = runTest {
        val repository = FakeRepository(emptyList())
        val promises = FakePromiseStore()
        val service = service(repository, promises)

        val result = service.confirmAndSave(validDraft(personName = "خالد"))

        assertIs<NaturalDebtConfirmationResult.Saved>(result)
        assertEquals("خالد", repository.newPersonCommand?.personName)
        assertEquals(null, repository.existingCommand)
        assertFalse((result as NaturalDebtConfirmationResult.Saved).promiseCreated)
    }

    private fun service(
        repository: FakeRepository,
        promises: FakePromiseStore,
    ) = NaturalDebtConfirmationService(
        repository = repository,
        paymentPromiseStore = promises,
        clock = clock,
        zoneIdProvider = { ZoneOffset.UTC },
        newId = sequenceOf("debt-id", "person-id", "promise-command", "promise-id")
            .iterator()::next,
    )

    private fun validDraft(
        personName: String = "عبدالله",
        promisedDate: LocalDate? = null,
    ) = NaturalEntryDraft(
        sourceText = "سلفت $personName 5000 ريال سعودي اليوم",
        kind = NaturalEntryKind.DEBT,
        personName = personName,
        direction = DebtDirection.RECEIVABLE,
        amountMinorUnits = 500_000L,
        currency = CurrencyCode.SAR,
        entryDate = LocalDate.of(2026, 8, 27),
        promisedDate = promisedDate,
    )

    private fun person(id: String, name: String) = PersonRecord(
        id = PersonId(id),
        displayName = name,
        createdAt = now,
        updatedAt = now,
    )

    private class FakeRepository(
        private val people: List<PersonRecord>,
    ) : WaslRepository {
        var newPersonCommand: CreatePersonWithDebtCommand? = null
        var existingCommand: CreateDebtForExistingPersonCommand? = null

        override fun observeAccounts(): Flow<List<AccountOverview>> = flowOf(emptyList())
        override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> = flowOf(emptyList())
        override fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>> = flowOf(emptyList())
        override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> = flowOf(people)
        override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> = flowOf(null)
        override suspend fun getAccount(debtId: DebtId): AccountOverview? = null

        override suspend fun createPersonWithDebt(command: CreatePersonWithDebtCommand): AccountOverview {
            newPersonCommand = command
            val createdPerson = PersonRecord(
                id = command.personId,
                displayName = command.personName,
                createdAt = command.createdAt,
                updatedAt = command.createdAt,
            )
            return account(
                person = createdPerson,
                debtId = command.debtId,
                direction = command.direction,
                amount = command.originalAmount,
                openedAt = command.openedAt,
            )
        }

        override suspend fun createDebtForExistingPerson(
            command: CreateDebtForExistingPersonCommand,
        ): AccountOverview {
            existingCommand = command
            val selected = people.first { it.id == command.personId }
            return account(
                person = selected,
                debtId = command.debtId,
                direction = command.direction,
                amount = command.originalAmount,
                openedAt = command.openedAt,
            )
        }

        override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger = error("unused")
        override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger = error("unused")
        override suspend fun updateDueSchedule(command: UpdateDueScheduleCommand): AccountOverview = error("unused")

        private fun account(
            person: PersonRecord,
            debtId: DebtId,
            direction: DebtDirection,
            amount: Money,
            openedAt: Instant,
        ) = AccountOverview(
            person = person,
            ledger = DebtLedger(
                DebtHeader(
                    id = debtId,
                    personId = person.id,
                    direction = direction,
                    originalAmount = amount,
                    openedAt = openedAt,
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }

    private class FakePromiseStore : PaymentPromiseStore {
        var created: CreatePaymentPromiseCommand? = null

        override fun observePaymentPromises(debtId: DebtId): Flow<List<PaymentPromiseRecord>> = flowOf(emptyList())
        override fun observePendingPaymentPromises(onOrBefore: LocalDate): Flow<List<PaymentPromiseRecord>> = flowOf(emptyList())

        override suspend fun createPaymentPromise(
            command: CreatePaymentPromiseCommand,
        ): PaymentPromiseRecord {
            created = command
            return PaymentPromiseRecord(
                id = command.promiseId,
                debtId = command.debtId,
                promisedDate = command.promisedDate,
                status = PaymentPromiseStatus.PENDING,
                note = command.note,
                createdAt = command.createdAt,
                updatedAt = command.createdAt,
            )
        }

        override suspend fun resolvePaymentPromise(
            command: ResolvePaymentPromiseCommand,
        ): PaymentPromiseRecord = error("unused")
    }
}
