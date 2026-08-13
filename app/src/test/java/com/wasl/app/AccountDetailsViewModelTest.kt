package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.WaslRepository
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
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun recordsReviewedArabicPartialPaymentAndKeepsOriginalAmount() = runTest {
        val repository = PaymentFakeRepository(baseAccount())
        val ids = ArrayDeque(listOf("payment-command", "payment-entry"))
        val viewModel = detailsViewModel(repository, ids)
        advanceUntilIdle()

        viewModel.openPaymentDialog()
        viewModel.updatePaymentAmount("٢٠٬٠٠٠")
        viewModel.updatePaymentNote("دفعة أولى")
        viewModel.reviewPayment()

        assertEquals(
            PaymentReview(
                amount = Money(20_000L, CurrencyCode.YER),
                remainingAfter = Money(80_000L, CurrencyCode.YER),
            ),
            viewModel.uiState.value.paymentReview,
        )

        viewModel.confirmPayment()
        advanceUntilIdle()

        val command = assertNotNull(repository.paymentCalls.singleOrNull())
        assertEquals("payment-command", command.commandId)
        assertEquals(LedgerEntryId("payment-entry"), command.entryId)
        assertEquals("دفعة أولى", command.note)
        assertEquals(Money(100_000L, CurrencyCode.YER), repository.account.ledger.header.originalAmount)
        assertEquals(Money(80_000L, CurrencyCode.YER), repository.account.ledger.balance)
        assertFalse(viewModel.uiState.value.isPaymentDialogOpen)
        assertIs<AccountOperationNotice.PaymentRecordedNotice>(viewModel.uiState.value.notice)
    }

    @Test
    fun overpaymentIsCorrectableAndNeverCallsRepository() = runTest {
        val repository = PaymentFakeRepository(baseAccount())
        val viewModel = detailsViewModel(repository, ArrayDeque<String>())
        advanceUntilIdle()

        viewModel.openPaymentDialog()
        viewModel.updatePaymentAmount("100001")
        viewModel.reviewPayment()

        assertTrue(viewModel.uiState.value.paymentError!!.contains("أكبر من المتبقي"))
        assertEquals("100001", viewModel.uiState.value.paymentForm.amount)
        assertNull(viewModel.uiState.value.paymentReview)
        assertTrue(repository.paymentCalls.isEmpty())
    }

    @Test
    fun retryAfterUnknownPaymentResultReusesCommandEvenAfterBalanceChanges() = runTest {
        val repository = PaymentFakeRepository(
            initialAccount = baseAccount(),
            paymentFailuresAfterCommit = 1,
        )
        val ids = ArrayDeque(listOf("stable-command", "stable-entry"))
        val viewModel = detailsViewModel(repository, ids)
        advanceUntilIdle()

        viewModel.openPaymentDialog()
        viewModel.updatePaymentAmount("20000")
        viewModel.reviewPayment()
        viewModel.confirmPayment()
        advanceUntilIdle()

        assertEquals(Money(80_000L, CurrencyCode.YER), repository.account.ledger.balance)
        assertNotNull(viewModel.uiState.value.paymentError)
        assertTrue(viewModel.uiState.value.isPaymentDialogOpen)

        viewModel.confirmPayment()
        advanceUntilIdle()

        assertEquals(2, repository.paymentCalls.size)
        assertEquals(repository.paymentCalls.first(), repository.paymentCalls.last())
        assertEquals(1, repository.account.ledger.entries.size)
        assertFalse(viewModel.uiState.value.isPaymentDialogOpen)
    }

    @Test
    fun reversalRequiresReasonAndAppendsHistoryInsteadOfDeletingPayment() = runTest {
        val paymentId = LedgerEntryId("existing-payment")
        val repository = PaymentFakeRepository(accountWithPayment(paymentId))
        val ids = ArrayDeque(listOf("reverse-command", "reverse-entry"))
        val viewModel = detailsViewModel(repository, ids)
        advanceUntilIdle()

        viewModel.openReversalDialog(paymentId)
        viewModel.confirmReversal()
        assertTrue(viewModel.uiState.value.reversalError!!.contains("سبب"))
        assertTrue(repository.reversalCalls.isEmpty())

        viewModel.updateReversalReason("تم تسجيلها بالخطأ")
        viewModel.confirmReversal()
        advanceUntilIdle()

        assertEquals(2, repository.account.ledger.entries.size)
        assertEquals(Money(100_000L, CurrencyCode.YER), repository.account.ledger.balance)
        assertEquals(setOf(paymentId), repository.account.ledger.reversedPaymentIds)
        assertIs<AccountOperationNotice.PaymentReversedNotice>(viewModel.uiState.value.notice)
    }

    @Test
    fun retryAfterUnknownReversalResultReusesTheSameCommand() = runTest {
        val paymentId = LedgerEntryId("existing-payment")
        val repository = PaymentFakeRepository(
            initialAccount = accountWithPayment(paymentId),
            reversalFailuresAfterCommit = 1,
        )
        val ids = ArrayDeque(listOf("stable-reverse-command", "stable-reverse-entry"))
        val viewModel = detailsViewModel(repository, ids)
        advanceUntilIdle()

        viewModel.openReversalDialog(paymentId)
        viewModel.updateReversalReason("سبب ثابت")
        viewModel.confirmReversal()
        advanceUntilIdle()

        assertEquals(setOf(paymentId), repository.account.ledger.reversedPaymentIds)
        assertNotNull(viewModel.uiState.value.reversalError)

        viewModel.confirmReversal()
        advanceUntilIdle()

        assertEquals(2, repository.reversalCalls.size)
        assertEquals(repository.reversalCalls.first(), repository.reversalCalls.last())
        assertEquals(2, repository.account.ledger.entries.size)
        assertNull(viewModel.uiState.value.reversalPaymentId)
    }

    private fun detailsViewModel(
        repository: PaymentFakeRepository,
        ids: ArrayDeque<String>,
    ) = AccountDetailsViewModel(
        repository = repository,
        debtId = DebtId("debt-1"),
        clock = Clock.fixed(Instant.parse("2026-08-13T00:05:00Z"), ZoneOffset.UTC),
        idFactory = { ids.removeFirst() },
    )

    private fun baseAccount(): AccountOverview {
        val openedAt = Instant.parse("2026-08-13T00:00:00Z")
        return AccountOverview(
            person = PersonRecord(
                id = PersonId("person-1"),
                displayName = "أحمد",
                createdAt = openedAt,
                updatedAt = openedAt,
            ),
            ledger = DebtLedger(
                DebtHeader(
                    id = DebtId("debt-1"),
                    personId = PersonId("person-1"),
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = openedAt,
                    description = "دين تجريبي",
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
        )
    }

    private fun accountWithPayment(paymentId: LedgerEntryId): AccountOverview {
        val account = baseAccount()
        return account.copy(
            ledger = account.ledger.recordPayment(
                id = paymentId,
                amount = Money(20_000L, CurrencyCode.YER),
                paidAt = Instant.parse("2026-08-13T00:01:00Z"),
            ),
        )
    }
}

private class PaymentFakeRepository(
    initialAccount: AccountOverview,
    private var paymentFailuresAfterCommit: Int = 0,
    private var reversalFailuresAfterCommit: Int = 0,
) : WaslRepository {
    private val accountState = MutableStateFlow(initialAccount)
    private val paymentCommandsById = mutableMapOf<String, RecordPaymentCommand>()
    private val reversalCommandsById = mutableMapOf<String, ReversePaymentCommand>()

    val paymentCalls = mutableListOf<RecordPaymentCommand>()
    val reversalCalls = mutableListOf<ReversePaymentCommand>()
    val account: AccountOverview
        get() = accountState.value

    override fun observeAccounts(): Flow<List<AccountOverview>> = accountState.map { listOf(it) }

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> =
        accountState.map { account ->
            listOfNotNull(
                account.takeIf {
                    it.ledger.header.dueDate?.let { dueDate ->
                        !dueDate.isAfter(onOrBefore)
                    } == true && !it.ledger.balance.isZero
                },
            )
        }

    override fun observeSearchAccounts(
        query: String,
        limit: Int,
    ): Flow<List<AccountOverview>> = accountState.map { account ->
        listOfNotNull(
            account.takeIf {
                it.person.displayName.contains(query, ignoreCase = true) ||
                    it.ledger.header.description?.contains(query, ignoreCase = true) == true
            },
        ).take(limit)
    }

    override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> =
        accountState.map { account ->
            listOf(account.person)
                .filter { it.displayName.contains(query, ignoreCase = true) }
                .take(limit)
        }

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> = accountState.map {
        it.takeIf { account -> account.ledger.header.id == debtId }
    }

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        account.takeIf { it.ledger.header.id == debtId }

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger {
        paymentCalls += command
        paymentCommandsById[command.commandId]?.let { existing ->
            if (existing != command) throw CommandConflictException("Conflicting payment command.")
            return account.ledger
        }

        val updated = account.ledger.recordPayment(
            id = command.entryId,
            amount = command.amount,
            paidAt = command.paidAt,
            recordedAt = command.recordedAt,
            note = command.note,
        )
        paymentCommandsById[command.commandId] = command
        accountState.value = account.copy(ledger = updated)
        if (paymentFailuresAfterCommit > 0) {
            paymentFailuresAfterCommit -= 1
            error("Simulated unknown payment result after commit.")
        }
        return updated
    }

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger {
        reversalCalls += command
        reversalCommandsById[command.commandId]?.let { existing ->
            if (existing != command) throw CommandConflictException("Conflicting reversal command.")
            return account.ledger
        }

        val updated = account.ledger.reversePayment(
            id = command.entryId,
            paymentId = command.paymentId,
            recordedAt = command.recordedAt,
            reason = command.reason,
        )
        reversalCommandsById[command.commandId] = command
        accountState.value = account.copy(ledger = updated)
        if (reversalFailuresAfterCommit > 0) {
            reversalFailuresAfterCommit -= 1
            error("Simulated unknown reversal result after commit.")
        }
        return updated
    }
}
