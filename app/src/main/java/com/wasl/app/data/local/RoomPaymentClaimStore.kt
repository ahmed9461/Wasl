package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.ResolvePaymentClaimCommand
import com.wasl.app.data.local.entity.PaymentClaimEntity
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPaymentClaimStore(
    private val database: WaslDatabase,
) : PaymentClaimStore {
    private val debtDao = database.debtDao()
    private val claimDao = database.paymentClaimDao()

    override fun observeClaims(debtId: DebtId): Flow<List<PaymentClaimRecord>> =
        claimDao.observeForDebt(debtId.value).map { rows -> rows.map { it.toRecord() } }

    override fun observeOpenClaims(onOrBefore: LocalDate): Flow<List<PaymentClaimRecord>> =
        claimDao.observeActiveOnOrBefore(onOrBefore.toEpochDay())
            .map { rows -> rows.map { it.toRecord() } }

    override suspend fun createClaim(
        command: CreatePaymentClaimCommand,
    ): PaymentClaimRecord = database.withTransaction {
        claimDao.findByCreateCommandId(command.commandId)?.let { persisted ->
            validateCreateReplay(command, persisted)
            return@withTransaction persisted.toRecord()
        }
        claimDao.findById(command.claimId)?.let {
            throw CommandConflictException(
                "Payment claim ID ${command.claimId} is already used by another command.",
            )
        }

        val debt = debtDao.findAggregateById(command.debtId.value)?.debt
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        require(debt.direction == DebtDirection.PAYABLE.name) {
            "Payment claims are allowed only for payable debts."
        }
        require(debt.lifecycleState == DebtLifecycleState.ACTIVE.name && debt.closedAt == null) {
            "Only an active unsettled debt can receive a payment claim."
        }
        require(!command.claimedAt.isBefore(Instant.ofEpochMilli(debt.openedAt))) {
            "A payment claim cannot predate the debt."
        }

        val entity = PaymentClaimEntity(
            id = command.claimId,
            createCommandId = command.commandId,
            debtId = command.debtId.value,
            claimedAt = command.claimedAt.toEpochMilli(),
            followUpKind = command.followUpKind.name,
            followUpDateEpochDay = command.followUpDate?.toEpochDay(),
            note = command.note?.trim()?.ifEmpty { null },
            status = PaymentClaimStatus.ACTIVE.name,
            createdAt = command.createdAt.toEpochMilli(),
            resolutionCommandId = null,
            resolvedAt = null,
            resolutionNote = null,
            updatedAt = command.createdAt.toEpochMilli(),
        )
        claimDao.insert(entity)
        entity.toRecord()
    }

    override suspend fun resolveClaim(
        command: ResolvePaymentClaimCommand,
    ): PaymentClaimRecord = database.withTransaction {
        claimDao.findByResolutionCommandId(command.commandId)?.let { persisted ->
            validateResolutionReplay(command, persisted)
            return@withTransaction persisted.toRecord()
        }

        val current = claimDao.findById(command.claimId)
            ?: throw RecordNotFoundException("Payment claim ${command.claimId} was not found.")
        require(current.debtId == command.debtId.value) {
            "Payment claim belongs to another debt."
        }
        require(current.status == PaymentClaimStatus.ACTIVE.name) {
            "Only an active payment claim can be resolved."
        }
        require(!command.resolvedAt.isBefore(Instant.ofEpochMilli(current.createdAt))) {
            "Payment claim resolution cannot predate its creation."
        }

        check(
            claimDao.resolve(
                id = command.claimId,
                status = command.status.name,
                resolutionCommandId = command.commandId,
                resolvedAt = command.resolvedAt.toEpochMilli(),
                resolutionNote = command.note?.trim()?.ifEmpty { null },
                updatedAt = command.resolvedAt.toEpochMilli(),
            ) == 1,
        ) { "Payment claim ${command.claimId} was not resolved." }

        requireNotNull(claimDao.findById(command.claimId)).toRecord()
    }

    private fun validateCreateReplay(
        command: CreatePaymentClaimCommand,
        persisted: PaymentClaimEntity,
    ) {
        val matches = persisted.id == command.claimId &&
            persisted.debtId == command.debtId.value &&
            persisted.claimedAt == command.claimedAt.toEpochMilli() &&
            persisted.followUpKind == command.followUpKind.name &&
            persisted.followUpDateEpochDay == command.followUpDate?.toEpochDay() &&
            persisted.note == command.note?.trim()?.ifEmpty { null } &&
            persisted.createdAt == command.createdAt.toEpochMilli()
        if (!matches) {
            throw CommandConflictException(
                "Payment claim command ID was reused with different data.",
            )
        }
    }

    private fun validateResolutionReplay(
        command: ResolvePaymentClaimCommand,
        persisted: PaymentClaimEntity,
    ) {
        val matches = persisted.id == command.claimId &&
            persisted.debtId == command.debtId.value &&
            persisted.status == command.status.name &&
            persisted.resolutionCommandId == command.commandId &&
            persisted.resolvedAt == command.resolvedAt.toEpochMilli() &&
            persisted.resolutionNote == command.note?.trim()?.ifEmpty { null }
        if (!matches) {
            throw CommandConflictException(
                "Payment claim resolution command ID was reused with different data.",
            )
        }
    }

    private fun PaymentClaimEntity.toRecord(): PaymentClaimRecord {
        val recordStatus = PaymentClaimStatus.valueOf(status)
        val recordKind = PaymentClaimFollowUpKind.valueOf(followUpKind)
        check((recordStatus == PaymentClaimStatus.ACTIVE) == (resolvedAt == null)) {
            "Payment claim resolution projection is corrupt."
        }
        check((resolutionCommandId == null) == (resolvedAt == null)) {
            "Payment claim resolution command projection is corrupt."
        }
        return PaymentClaimRecord(
            id = id,
            debtId = DebtId(debtId),
            claimedAt = Instant.ofEpochMilli(claimedAt),
            followUpKind = recordKind,
            followUpDate = followUpDateEpochDay?.let(LocalDate::ofEpochDay),
            note = note,
            status = recordStatus,
            createdAt = Instant.ofEpochMilli(createdAt),
            resolvedAt = resolvedAt?.let(Instant::ofEpochMilli),
            resolutionNote = resolutionNote,
        )
    }
}
