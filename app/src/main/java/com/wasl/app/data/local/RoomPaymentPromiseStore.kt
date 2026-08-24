package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.ResolvePaymentPromiseCommand
import com.wasl.app.data.local.entity.PaymentPromiseEntity
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPaymentPromiseStore(
    private val database: WaslDatabase,
) : PaymentPromiseStore {
    private val debtDao = database.debtDao()
    private val promiseDao = database.paymentPromiseDao()

    override fun observePaymentPromises(debtId: DebtId): Flow<List<PaymentPromiseRecord>> =
        promiseDao.observeForDebt(debtId.value).map { rows -> rows.map { it.toRecord() } }

    override suspend fun createPaymentPromise(
        command: CreatePaymentPromiseCommand,
    ): PaymentPromiseRecord = database.withTransaction {
        promiseDao.findByCreateCommandId(command.commandId)?.let { persisted ->
            validateCreateReplay(command, persisted)
            return@withTransaction persisted.toRecord()
        }
        promiseDao.findById(command.promiseId)?.let {
            throw CommandConflictException(
                "Payment promise ID ${command.promiseId} is already used by another command.",
            )
        }

        val debt = debtDao.findAggregateById(command.debtId.value)?.debt
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        require(debt.lifecycleState == DebtLifecycleState.ACTIVE.name) {
            "Only an active debt can receive a payment promise."
        }
        require(debt.closedAt == null) {
            "A settled debt cannot receive a payment promise."
        }
        require(!command.createdAt.isBefore(Instant.ofEpochMilli(debt.openedAt))) {
            "A payment promise cannot be recorded before the debt was opened."
        }

        val normalizedNote = command.note?.trim()?.ifEmpty { null }
        val entity = PaymentPromiseEntity(
            id = command.promiseId,
            createCommandId = command.commandId,
            debtId = command.debtId.value,
            promisedDateEpochDay = command.promisedDate.toEpochDay(),
            status = PaymentPromiseStatus.PENDING.name,
            note = normalizedNote,
            createdAt = command.createdAt.toEpochMilli(),
            resolutionCommandId = null,
            resolvedAt = null,
            resolutionNote = null,
            updatedAt = command.createdAt.toEpochMilli(),
        )
        promiseDao.insert(entity)
        entity.toRecord()
    }

    override suspend fun resolvePaymentPromise(
        command: ResolvePaymentPromiseCommand,
    ): PaymentPromiseRecord = database.withTransaction {
        promiseDao.findByResolutionCommandId(command.commandId)?.let { persisted ->
            validateResolutionReplay(command, persisted)
            return@withTransaction persisted.toRecord()
        }

        val current = promiseDao.findById(command.promiseId)
            ?: throw RecordNotFoundException(
                "Payment promise ${command.promiseId} was not found.",
            )
        require(current.debtId == command.debtId.value) {
            "Payment promise belongs to another debt."
        }
        require(current.status == PaymentPromiseStatus.PENDING.name) {
            "Only a pending payment promise can be resolved."
        }
        require(!command.resolvedAt.isBefore(Instant.ofEpochMilli(current.createdAt))) {
            "Payment promise resolution cannot predate its creation."
        }

        check(
            promiseDao.resolve(
                id = command.promiseId,
                status = command.status.name,
                resolutionCommandId = command.commandId,
                resolvedAt = command.resolvedAt.toEpochMilli(),
                resolutionNote = command.note?.trim()?.ifEmpty { null },
                updatedAt = command.resolvedAt.toEpochMilli(),
            ) == 1,
        ) { "Payment promise ${command.promiseId} was not resolved." }

        requireNotNull(promiseDao.findById(command.promiseId)).toRecord()
    }

    private fun validateCreateReplay(
        command: CreatePaymentPromiseCommand,
        persisted: PaymentPromiseEntity,
    ) {
        val matches = persisted.id == command.promiseId &&
            persisted.debtId == command.debtId.value &&
            persisted.promisedDateEpochDay == command.promisedDate.toEpochDay() &&
            persisted.note == command.note?.trim()?.ifEmpty { null } &&
            persisted.createdAt == command.createdAt.toEpochMilli()
        if (!matches) {
            throw CommandConflictException(
                "Payment promise command ID was reused with different data.",
            )
        }
    }

    private fun validateResolutionReplay(
        command: ResolvePaymentPromiseCommand,
        persisted: PaymentPromiseEntity,
    ) {
        val matches = persisted.id == command.promiseId &&
            persisted.debtId == command.debtId.value &&
            persisted.status == command.status.name &&
            persisted.resolutionCommandId == command.commandId &&
            persisted.resolvedAt == command.resolvedAt.toEpochMilli() &&
            persisted.resolutionNote == command.note?.trim()?.ifEmpty { null }
        if (!matches) {
            throw CommandConflictException(
                "Payment promise resolution command ID was reused with different data.",
            )
        }
    }

    private fun PaymentPromiseEntity.toRecord(): PaymentPromiseRecord {
        val recordStatus = PaymentPromiseStatus.valueOf(status)
        check((recordStatus == PaymentPromiseStatus.PENDING) == (resolvedAt == null)) {
            "Payment promise resolution projection is corrupt."
        }
        check((resolutionCommandId == null) == (resolvedAt == null)) {
            "Payment promise resolution command projection is corrupt."
        }
        return PaymentPromiseRecord(
            id = id,
            debtId = DebtId(debtId),
            promisedDate = LocalDate.ofEpochDay(promisedDateEpochDay),
            status = recordStatus,
            note = note,
            createdAt = Instant.ofEpochMilli(createdAt),
            resolvedAt = resolvedAt?.let(Instant::ofEpochMilli),
            resolutionNote = resolutionNote,
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }
}
