package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.entity.DebtAggregate
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.app.data.local.entity.LedgerEntryEntity
import com.wasl.app.data.local.entity.PersonEntity
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.DebtState
import com.wasl.domain.LedgerEntry
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWaslRepository(
    private val database: WaslDatabase,
) : WaslRepository {
    private val personDao = database.personDao()
    private val debtDao = database.debtDao()
    private val ledgerDao = database.ledgerDao()

    override fun observeAccounts(): Flow<List<AccountOverview>> =
        debtDao.observeActiveAggregates().map { aggregates ->
            aggregates.map(::toAccountOverview)
        }

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =
        debtDao.observeAggregateById(debtId.value).map { aggregate ->
            aggregate?.let(::toAccountOverview)
        }

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview = database.withTransaction {
        val existingDebt = debtDao.findAggregateById(command.debtId.value)
        if (existingDebt != null) {
            validateCreateReplay(command, existingDebt)
            return@withTransaction toAccountOverview(existingDebt)
        }

        val normalizedName = command.personName.trim()
        if (personDao.findById(command.personId.value) != null) {
            throw CommandConflictException(
                "Person ID ${command.personId.value} is already used by another command.",
            )
        }
        personDao.insert(
            PersonEntity(
                id = command.personId.value,
                displayName = normalizedName,
                phone = null,
                email = null,
                photoUri = null,
                notes = command.personNotes?.trim(),
                createdAt = command.createdAt.toEpochMilli(),
                updatedAt = command.createdAt.toEpochMilli(),
                archivedAt = null,
            ),
        )

        debtDao.insert(
            DebtEntity(
                id = command.debtId.value,
                personId = command.personId.value,
                direction = command.direction.name,
                originalAmountMinor = command.originalAmount.minorUnits,
                currencyCode = command.originalAmount.currency.value,
                openedAt = command.openedAt.toEpochMilli(),
                dueDateEpochDay = command.dueDate?.toEpochDay(),
                description = command.description?.trim(),
                notes = command.debtNotes?.trim(),
                lifecycleState = DebtLifecycleState.ACTIVE.name,
                createdAt = command.createdAt.toEpochMilli(),
                updatedAt = command.createdAt.toEpochMilli(),
                closedAt = null,
            ),
        )

        toAccountOverview(
            requireNotNull(debtDao.findAggregateById(command.debtId.value)) {
                "Created debt could not be read back."
            },
        )
    }

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        debtDao.findAggregateById(debtId.value)?.let(::toAccountOverview)

    override suspend fun recordPayment(command: RecordPaymentCommand): DebtLedger =
        database.withTransaction {
            ledgerDao.findByCommandId(command.commandId)?.let { persisted ->
                validatePaymentReplay(command, persisted)
                return@withTransaction requireAccount(command.debtId).ledger
            }

            val current = requireAccount(command.debtId).ledger
            val updated = current.recordPayment(
                id = command.entryId,
                amount = command.amount,
                paidAt = command.paidAt,
                recordedAt = command.recordedAt,
                note = command.note?.trim(),
            )
            ledgerDao.insert(
                LedgerEntryEntity(
                    id = command.entryId.value,
                    commandId = command.commandId,
                    debtId = command.debtId.value,
                    kind = LedgerKind.PAYMENT.name,
                    amountMinor = command.amount.minorUnits,
                    currencyCode = command.amount.currency.value,
                    occurredAt = command.paidAt.toEpochMilli(),
                    recordedAt = command.recordedAt.toEpochMilli(),
                    reversesEntryId = null,
                    note = command.note?.trim(),
                    reason = null,
                    sequenceNumber = current.entries.size.toLong() + 1L,
                ),
            )
            updateClosure(command.debtId, updated, command.recordedAt)
            requireAccount(command.debtId).ledger
        }

    override suspend fun reversePayment(command: ReversePaymentCommand): DebtLedger =
        database.withTransaction {
            ledgerDao.findByCommandId(command.commandId)?.let { persisted ->
                validateReversalReplay(command, persisted)
                return@withTransaction requireAccount(command.debtId).ledger
            }

            val current = requireAccount(command.debtId).ledger
            val updated = current.reversePayment(
                id = command.entryId,
                paymentId = command.paymentId,
                recordedAt = command.recordedAt,
                reason = command.reason.trim(),
            )
            ledgerDao.insert(
                LedgerEntryEntity(
                    id = command.entryId.value,
                    commandId = command.commandId,
                    debtId = command.debtId.value,
                    kind = LedgerKind.PAYMENT_REVERSAL.name,
                    amountMinor = null,
                    currencyCode = null,
                    occurredAt = null,
                    recordedAt = command.recordedAt.toEpochMilli(),
                    reversesEntryId = command.paymentId.value,
                    note = null,
                    reason = command.reason.trim(),
                    sequenceNumber = current.entries.size.toLong() + 1L,
                ),
            )
            updateClosure(command.debtId, updated, command.recordedAt)
            requireAccount(command.debtId).ledger
        }

    private suspend fun requireAccount(debtId: DebtId): AccountOverview =
        debtDao.findAggregateById(debtId.value)
            ?.let(::toAccountOverview)
            ?: throw RecordNotFoundException("Debt ${debtId.value} was not found.")

    private suspend fun updateClosure(
        debtId: DebtId,
        ledger: DebtLedger,
        updatedAt: Instant,
    ) {
        val closedAt = if (ledger.state == DebtState.SETTLED) {
            updatedAt.toEpochMilli()
        } else {
            null
        }
        check(
            debtDao.updateClosure(
                id = debtId.value,
                closedAt = closedAt,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Debt closure projection was not updated." }
    }

    private fun validateCreateReplay(
        command: CreatePersonWithDebtCommand,
        aggregate: DebtAggregate,
    ) {
        val persisted = toAccountOverview(aggregate)
        val expectedDescription = command.description?.trim()
        val expectedPersonNotes = command.personNotes?.trim()
        val expectedDebtNotes = command.debtNotes?.trim()
        val matches = persisted.person.id == command.personId &&
            persisted.person.displayName == command.personName.trim() &&
            persisted.person.notes == expectedPersonNotes &&
            persisted.person.createdAt == command.createdAt &&
            persisted.ledger.header.direction == command.direction &&
            persisted.ledger.header.originalAmount == command.originalAmount &&
            persisted.ledger.header.openedAt == command.openedAt &&
            persisted.ledger.header.dueDate == command.dueDate &&
            persisted.ledger.header.description == expectedDescription &&
            persisted.notes == expectedDebtNotes &&
            aggregate.debt.createdAt == command.createdAt.toEpochMilli()
        if (!matches) {
            throw CommandConflictException(
                "Debt ID ${command.debtId.value} is already used by a different command.",
            )
        }
    }

    private fun validatePaymentReplay(
        command: RecordPaymentCommand,
        persisted: LedgerEntryEntity,
    ) {
        val matches = persisted.id == command.entryId.value &&
            persisted.debtId == command.debtId.value &&
            persisted.kind == LedgerKind.PAYMENT.name &&
            persisted.amountMinor == command.amount.minorUnits &&
            persisted.currencyCode == command.amount.currency.value &&
            persisted.occurredAt == command.paidAt.toEpochMilli() &&
            persisted.recordedAt == command.recordedAt.toEpochMilli() &&
            persisted.note == command.note?.trim()
        if (!matches) throw CommandConflictException("Command ID was reused with different data.")
    }

    private fun validateReversalReplay(
        command: ReversePaymentCommand,
        persisted: LedgerEntryEntity,
    ) {
        val matches = persisted.id == command.entryId.value &&
            persisted.debtId == command.debtId.value &&
            persisted.kind == LedgerKind.PAYMENT_REVERSAL.name &&
            persisted.reversesEntryId == command.paymentId.value &&
            persisted.recordedAt == command.recordedAt.toEpochMilli() &&
            persisted.reason == command.reason.trim()
        if (!matches) throw CommandConflictException("Command ID was reused with different data.")
    }

    private fun toAccountOverview(aggregate: DebtAggregate): AccountOverview {
        val debt = aggregate.debt
        check(aggregate.person.id == debt.personId) { "Debt person relation is corrupt." }
        check(debt.originalAmountMinor > 0L) { "Stored original amount must be positive." }

        val orderedEntries = aggregate.entries.sortedBy { it.sequenceNumber }
        orderedEntries.forEachIndexed { index, entry ->
            check(entry.sequenceNumber == index.toLong() + 1L) {
                "Ledger sequence contains a gap or duplicate."
            }
        }

        val ledger = DebtLedger(
            header = DebtHeader(
                id = DebtId(debt.id),
                personId = PersonId(debt.personId),
                direction = DebtDirection.valueOf(debt.direction),
                originalAmount = Money(
                    minorUnits = debt.originalAmountMinor,
                    currency = CurrencyCode.of(debt.currencyCode),
                ),
                openedAt = Instant.ofEpochMilli(debt.openedAt),
                dueDate = debt.dueDateEpochDay?.let(LocalDate::ofEpochDay),
                description = debt.description,
            ),
            entries = orderedEntries.map(::toDomainEntry),
        )

        check((ledger.state == DebtState.SETTLED) == (debt.closedAt != null)) {
            "Debt closure projection does not match its ledger."
        }

        return AccountOverview(
            person = aggregate.person.toRecord(),
            ledger = ledger,
            lifecycleState = DebtLifecycleState.valueOf(debt.lifecycleState),
            notes = debt.notes,
            closedAt = debt.closedAt?.let(Instant::ofEpochMilli),
        )
    }

    private fun toDomainEntry(entity: LedgerEntryEntity): LedgerEntry =
        when (LedgerKind.valueOf(entity.kind)) {
            LedgerKind.PAYMENT -> {
                check(entity.reversesEntryId == null && entity.reason == null) {
                    "Payment row contains reversal-only fields."
                }
                PaymentRecorded(
                    id = LedgerEntryId(entity.id),
                    amount = Money(
                        minorUnits = requireNotNull(entity.amountMinor),
                        currency = CurrencyCode.of(requireNotNull(entity.currencyCode)),
                    ),
                    paidAt = Instant.ofEpochMilli(requireNotNull(entity.occurredAt)),
                    recordedAt = Instant.ofEpochMilli(entity.recordedAt),
                    note = entity.note,
                )
            }

            LedgerKind.PAYMENT_REVERSAL -> {
                check(
                    entity.amountMinor == null &&
                        entity.currencyCode == null &&
                        entity.occurredAt == null &&
                        entity.note == null,
                ) { "Reversal row contains payment-only fields." }
                PaymentReversed(
                    id = LedgerEntryId(entity.id),
                    paymentId = LedgerEntryId(requireNotNull(entity.reversesEntryId)),
                    recordedAt = Instant.ofEpochMilli(entity.recordedAt),
                    reason = requireNotNull(entity.reason),
                )
            }
        }

    private fun PersonEntity.toRecord(): PersonRecord = PersonRecord(
        id = PersonId(id),
        displayName = displayName,
        phone = phone,
        email = email,
        photoUri = photoUri,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        archivedAt = archivedAt?.let(Instant::ofEpochMilli),
    )

    private enum class LedgerKind {
        PAYMENT,
        PAYMENT_REVERSAL,
    }
}
