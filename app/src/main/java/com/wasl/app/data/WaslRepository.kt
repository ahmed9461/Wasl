package com.wasl.app.data

import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface WaslRepository {
    fun observeAccounts(): Flow<List<AccountOverview>>

    fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>>

    fun observeSearchAccounts(query: String, limit: Int): Flow<List<AccountOverview>>

    fun observeAccount(debtId: DebtId): Flow<AccountOverview?>

    suspend fun createPersonWithDebt(command: CreatePersonWithDebtCommand): AccountOverview

    suspend fun getAccount(debtId: DebtId): AccountOverview?

    suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger

    suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger
}
