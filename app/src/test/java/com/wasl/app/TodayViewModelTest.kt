package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun usesDeviceZoneAndSeparatesOverdueFromDueToday() = runTest {
        val repository = TodayFakeRepository(
            listOf(
                account("overdue", LocalDate.parse("2026-08-12")),
                account("today", LocalDate.parse("2026-08-14")),
                account("upcoming", LocalDate.parse("2026-08-15")),
                settledAccount("settled", LocalDate.parse("2026-08-11")),
            ),
        )
        val viewModel = TodayViewModel(
            repository = repository,
            clock = Clock.fixed(
                Instant.parse("2026-08-13T22:30:00Z"),
                ZoneOffset.UTC,
            ),
            zoneIdProvider = { ZoneId.of("Asia/Aden") },
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.parse("2026-08-14"), state.today)
        assertEquals(listOf("overdue"), state.overdueItems.map { it.account.ledger.header.id.value })
        assertEquals(2L, state.overdueItems.single().daysOverdue)
        assertEquals(listOf("today"), state.dueTodayItems.map { it.account.ledger.header.id.value })
        assertFalse(state.isLoading)
        assertEquals(listOf(LocalDate.parse("2026-08-14")), repository.observedDates)
    }

    @Test
    fun resumeAfterLocalMidnightRequeriesWithTheNewDate() = runTest {
        val clock = MutableTestClock(Instant.parse("2026-08-13T20:30:00Z"))
        val repository = TodayFakeRepository(
            listOf(account("tomorrow", LocalDate.parse("2026-08-14"))),
        )
        val viewModel = TodayViewModel(
            repository = repository,
            clock = clock,
            zoneIdProvider = { ZoneId.of("Asia/Aden") },
        )
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-08-13"), viewModel.uiState.value.today)
        assertEquals(0, viewModel.uiState.value.items.size)

        clock.current = Instant.parse("2026-08-13T22:30:00Z")
        viewModel.refreshForCurrentDate()
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-08-14"), viewModel.uiState.value.today)
        assertEquals("tomorrow", viewModel.uiState.value.dueTodayItems.single().account.ledger.header.id.value)
        assertEquals(
            listOf(LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-14")),
            repository.observedDates,
        )
    }

    @Test
    fun loadFailureShowsRetryWithoutDiscardingTheSelectedDate() = runTest {
        val repository = TodayFakeRepository(
            initialAccounts = listOf(account("due", LocalDate.parse("2026-08-14"))),
            failNextObservation = true,
        )
        val viewModel = TodayViewModel(
            repository = repository,
            clock = Clock.fixed(
                Instant.parse("2026-08-14T08:00:00Z"),
                ZoneOffset.UTC,
            ),
            zoneIdProvider = { ZoneOffset.UTC },
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.loadError)
        assertEquals(LocalDate.parse("2026-08-14"), viewModel.uiState.value.today)

        viewModel.retryLoad()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.loadError)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(2, repository.observedDates.size)
    }

    @Test
    fun reminderRetryReportsWhetherRecoveryWasEnqueued() = runTest {
        val scheduler = RecordingTodayReminderScheduler()
        val viewModel = TodayViewModel(
            repository = TodayFakeRepository(emptyList()),
            reminderScheduler = scheduler,
        )
        advanceUntilIdle()

        viewModel.retryReminderRecovery()
        assertEquals(1, scheduler.recoveryRequests)
        assertEquals(
            TodayNotice.REMINDER_RECOVERY_REQUESTED,
            viewModel.uiState.value.notice,
        )

        viewModel.clearNotice()
        scheduler.fail = true
        viewModel.retryReminderRecovery()
        assertEquals(2, scheduler.recoveryRequests)
        assertEquals(TodayNotice.REMINDER_RECOVERY_FAILED, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isRequestingReminderRecovery)
    }
}

private class TodayFakeRepository(
    initialAccounts: List<AccountOverview>,
    var failNextObservation: Boolean = false,
) : WaslRepository {
    private val accounts = MutableStateFlow(initialAccounts)
    val observedDates = mutableListOf<LocalDate>()

    override fun observeAccounts(): Flow<List<AccountOverview>> = accounts

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> {
        observedDates += onOrBefore
        if (failNextObservation) {
            failNextObservation = false
            return flow { error("Simulated read failure.") }
        }
        return accounts.map { values ->
            values.filter { account ->
                account.ledger.header.dueDate?.let { !it.isAfter(onOrBefore) } == true &&
                    !account.ledger.balance.isZero
            }
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
        accounts.map { values ->
            values.map { it.person }
                .distinctBy { it.id }
                .filter { it.displayName.contains(query, ignoreCase = true) }
                .take(limit)
        }

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =
        accounts.map { values -> values.firstOrNull { it.ledger.header.id == debtId } }

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        accounts.value.firstOrNull { it.ledger.header.id == debtId }

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger =
        error("Not used in this test.")

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger =
        error("Not used in this test.")
}

private class RecordingTodayReminderScheduler : ReminderScheduler {
    var recoveryRequests = 0
    var fail = false

    override fun schedule(reminder: com.wasl.app.data.ReminderRecord) = Unit

    override fun requestRecovery() {
        recoveryRequests += 1
        if (fail) error("Simulated scheduler failure.")
    }
}

private class MutableTestClock(
    var current: Instant,
    private val clockZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = clockZone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)

    override fun instant(): Instant = current
}

private fun account(id: String, dueDate: LocalDate): AccountOverview {
    val openedAt = Instant.parse("2026-08-01T00:00:00Z")
    return AccountOverview(
        person = PersonRecord(
            id = PersonId("person-$id"),
            displayName = "شخص $id",
            createdAt = openedAt,
            updatedAt = openedAt,
        ),
        ledger = DebtLedger(
            DebtHeader(
                id = DebtId(id),
                personId = PersonId("person-$id"),
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(10_000L, CurrencyCode.YER),
                openedAt = openedAt,
                dueDate = dueDate,
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
    )
}

private fun settledAccount(id: String, dueDate: LocalDate): AccountOverview {
    val base = account(id, dueDate)
    return base.copy(
        ledger = base.ledger.recordPayment(
            id = LedgerEntryId("payment-$id"),
            amount = base.ledger.balance,
            paidAt = Instant.parse("2026-08-12T00:00:00Z"),
        ),
        closedAt = Instant.parse("2026-08-12T00:00:00Z"),
    )
}
