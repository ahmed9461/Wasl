package com.wasl.app.data

import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.GroupExpenseId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WaslRepository {
    fun observeAccounts(): Flow<List<AccountOverview>>

    fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>>

    fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>>

    fun observePeople(query: String, limit: Int): Flow<List<PersonRecord>>

    fun observeAccount(debtId: DebtId): Flow<AccountOverview?>

    fun observeGroupExpenses(): Flow<List<GroupExpenseRecord>> = flowOf(emptyList())

    suspend fun createPersonWithDebt(command: CreatePersonWithDebtCommand): AccountOverview

    suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview

    suspend fun createGroupExpense(command: CreateGroupExpenseCommand): GroupExpenseRecord =
        throw UnsupportedOperationException("Group expenses are not available in this repository.")

    suspend fun getAccount(debtId: DebtId): AccountOverview?

    suspend fun getGroupExpense(groupExpenseId: GroupExpenseId): GroupExpenseRecord? = null

    suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger

    suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger

    suspend fun updateDueSchedule(command: UpdateDueScheduleCommand): AccountOverview
}
