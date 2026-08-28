package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface InstallmentPlanStore {
    fun observeInstallmentPlans(debtId: DebtId): Flow<List<InstallmentPlanRecord>>

    fun observeActiveInstallmentPlans(): Flow<List<InstallmentPlanRecord>>

    fun observeActionableInstallments(onOrBefore: LocalDate): Flow<List<InstallmentRecord>>

    suspend fun createInstallmentPlan(
        command: CreateInstallmentPlanCommand,
    ): InstallmentPlanRecord

    suspend fun reviseInstallmentPlan(
        command: ReviseInstallmentPlanCommand,
    ): InstallmentPlanRecord
}

object UnavailableInstallmentPlanStore : InstallmentPlanStore {
    override fun observeInstallmentPlans(debtId: DebtId): Flow<List<InstallmentPlanRecord>> =
        flowOf(emptyList())

    override fun observeActiveInstallmentPlans(): Flow<List<InstallmentPlanRecord>> =
        flowOf(emptyList())

    override fun observeActionableInstallments(
        onOrBefore: LocalDate,
    ): Flow<List<InstallmentRecord>> = flowOf(emptyList())

    override suspend fun createInstallmentPlan(
        command: CreateInstallmentPlanCommand,
    ): InstallmentPlanRecord = error("Installment plans are unavailable.")

    override suspend fun reviseInstallmentPlan(
        command: ReviseInstallmentPlanCommand,
    ): InstallmentPlanRecord = error("Installment plans are unavailable.")
}
