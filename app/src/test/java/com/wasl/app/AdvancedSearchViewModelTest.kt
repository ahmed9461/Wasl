package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AdvancedSearchResult
import com.wasl.app.data.AdvancedSearchResultType
import com.wasl.app.data.AdvancedSearchStore
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.Money
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun collectsTypedResultsAndUsesTheProvidedCivilZone() = runTest {
        val repository = EmptySearchRepository()
        val store = RecordingAdvancedSearchStore(
            listOf(
                AdvancedSearchResult(
                    id = "document-1",
                    type = AdvancedSearchResultType.DOCUMENT,
                    debtId = DebtId("debt-1"),
                    personName = "أحمد",
                    amount = Money(20_000L, CurrencyCode.YER),
                    date = LocalDate.of(2026, 8, 13),
                    documentNumber = "PAY-2026-00042",
                    documentType = DocumentType.PAYMENT_RECEIPT,
                    documentStatus = DocumentStatus.READY,
                ),
            ),
        )
        val zoneId = ZoneId.of("Asia/Aden")
        val viewModel = SearchViewModel(
            repository = repository,
            advancedSearchStore = store,
            zoneIdProvider = { zoneId },
        )

        viewModel.updateQuery("  PAY-2026-00042  ")
        advanceUntilIdle()

        assertEquals("PAY-2026-00042", viewModel.uiState.value.normalizedQuery)
        assertEquals(listOf("document-1"), viewModel.uiState.value.advancedResults.map { it.id })
        assertEquals(listOf(Triple("PAY-2026-00042", zoneId, 51)), store.observations)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun blankQueryDoesNotStartAdvancedSearch() = runTest {
        val store = RecordingAdvancedSearchStore(emptyList())
        val viewModel = SearchViewModel(
            repository = EmptySearchRepository(),
            advancedSearchStore = store,
        )

        viewModel.updateQuery("   ")
        advanceUntilIdle()

        assertEquals(emptyList(), store.observations)
        assertEquals(emptyList(), viewModel.uiState.value.advancedResults)
    }
}

private class RecordingAdvancedSearchStore(
    initialResults: List<AdvancedSearchResult>,
) : AdvancedSearchStore {
    private val results = MutableStateFlow(initialResults)
    val observations = mutableListOf<Triple<String, ZoneId, Int>>()

    override fun observeAdvancedSearch(
        query: String,
        zoneId: ZoneId,
        limit: Int,
    ): Flow<List<AdvancedSearchResult>> {
        observations += Triple(query, zoneId, limit)
        return results
    }
}

private class EmptySearchRepository : WaslRepository {
    override fun observeAccounts(): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> =
        flowOf(emptyList())

    override fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>> =
        flowOf(emptyList())

    override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> =
        flowOf(emptyList())

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> = flowOf(null)

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview = error("Not used in this test.")

    override suspend fun getAccount(debtId: DebtId): AccountOverview? = null

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger =
        error("Not used in this test.")

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger =
        error("Not used in this test.")

    override suspend fun updateDueSchedule(command: UpdateDueScheduleCommand): AccountOverview =
        error("Not used in this test.")
}
