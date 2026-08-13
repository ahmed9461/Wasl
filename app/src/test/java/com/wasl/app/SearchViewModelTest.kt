package com.wasl.app

import com.wasl.app.data.AccountOverview
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
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun blankQueryDoesNotRunAnUnboundedSearch() = runTest {
        val repository = SearchFakeRepository(emptyList())
        val viewModel = SearchViewModel(repository)

        viewModel.updateQuery("  \n ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isQueryBlank)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertTrue(repository.observations.isEmpty())
    }

    @Test
    fun normalizesQueryAndExposesTheExplicitFiftyResultLimit() = runTest {
        val repository = SearchFakeRepository(
            (1..51).map { account("debt-$it", "شخص مشترك $it", "بيان") },
        )
        val viewModel = SearchViewModel(repository)

        viewModel.updateQuery("  شخص   مشترك ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("شخص مشترك", state.normalizedQuery)
        assertEquals(SEARCH_RESULT_LIMIT, state.results.size)
        assertTrue(state.hasMoreResults)
        assertEquals(listOf("شخص مشترك" to 51), repository.observations)
    }

    @Test
    fun activeSearchUpdatesAfterAResultIsAddedAndItsLedgerChanges() = runTest {
        val first = account("first", "أحمد", "رسوم متجر")
        val repository = SearchFakeRepository(listOf(first))
        val viewModel = SearchViewModel(repository)

        viewModel.updateQuery("متجر")
        advanceUntilIdle()
        assertEquals(listOf("first"), viewModel.uiState.value.resultIds())

        val second = account("second", "خالد", "دين متجر جديد")
        repository.replaceAccounts(listOf(first, second))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), viewModel.uiState.value.resultIds())

        val paidFirst = first.copy(
            ledger = first.ledger.recordPayment(
                id = LedgerEntryId("payment-first"),
                amount = Money(2_000L, CurrencyCode.YER),
                paidAt = Instant.parse("2026-08-13T00:01:00Z"),
            ),
        )
        repository.replaceAccounts(listOf(paidFirst, second))
        advanceUntilIdle()

        assertEquals(
            Money(8_000L, CurrencyCode.YER),
            viewModel.uiState.value.results.first { it.ledger.header.id.value == "first" }
                .ledger.balance,
        )
    }

    @Test
    fun failedReadShowsRetryAndTheSameQueryCanRecover() = runTest {
        val repository = SearchFakeRepository(
            initialAccounts = listOf(account("debt", "أحمد", "إيجار")),
            failNextObservation = true,
        )
        val viewModel = SearchViewModel(repository)

        viewModel.updateQuery("إيجار")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.retryLoad()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.loadError)
        assertEquals(listOf("debt"), viewModel.uiState.value.resultIds())
        assertEquals(2, repository.observations.size)
    }
}

private fun SearchUiState.resultIds(): List<String> =
    results.map { it.ledger.header.id.value }

private class SearchFakeRepository(
    initialAccounts: List<AccountOverview>,
    var failNextObservation: Boolean = false,
) : WaslRepository {
    private val accounts = MutableStateFlow(initialAccounts)
    val observations = mutableListOf<Pair<String, Int>>()

    fun replaceAccounts(values: List<AccountOverview>) {
        accounts.value = values
    }

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
    ): Flow<List<AccountOverview>> {
        observations += query to limit
        if (failNextObservation) {
            failNextObservation = false
            return flow { error("Simulated search failure.") }
        }
        return accounts.map { values ->
            values.filter { account ->
                account.person.displayName.contains(query, ignoreCase = true) ||
                    account.ledger.header.description?.contains(query, ignoreCase = true) == true
            }.take(limit)
        }
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

private fun account(
    id: String,
    personName: String,
    description: String,
): AccountOverview {
    val openedAt = Instant.parse("2026-08-13T00:00:00Z")
    return AccountOverview(
        person = PersonRecord(
            id = PersonId("person-$id"),
            displayName = personName,
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
                description = description,
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
    )
}
