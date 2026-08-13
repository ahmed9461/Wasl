package com.wasl.app

import com.wasl.app.data.AccountOverview
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
        viewModel.createPersonWithDebt()
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
        viewModel.createPersonWithDebt()
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
        viewModel.createPersonWithDebt()
        advanceUntilIdle()
        viewModel.createPersonWithDebt()
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
        viewModel.createPersonWithDebt()
        advanceUntilIdle()

        val command = assertNotNull(repository.lastCreateCommand)
        assertEquals(LocalDate.parse("2026-08-14"), command.dueDate)
        assertEquals("reminder-1", command.dueReminder?.id)
        assertEquals(Instant.parse("2026-08-14T06:00:00Z"), command.dueReminder?.triggerAt)
        assertEquals(ZoneId.of("Asia/Riyadh"), command.dueReminder?.zoneId)
        assertEquals(listOf("reminder-1"), scheduler.scheduled.map { it.id })
        assertEquals("تم حفظ الحساب وجدولة التذكير.", viewModel.uiState.value.successMessage)
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
        viewModel.createPersonWithDebt()
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
        viewModel.createPersonWithDebt()
        advanceUntilIdle()

        assertNotNull(repository.lastCreateCommand?.dueReminder)
        assertFalse(viewModel.uiState.value.isCreateDialogOpen)
        assertEquals(
            "تم حفظ الحساب والتذكير، وستُعاد محاولة الجدولة تلقائيًا.",
            viewModel.uiState.value.successMessage,
        )
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
) : WaslRepository {
    private val accounts = MutableStateFlow<List<AccountOverview>>(emptyList())
    var lastCreateCommand: CreatePersonWithDebtCommand? = null
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
        return AccountOverview(
            person = PersonRecord(
                id = command.personId,
                displayName = command.personName,
                createdAt = command.createdAt,
                updatedAt = command.createdAt,
            ),
            ledger = DebtLedger(
                DebtHeader(
                    id = command.debtId,
                    personId = command.personId,
                    direction = command.direction,
                    originalAmount = command.originalAmount,
                    openedAt = command.openedAt,
                    dueDate = command.dueDate,
                    description = command.description,
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
            dueReminder = command.dueReminder?.let { reminder ->
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
        )
    }

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        accounts.value.firstOrNull { it.ledger.header.id == debtId }

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger {
        error("Not used in this test.")
    }

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger {
        error("Not used in this test.")
    }
}

private class RecordingReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ReminderRecord>()

    override fun schedule(reminder: ReminderRecord) {
        scheduled += reminder
    }

    override fun requestRecovery() = Unit
}

private class FailingReminderScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderRecord) {
        error("Simulated platform scheduler failure.")
    }

    override fun requestRecovery() = Unit
}
