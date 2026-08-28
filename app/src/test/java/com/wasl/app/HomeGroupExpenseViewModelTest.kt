package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreateGroupExpenseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.GroupExpenseRecord
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HomeGroupExpenseViewModelTest {
    @get:org.junit.Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reviewRequiresAtLeastTwoParticipantsAndNeverPersistsEarly() = runTest {
        val repository = GroupHomeFakeRepository(
            people = listOf(person("p1", "أحمد"), person("p2", "سالم")),
        )
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.openGroupExpenseDialog()
        viewModel.toggleGroupParticipant(PersonId("p1"))
        viewModel.updateGroupParticipantAmount(PersonId("p1"), "1000")
        viewModel.updateGroupDescription("عشاء")
        viewModel.reviewGroupExpense()

        assertEquals(GroupExpenseEditorStep.EDIT, viewModel.uiState.value.groupExpenseStep)
        assertEquals("اختر شخصين على الأقل للعملية الجماعية.", viewModel.uiState.value.groupExpenseError)
        assertTrue(repository.groupCommands.isEmpty())
    }

    @Test
    fun reviewBuildsExactMinorUnitTotalBeforeAtomicConfirmation() = runTest {
        val repository = GroupHomeFakeRepository(
            people = listOf(person("p1", "أحمد"), person("p2", "سالم")),
        )
        val ids = ArrayDeque(
            listOf(
                "share-1", "debt-1",
                "share-2", "debt-2",
                "command-1", "group-1",
            ),
        )
        val now = Instant.parse("2026-08-28T01:00:00Z")
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
        )
        advanceUntilIdle()

        viewModel.openGroupExpenseDialog()
        viewModel.toggleGroupParticipant(PersonId("p1"))
        viewModel.toggleGroupParticipant(PersonId("p2"))
        viewModel.updateGroupParticipantAmount(PersonId("p1"), "١٠٠٬٠٠٠")
        viewModel.updateGroupParticipantAmount(PersonId("p2"), "٢٥٬٠٠٠")
        viewModel.updateGroupDescription("مشتريات مشتركة")
        viewModel.reviewGroupExpense()

        val preview = assertNotNull(viewModel.uiState.value.groupExpensePreview)
        assertEquals(GroupExpenseEditorStep.REVIEW, viewModel.uiState.value.groupExpenseStep)
        assertEquals(Money(125_000L, CurrencyCode.YER), preview.totalAmount)
        assertEquals(listOf(100_000L, 25_000L), preview.shares.map { it.amount.minorUnits })
        assertEquals(listOf("p1", "p2"), preview.shares.map { it.personId.value })
        assertTrue(repository.groupCommands.isEmpty())

        viewModel.confirmGroupExpense()
        advanceUntilIdle()

        val persisted = repository.groupCommands.single()
        assertEquals("command-1", persisted.commandId)
        assertEquals(GroupExpenseId("group-1"), persisted.expense.id)
        assertEquals(now, persisted.createdAt)
        assertFalse(viewModel.uiState.value.isGroupExpenseDialogOpen)
        assertEquals("تم حفظ العملية الجماعية وربط 2 حسابات بنجاح.", viewModel.uiState.value.successMessage)
    }

    @Test
    fun unknownPersistenceResultRetriesTheExactSameGroupCommand() = runTest {
        val repository = GroupHomeFakeRepository(
            people = listOf(person("p1", "أحمد"), person("p2", "سالم")),
            groupFailuresRemaining = 1,
        )
        val ids = ArrayDeque(
            listOf(
                "share-1", "debt-1",
                "share-2", "debt-2",
                "command-stable", "group-stable",
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC),
            idFactory = { ids.removeFirst() },
        )
        advanceUntilIdle()

        viewModel.openGroupExpenseDialog()
        viewModel.toggleGroupParticipant(PersonId("p1"))
        viewModel.toggleGroupParticipant(PersonId("p2"))
        viewModel.updateGroupParticipantAmount(PersonId("p1"), "500")
        viewModel.updateGroupParticipantAmount(PersonId("p2"), "700")
        viewModel.updateGroupDescription("فاتورة مشتركة")
        viewModel.reviewGroupExpense()

        viewModel.confirmGroupExpense()
        advanceUntilIdle()
        assertEquals(GroupExpenseEditorStep.REVIEW, viewModel.uiState.value.groupExpenseStep)
        assertEquals("لم تُحفظ العملية الجماعية. أعد المحاولة دون تغيير البيانات.", viewModel.uiState.value.groupExpenseError)

        viewModel.confirmGroupExpense()
        advanceUntilIdle()

        assertEquals(2, repository.groupCommands.size)
        assertEquals(repository.groupCommands.first(), repository.groupCommands.last())
        assertFalse(viewModel.uiState.value.isGroupExpenseDialogOpen)
    }

    @Test
    fun editingAfterReviewInvalidatesPreviewBeforeAnySave() = runTest {
        val repository = GroupHomeFakeRepository(
            people = listOf(person("p1", "أحمد"), person("p2", "سالم")),
        )
        val ids = ArrayDeque(
            listOf(
                "share-1", "debt-1",
                "share-2", "debt-2",
                "command-1", "group-1",
            ),
        )
        val viewModel = HomeViewModel(repository = repository, idFactory = { ids.removeFirst() })
        advanceUntilIdle()

        viewModel.openGroupExpenseDialog()
        viewModel.toggleGroupParticipant(PersonId("p1"))
        viewModel.toggleGroupParticipant(PersonId("p2"))
        viewModel.updateGroupParticipantAmount(PersonId("p1"), "10")
        viewModel.updateGroupParticipantAmount(PersonId("p2"), "20")
        viewModel.updateGroupDescription("اختبار")
        viewModel.reviewGroupExpense()
        assertNotNull(viewModel.uiState.value.groupExpensePreview)

        viewModel.editGroupExpenseReview()

        assertEquals(GroupExpenseEditorStep.EDIT, viewModel.uiState.value.groupExpenseStep)
        assertEquals(null, viewModel.uiState.value.groupExpensePreview)
        assertTrue(repository.groupCommands.isEmpty())
    }
}

private class GroupHomeFakeRepository(
    private val people: List<PersonRecord>,
    private var groupFailuresRemaining: Int = 0,
) : WaslRepository {
    val groupCommands = mutableListOf<CreateGroupExpenseCommand>()

    override fun observeAccounts(): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>> = flowOf(emptyList())

    override fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>> = flowOf(
        people.filter { it.displayName.contains(query.trim(), ignoreCase = true) }.take(limit),
    )

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> = flowOf(null)

    override suspend fun createPersonWithDebt(command: CreatePersonWithDebtCommand): AccountOverview =
        error("Not used by group expense tests")

    override suspend fun createDebtForExistingPerson(command: CreateDebtForExistingPersonCommand): AccountOverview =
        error("Not used by group expense tests")

    override suspend fun createGroupExpense(command: CreateGroupExpenseCommand): GroupExpenseRecord {
        groupCommands += command
        if (groupFailuresRemaining > 0) {
            groupFailuresRemaining -= 1
            error("Simulated unknown persistence result")
        }
        return GroupExpenseRecord(
            commandId = command.commandId,
            expense = command.expense,
            createdAt = command.createdAt,
        )
    }

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
