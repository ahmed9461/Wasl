package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HomeGroupExpenseOverflowViewModelTest {
    @get:org.junit.Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reviewRejectsTotalThatWouldOverflowMinorUnits() = runTest {
        val repository = OverflowGroupFakeRepository(
            people = listOf(
                person("p1", "أحمد"),
                person("p2", "سالم"),
            ),
        )
        var nextId = 0
        val viewModel = HomeViewModel(
            repository = repository,
            idFactory = { "overflow-id-${++nextId}" },
        )
        advanceUntilIdle()

        viewModel.openGroupExpenseDialog()
        viewModel.toggleGroupParticipant(PersonId("p1"))
        viewModel.toggleGroupParticipant(PersonId("p2"))
        viewModel.updateGroupParticipantAmount(PersonId("p1"), Long.MAX_VALUE.toString())
        viewModel.updateGroupParticipantAmount(PersonId("p2"), Long.MAX_VALUE.toString())
        viewModel.updateGroupDescription("اختبار تجاوز الإجمالي")
        viewModel.reviewGroupExpense()

        assertEquals(GroupExpenseEditorStep.EDIT, viewModel.uiState.value.groupExpenseStep)
        assertNull(viewModel.uiState.value.groupExpensePreview)
        assertEquals(
            "إجمالي الحصص يتجاوز النطاق المالي المدعوم.",
            viewModel.uiState.value.groupExpenseError,
        )
    }
}

private class OverflowGroupFakeRepository(
    private val people: List<PersonRecord>,
) : WaslRepository {
    override fun observeAccounts(): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> = flowOf(
        people.filter { it.displayName.contains(query.trim(), ignoreCase = true) }.take(limit),
    )

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> = flowOf(null)

    override suspend fun createPersonWithDebt(command: CreatePersonWithDebtCommand): AccountOverview =
        error("Not used")

    override suspend fun createDebtForExistingPerson(command: CreateDebtForExistingPersonCommand): AccountOverview =
        error("Not used")

    override suspend fun getAccount(debtId: DebtId): AccountOverview? = null

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger = error("Not used")

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger = error("Not used")

    override suspend fun updateDueSchedule(command: UpdateDueScheduleCommand): AccountOverview = error("Not used")
}

private fun person(id: String, name: String): PersonRecord = PersonRecord(
    id = PersonId(id),
    displayName = name,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
