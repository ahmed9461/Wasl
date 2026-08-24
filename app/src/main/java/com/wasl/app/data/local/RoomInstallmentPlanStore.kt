package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateInstallmentPlanCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.InstallmentPlanItemInput
import com.wasl.app.data.InstallmentPlanRecord
import com.wasl.app.data.InstallmentPlanStatus
import com.wasl.app.data.InstallmentPlanStore
import com.wasl.app.data.InstallmentRecord
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.ReviseInstallmentPlanCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.entity.InstallmentEntity
import com.wasl.app.data.local.entity.InstallmentPlanEntity
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.InstallmentSchedule
import com.wasl.domain.InstallmentScheduleItem
import com.wasl.domain.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomInstallmentPlanStore(
    private val database: WaslDatabase,
    private val repository: WaslRepository,
) : InstallmentPlanStore {
    private val dao = database.installmentPlanDao()

    override fun observeInstallmentPlans(
        debtId: DebtId,
    ): Flow<List<InstallmentPlanRecord>> =
        combine(
            repository.observeAccount(debtId),
            dao.observePlansForDebt(debtId.value),
            dao.observeInstallmentsForDebt(debtId.value),
        ) { account, plans, installments ->
            if (account == null) {
                emptyList()
            } else {
                plans.map { plan ->
                    plan.toRecord(
                        account = account,
                        installments = installments.filter { it.planId == plan.id },
                    )
                }
            }
        }

    override fun observeActiveInstallmentPlans(): Flow<List<InstallmentPlanRecord>> =
        combine(
            repository.observeAccounts(),
            dao.observeActivePlans(),
            dao.observeAllInstallments(),
        ) { accounts, plans, installments ->
            val accountsByDebt = accounts.associateBy { it.ledger.header.id.value }
            plans.mapNotNull { plan ->
                accountsByDebt[plan.debtId]?.let { account ->
                    plan.toRecord(
                        account = account,
                        installments = installments.filter { it.planId == plan.id },
                    )
                }
            }
        }

    override fun observeActionableInstallments(
        onOrBefore: LocalDate,
    ): Flow<List<InstallmentRecord>> =
        observeActiveInstallmentPlans().map { plans ->
            plans.flatMap { it.installments }
                .filter { !it.isPaid && !it.dueDate.isAfter(onOrBefore) }
                .sortedWith(compareBy<InstallmentRecord> { it.dueDate }.thenBy { it.sequenceNumber })
        }

    override suspend fun createInstallmentPlan(
        command: CreateInstallmentPlanCommand,
    ): InstallmentPlanRecord = database.withTransaction {
        dao.findPlanByCommandId(command.commandId)?.let { persisted ->
            validateReplay(
                commandPlanId = command.planId,
                commandDebtId = command.debtId,
                commandCreatedAt = command.createdAt,
                commandSupersedesPlanId = null,
                commandReason = null,
                commandItems = command.installments,
                persisted = persisted,
            )
            return@withTransaction persisted.toRecord(
                account = requireAccount(command.debtId),
                installments = dao.findInstallmentsForPlan(persisted.id),
            )
        }
        dao.findPlanById(command.planId)?.let {
            throw CommandConflictException(
                "Installment plan ID ${command.planId} is already used by another command.",
            )
        }
        require(dao.findActivePlanForDebt(command.debtId.value) == null) {
            "Debt already has an active installment plan. Revise it instead."
        }

        val account = requireAccount(command.debtId)
        validateWritableDebt(account, command.createdAt)
        validateSchedule(command.installments, account)

        val plan = InstallmentPlanEntity(
            id = command.planId,
            commandId = command.commandId,
            debtId = command.debtId.value,
            revisionNumber = 1,
            status = InstallmentPlanStatus.ACTIVE.name,
            createdAt = command.createdAt.toEpochMilli(),
            supersedesPlanId = null,
            supersededAt = null,
            supersededAfterSequence = null,
            reason = null,
        )
        dao.insertPlan(plan)
        dao.insertInstallments(command.installments.toEntities(plan, command.createdAt))
        plan.toRecord(account, dao.findInstallmentsForPlan(plan.id))
    }

    override suspend fun reviseInstallmentPlan(
        command: ReviseInstallmentPlanCommand,
    ): InstallmentPlanRecord = database.withTransaction {
        dao.findPlanByCommandId(command.commandId)?.let { persisted ->
            validateReplay(
                commandPlanId = command.planId,
                commandDebtId = command.debtId,
                commandCreatedAt = command.createdAt,
                commandSupersedesPlanId = command.supersedesPlanId,
                commandReason = command.reason?.trim()?.ifEmpty { null },
                commandItems = command.installments,
                persisted = persisted,
            )
            return@withTransaction persisted.toRecord(
                account = requireAccount(command.debtId),
                installments = dao.findInstallmentsForPlan(persisted.id),
            )
        }
        dao.findPlanById(command.planId)?.let {
            throw CommandConflictException(
                "Installment plan ID ${command.planId} is already used by another command.",
            )
        }

        val account = requireAccount(command.debtId)
        validateWritableDebt(account, command.createdAt)
        validateSchedule(command.installments, account)
        val current = dao.findActivePlanForDebt(command.debtId.value)
            ?: throw RecordNotFoundException(
                "Debt ${command.debtId.value} has no active installment plan.",
            )
        require(current.id == command.supersedesPlanId) {
            "The installment plan changed before this revision was saved."
        }
        require(!command.createdAt.isBefore(Instant.ofEpochMilli(current.createdAt))) {
            "Installment revision cannot predate the active plan."
        }

        check(
            dao.markSuperseded(
                planId = current.id,
                supersededAt = command.createdAt.toEpochMilli(),
                supersededAfterSequence = account.ledger.entries.size.toLong(),
            ) == 1,
        ) { "Active installment plan was not superseded." }

        val plan = InstallmentPlanEntity(
            id = command.planId,
            commandId = command.commandId,
            debtId = command.debtId.value,
            revisionNumber = Math.addExact(current.revisionNumber, 1),
            status = InstallmentPlanStatus.ACTIVE.name,
            createdAt = command.createdAt.toEpochMilli(),
            supersedesPlanId = current.id,
            supersededAt = null,
            supersededAfterSequence = null,
            reason = command.reason?.trim()?.ifEmpty { null },
        )
        dao.insertPlan(plan)
        dao.insertInstallments(command.installments.toEntities(plan, command.createdAt))
        plan.toRecord(account, dao.findInstallmentsForPlan(plan.id))
    }

    private suspend fun requireAccount(debtId: DebtId): AccountOverview =
        repository.getAccount(debtId)
            ?: throw RecordNotFoundException("Debt ${debtId.value} was not found.")

    private fun validateWritableDebt(account: AccountOverview, changedAt: Instant) {
        require(account.lifecycleState == DebtLifecycleState.ACTIVE) {
            "Only an active debt can have an installment plan."
        }
        require(!account.ledger.balance.isZero) {
            "A settled debt cannot receive or revise an installment plan."
        }
        require(!changedAt.isBefore(account.ledger.header.openedAt)) {
            "Installment plan cannot predate the debt."
        }
    }

    private fun validateSchedule(
        items: List<InstallmentPlanItemInput>,
        account: AccountOverview,
    ) {
        val schedule = items.map { it.toScheduleItem() }
        InstallmentSchedule.validateExactTotal(
            items = schedule,
            expectedTotal = account.ledger.header.originalAmount,
        )
        require(items.map { it.id }.toSet().size == items.size) {
            "Installment IDs must be unique inside one plan."
        }
    }

    private suspend fun validateReplay(
        commandPlanId: String,
        commandDebtId: DebtId,
        commandCreatedAt: Instant,
        commandSupersedesPlanId: String?,
        commandReason: String?,
        commandItems: List<InstallmentPlanItemInput>,
        persisted: InstallmentPlanEntity,
    ) {
        val persistedItems = dao.findInstallmentsForPlan(persisted.id)
        val matches = persisted.id == commandPlanId &&
            persisted.debtId == commandDebtId.value &&
            persisted.createdAt == commandCreatedAt.toEpochMilli() &&
            persisted.supersedesPlanId == commandSupersedesPlanId &&
            persisted.reason == commandReason &&
            persistedItems.size == commandItems.size &&
            persistedItems.zip(commandItems).all { (stored, requested) ->
                stored.id == requested.id &&
                    stored.sequenceNumber == requested.sequenceNumber &&
                    stored.dueDateEpochDay == requested.dueDate.toEpochDay() &&
                    stored.amountMinor == requested.amount.minorUnits &&
                    stored.currencyCode == requested.amount.currency.value
            }
        if (!matches) {
            throw CommandConflictException(
                "Installment command ID was reused with different data.",
            )
        }
    }

    private fun InstallmentPlanEntity.toRecord(
        account: AccountOverview,
        installments: List<InstallmentEntity>,
    ): InstallmentPlanRecord {
        val statusValue = InstallmentPlanStatus.valueOf(status)
        val ordered = installments.sortedBy { it.sequenceNumber }
        val schedule = ordered.map { it.toScheduleItem() }
        InstallmentSchedule.validateExactTotal(
            items = schedule,
            expectedTotal = account.ledger.header.originalAmount,
        )

        val ledgerForProgress = when (statusValue) {
            InstallmentPlanStatus.ACTIVE -> account.ledger
            InstallmentPlanStatus.SUPERSEDED -> {
                val frozenSequence = requireNotNull(supersededAfterSequence) {
                    "Superseded installment plan is missing its Ledger snapshot boundary."
                }
                check(frozenSequence in 0L..account.ledger.entries.size.toLong()) {
                    "Installment plan Ledger snapshot boundary is invalid."
                }
                DebtLedger(
                    header = account.ledger.header,
                    entries = account.ledger.entries.take(frozenSequence.toInt()),
                )
            }
        }
        val effectivePaid = account.ledger.header.originalAmount.minus(ledgerForProgress.balance)
        val progress = InstallmentSchedule.progress(schedule, effectivePaid)
        val records = progress.mapIndexed { index, itemProgress ->
            val stored = ordered[index]
            InstallmentRecord(
                id = stored.id,
                planId = id,
                debtId = DebtId(debtId),
                revisionNumber = revisionNumber,
                sequenceNumber = stored.sequenceNumber,
                dueDate = LocalDate.ofEpochDay(stored.dueDateEpochDay),
                scheduledAmount = itemProgress.item.amount,
                paidAmount = itemProgress.paidAmount,
                remainingAmount = itemProgress.remainingAmount,
            )
        }
        return InstallmentPlanRecord(
            id = id,
            debtId = DebtId(debtId),
            revisionNumber = revisionNumber,
            status = statusValue,
            createdAt = Instant.ofEpochMilli(createdAt),
            supersedesPlanId = supersedesPlanId,
            supersededAt = supersededAt?.let(Instant::ofEpochMilli),
            supersededAfterLedgerSequence = supersededAfterSequence,
            reason = reason,
            installments = records,
        )
    }

    private fun InstallmentPlanItemInput.toScheduleItem(): InstallmentScheduleItem =
        InstallmentScheduleItem(
            sequenceNumber = sequenceNumber,
            dueDate = dueDate,
            amount = amount,
        )

    private fun InstallmentEntity.toScheduleItem(): InstallmentScheduleItem =
        InstallmentScheduleItem(
            sequenceNumber = sequenceNumber,
            dueDate = LocalDate.ofEpochDay(dueDateEpochDay),
            amount = Money(amountMinor, CurrencyCode(currencyCode)),
        )

    private fun List<InstallmentPlanItemInput>.toEntities(
        plan: InstallmentPlanEntity,
        createdAt: Instant,
    ): List<InstallmentEntity> = map { item ->
        InstallmentEntity(
            id = item.id,
            planId = plan.id,
            debtId = plan.debtId,
            sequenceNumber = item.sequenceNumber,
            dueDateEpochDay = item.dueDate.toEpochDay(),
            amountMinor = item.amount.minorUnits,
            currencyCode = item.amount.currency.value,
            createdAt = createdAt.toEpochMilli(),
        )
    }
}
