package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createsExactArabicAmountAndClosesDialogAfterPersistence() = runTest {
        val repository = FakeWaslRepository()
        val ids = ArrayDeque(listOf("person-1", "debt-1"))
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("أحمد")
        viewModel.updateAmount("١٠٠٬٠٠٠")
        viewModel.createDebt()
        advanceUntilIdle()

        val command = assertNotNull(repository.lastCreateCommand)
        assertEquals(Money(100_000L, CurrencyCode.YER), command.originalAmount)
        assertEquals("أحمد", command.personName)
        assertFalse(viewModel.uiState.value.isCreateDialogOpen)
        assertEquals("تم حفظ الحساب والدين بنجاح.", viewModel.uiState.value.successMessage)
    }

    @Test
    fun invalidAmountRemainsAUserErrorAndDoesNotCallRepository() = runTest {
        val repository = FakeWaslRepository()
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("شخص")
        viewModel.updateAmount("0")
        viewModel.createDebt()
        advanceUntilIdle()

        assertNull(repository.lastCreateCommand)
        assertEquals(
            "تحقق من المبلغ ودقة العملة المختارة.",
            viewModel.uiState.value.formError,
        )
    }

    @Test
    fun retryAfterUnknownFailureReusesTheSameCreateIdentity() = runTest {
        val repository = FakeWaslRepository(createFailuresRemaining = 1)
        val ids = ArrayDeque(listOf("person-stable", "debt-stable"))
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("أحمد")
        viewModel.updateAmount("1000")
        viewModel.createDebt()
        advanceUntilIdle()
        viewModel.createDebt()
        advanceUntilIdle()

        assertEquals(2, repository.createCommands.size)
        assertEquals(repository.createCommands.first(), repository.createCommands.last())
        assertFalse(viewModel.uiState.value.isCreateDialogOpen)
    }

    @Test
    fun dueDateReminderIsPersistedWithStableCivilTimeThenScheduled() = runTest {
        val repository = FakeWaslRepository()
        val scheduler = RecordingReminderScheduler()
        val ids = ArrayDeque(listOf("person-1", "debt-1", "reminder-1"))
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
            zoneIdProvider = { ZoneId.of("Asia/Riyadh") },
            reminderScheduler = scheduler,
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("أحمد")
        viewModel.updateAmount("1000")
        viewModel.updateDueDate(LocalDate.parse("2026-08-14"))
        viewModel.updateRemindOnDueDate(true)
        viewModel.createDebt()
        advanceUntilIdle()

        val command = assertNotNull(repository.lastCreateCommand)
        assertEquals(LocalDate.parse("2026-08-14"), command.dueDate)
        assertEquals("reminder-1", command.dueReminder?.id)
        assertEquals(Instant.parse("2026-08-14T06:00:00Z"), command.dueReminder?.triggerAt)
        assertEquals(ZoneId.of("Asia/Riyadh"), command.dueReminder?.zoneId)
        assertEquals(listOf("reminder-1"), scheduler.scheduled.map { it.id })
        assertEquals("تم حفظ الحساب وتفعيل المتابعة الذكية.", viewModel.uiState.value.successMessage)
    }

    @Test
    fun pastDueDateIsRejectedBeforePersistence() = runTest {
        val repository = FakeWaslRepository()
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC),
            zoneIdProvider = { ZoneOffset.UTC },
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("أحمد")
        viewModel.updateAmount("1000")
        viewModel.updateDueDate(LocalDate.parse("2026-08-12"))
        viewModel.createDebt()
        advanceUntilIdle()

        assertNull(repository.lastCreateCommand)
        assertEquals("اختر تاريخ استحقاق اليوم أو بعده.", viewModel.uiState.value.formError)
    }

    @Test
    fun platformSchedulingFailureDoesNotUndoPersistedDebtOrReminder() = runTest {
        val repository = FakeWaslRepository()
        val ids = ArrayDeque(listOf("person-1", "debt-1", "reminder-1"))
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
            zoneIdProvider = { ZoneOffset.UTC },
            reminderScheduler = FailingReminderScheduler(),
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonName("أحمد")
        viewModel.updateAmount("1000")
        viewModel.updateDueDate(LocalDate.parse("2026-08-14"))
        viewModel.updateRemindOnDueDate(true)
        viewModel.createDebt()
        advanceUntilIdle()

        assertNotNull(repository.lastCreateCommand?.dueReminder)
        assertFalse(viewModel.uiState.value.isCreateDialogOpen)
        assertEquals(
            "تم حفظ الحساب والمتابعة، وستُعاد محاولة الجدولة تلقائيًا.",
            viewModel.uiState.value.successMessage,
        )
    }

    @Test
    fun createsIndependentDebtForSelectedExistingPersonId() = runTest {
        val person = person("person-existing", "أحمد")
        val repository = FakeWaslRepository(initialPeople = listOf(person))
        val ids = ArrayDeque(listOf("debt-existing"))
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
        )
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonMode(DebtPersonMode.EXISTING)
        viewModel.selectExistingPerson(person.id)
        viewModel.updateAmount("50000")
        viewModel.updateDescription("دين مستقل ثانٍ")
        viewModel.createDebt()
        advanceUntilIdle()

        val command = assertNotNull(repository.lastExistingPersonCommand)
        assertEquals(person.id, command.personId)
        assertEquals(DebtId("debt-existing"), command.debtId)
        assertEquals("دين مستقل ثانٍ", command.description)
        assertNull(repository.lastCreateCommand)
        assertEquals(
            "تم حفظ دين جديد للشخص أحمد بنجاح.",
            viewModel.uiState.value.successMessage,
        )
    }

    @Test
    fun existingPersonModeRequiresAnExplicitSelection() = runTest {
        val repository = FakeWaslRepository(initialPeople = listOf(person("person-1", "أحمد")))
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.openCreateDialog()
        viewModel.updatePersonMode(DebtPersonMode.EXISTING)
        viewModel.updateAmount("1000")
        viewModel.createDebt()
        advanceUntilIdle()

        assertNull(repository.lastExistingPersonCommand)
        assertEquals("اختر شخصًا محفوظًا.", viewModel.uiState.value.formError)
    }

    @Test
    fun peoplePickerUsesAVisibleLimitAndFiltersByName() = runTest {
        val people = (0..20).map { index -> person("person-$index", "شخص $index") }
        val repository = FakeWaslRepository(initialPeople = people)
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.selectablePeople.size)
        assertEquals(true, viewModel.uiState.value.hasMorePeople)

        viewModel.updatePeopleQuery("شخص 20")
        advanceUntilIdle()

        assertEquals(listOf("person-20"), viewModel.uiState.value.selectablePeople.map { it.id.value })
        assertFalse(viewModel.uiState.value.hasMorePeople)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeWaslRepository(
    private var createFailuresRemaining: Int = 0,
    initialPeople: List<PersonRecord> = emptyList(),
) : WaslRepository {
    private val accounts = MutableStateFlow<List<AccountOverview>>(emptyList())
    private val people = MutableStateFlow(initialPeople)
    var lastCreateCommand: CreatePersonWithDebtCommand? = null
    var lastExistingPersonCommand: CreateDebtForExistingPersonCommand? = null
    val createCommands = mutableListOf<CreatePersonWithDebtCommand>()

    override fun observeAccounts(): Flow<List<AccountOverview>> = accounts

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> =
        accounts.map { values ->
            values.filter { account ->
                account.ledger.header.dueDate?.let { !it.isAfter(onOrBefore) } == true &&
                    !account.ledger.balance.isZero
            }
        }

    override fun observeSearchAccounts(
        query: String,
        limit: Int,
    ): Flow<List<AccountOverview>> = accounts.map { values ->
        values.filter { account ->
            account.person.displayName.contains(query, ignoreCase = true) ||
                account.ledger.header.description?.contains(query, ignoreCase = true) == true
        }.take(limit)
    }

    override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> =
        people.map { values ->
            values.filter { it.displayName.contains(query.trim(), ignoreCase = true) }
                .take(limit)
        }

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =
        accounts.map { values ->
            values.firstOrNull { it.ledger.header.id == debtId }
        }

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview {
        lastCreateCommand = command
        createCommands += command
        if (createFailuresRemaining > 0) {
            createFailuresRemaining -= 1
            error("Simulated unknown persistence result.")
        }
        val person = PersonRecord(
            id = command.personId,
            displayName = command.personName,
            createdAt = command.createdAt,
            updatedAt = command.createdAt,
        )
        if (people.value.none { it.id == person.id }) people.value += person
        return account(
            person = person,
            debtId = command.debtId,
            direction = command.direction,
            amount = command.originalAmount,
            openedAt = command.openedAt,
            dueDate = command.dueDate,
            description = command.description,
            reminder = command.dueReminder?.let { reminder ->
                ReminderRecord(
                    id = reminder.id,
                    debtId = command.debtId,
                    triggerAt = reminder.triggerAt,
                    zoneId = reminder.zoneId,
                    status = ReminderStatus.SCHEDULED,
                    createdAt = command.createdAt,
                    updatedAt = command.createdAt,
                )
            },
        ).also { created -> accounts.value += created }
    }

    override suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview {
        lastExistingPersonCommand = command
        val person = people.value.first { it.id == command.personId }
        return account(
            person = person,
            debtId = command.debtId,
            direction = command.direction,
            amount = command.originalAmount,
            openedAt = command.openedAt,
            dueDate = command.dueDate,
            description = command.description,
            reminder = command.dueReminder?.let { reminder ->
                ReminderRecord(
                    id = reminder.id,
                    debtId = command.debtId,
                    triggerAt = reminder.triggerAt,
                    zoneId = reminder.zoneId,
                    status = ReminderStatus.SCHEDULED,
                    createdAt = command.createdAt,
                    updatedAt = command.createdAt,
                )
            },
        ).also { created -> accounts.value += created }
    }

    private fun account(
        person: PersonRecord,
        debtId: DebtId,
        direction: com.wasl.domain.DebtDirection,
        amount: Money,
        openedAt: Instant,
        dueDate: LocalDate?,
        description: String?,
        reminder: ReminderRecord?,
    ) = AccountOverview(
            person = person,
            ledger = DebtLedger(
                DebtHeader(
                    id = debtId,
                    personId = person.id,
                    direction = direction,
                    originalAmount = amount,
                    openedAt = openedAt,
                    dueDate = dueDate,
                    description = description,
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
            dueReminder = reminder,
        )

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        accounts.value.firstOrNull { it.ledger.header.id == debtId }

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger {
        error("Not used in this test.")
    }

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger {
        error("Not used in this test.")
    }

    override suspend fun updateDueSchedule(
        command: com.wasl.app.data.UpdateDueScheduleCommand,
    ): AccountOverview = error("Not used in this test.")
}

private fun person(id: String, name: String): PersonRecord {
    val now = Instant.parse("2026-08-13T00:00:00Z")
    return PersonRecord(
        id = com.wasl.domain.PersonId(id),
        displayName = name,
        createdAt = now,
        updatedAt = now,
    )
}

private class RecordingReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ReminderRecord>()

    override fun schedule(reminder: ReminderRecord) {
        scheduled += reminder
    }

    override fun cancel(reminderId: String) = Unit

    override fun requestRecovery() = Unit
}

private class FailingReminderScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderRecord) {
        error("Simulated platform scheduler failure.")
    }

    override fun cancel(reminderId: String) = Unit

    override fun requestRecovery() = Unit
}
