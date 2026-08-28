package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreateGroupExpenseCommand
import com.wasl.app.data.GroupExpenseRecord
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.DueScheduleAuditEvent
import com.wasl.app.data.DueScheduleSnapshot
import com.wasl.app.data.LocalSearchQuery
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.DocumentIdentityRecord
import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.app.data.PaymentReceiptStore
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.ReminderStore
import com.wasl.app.data.ReminderType
import com.wasl.app.data.StrongAlarmRequest
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.entity.DebtAggregate
import com.wasl.app.data.local.entity.AuditEventEntity
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.app.data.local.entity.GroupExpenseAggregate
import com.wasl.app.data.local.entity.GroupExpenseEntity
import com.wasl.app.data.local.entity.GroupExpenseShareEntity
import com.wasl.app.data.local.entity.LedgerEntryEntity
import com.wasl.app.data.local.entity.PersonEntity
import com.wasl.app.data.local.entity.ReminderEntity
import com.wasl.app.data.local.entity.DocumentIdentityEntity
import com.wasl.app.data.local.entity.IssuedDocumentEntity
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.DebtState
import com.wasl.domain.GroupExpense
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.GroupExpenseShare
import com.wasl.domain.GroupExpenseShareId
import com.wasl.domain.LedgerEntry
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomWaslRepository(
    private val database: WaslDatabase,
) : WaslRepository, ReminderStore, PaymentReceiptStore {
    private val personDao = database.personDao()
    private val debtDao = database.debtDao()
    private val ledgerDao = database.ledgerDao()
    private val reminderDao = database.reminderDao()
    private val auditEventDao = database.auditEventDao()
    private val documentIdentityDao = database.documentIdentityDao()
    private val issuedDocumentDao = database.issuedDocumentDao()
    private val groupExpenseDao = database.groupExpenseDao()

    override fun observeAccounts(): Flow<List<AccountOverview>> =
        debtDao.observeActiveAggregates().map { aggregates ->
            aggregates.map(::toAccountOverview)
        }

    override fun observeDueAccounts(onOrBefore: LocalDate): Flow<List<AccountOverview>> =
        debtDao.observeDueAggregates(onOrBefore.toEpochDay()).map { aggregates ->
            aggregates.map(::toAccountOverview)
        }

    override fun observeSearchAccounts(
        query: String,
        limit: Int,
    ): Flow<List<AccountOverview>> {
        require(limit > 0) { "Search limit must be positive." }
        val pattern = LocalSearchQuery.toSqlLikePattern(query) ?: return flowOf(emptyList())
        return debtDao.observeSearchAggregates(pattern, limit).map { aggregates ->
            aggregates.map(::toAccountOverview)
        }
    }

    override fun observePeople(
        query: String,
        limit: Int,
    ): Flow<List<PersonRecord>> {
        require(limit > 0) { "People limit must be positive." }
        val pattern = LocalSearchQuery.toSqlLikePattern(query)
        return personDao.observeActiveForSelection(pattern, limit).map { people ->
            people.map { it.toRecord() }
        }
    }

    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =
        debtDao.observeAggregateById(debtId.value).map { aggregate ->
            aggregate?.let(::toAccountOverview)
        }

    override fun observeGroupExpenses(): Flow<List<GroupExpenseRecord>> =
        groupExpenseDao.observeAggregates().map { aggregates ->
            aggregates.map(::toGroupExpenseRecord)
        }

    override suspend fun createPersonWithDebt(
        command: CreatePersonWithDebtCommand,
    ): AccountOverview = database.withTransaction {
        val existingDebt = debtDao.findAggregateById(command.debtId.value)
        if (existingDebt != null) {
            validateCreatePersonReplay(command, existingDebt)
            return@withTransaction toAccountOverview(existingDebt)
        }

        val normalizedName = command.personName.trim()
        val normalizedPhone = command.personPhone?.trim()?.ifEmpty { null }
        val normalizedEmail = command.personEmail?.trim()?.ifEmpty { null }
        val normalizedPersonNotes = command.personNotes?.trim()?.ifEmpty { null }
        if (personDao.findById(command.personId.value) != null) {
            throw CommandConflictException(
                "Person ID ${command.personId.value} is already used by another command.",
            )
        }
        personDao.insert(
            PersonEntity(
                id = command.personId.value,
                displayName = normalizedName,
                phone = normalizedPhone,
                email = normalizedEmail,
                photoUri = null,
                notes = normalizedPersonNotes,
                createdAt = command.createdAt.toEpochMilli(),
                updatedAt = command.createdAt.toEpochMilli(),
                archivedAt = null,
            ),
        )

        insertDebtWithReminder(command.toDebtCreation())

        toAccountOverview(
            requireNotNull(debtDao.findAggregateById(command.debtId.value)) {
                "Created debt could not be read back."
            },
        )
    }

    override suspend fun createDebtForExistingPerson(
        command: CreateDebtForExistingPersonCommand,
    ): AccountOverview = database.withTransaction {
        val creation = command.toDebtCreation()
        val existingDebt = debtDao.findAggregateById(command.debtId.value)
        if (existingDebt != null) {
            validateExistingPersonDebtReplay(creation, existingDebt)
            return@withTransaction toAccountOverview(existingDebt)
        }

        val person = personDao.findById(command.personId.value)
            ?: throw RecordNotFoundException(
                "Person ${command.personId.value} was not found.",
            )
        if (person.archivedAt != null) {
            throw RecordNotFoundException(
                "Person ${command.personId.value} is archived.",
            )
        }

        insertDebtWithReminder(creation)
        toAccountOverview(
            requireNotNull(debtDao.findAggregateById(command.debtId.value)) {
                "Created debt could not be read back."
            },
        )
    }

    override suspend fun createGroupExpense(
        command: CreateGroupExpenseCommand,
    ): GroupExpenseRecord = database.withTransaction {
        val normalizedExpense = command.expense.copy(
            description = command.expense.description.trim(),
            notes = command.expense.notes?.trim(),
        )
        val normalizedCommand = command.copy(expense = normalizedExpense)

        groupExpenseDao.findAggregateByCommandId(command.commandId)?.let { existing ->
            validateGroupExpenseReplay(normalizedCommand, existing)
            return@withTransaction toGroupExpenseRecord(existing)
        }
        if (groupExpenseDao.findAggregateById(normalizedExpense.id.value) != null) {
            throw CommandConflictException(
                "Group expense ID ${normalizedExpense.id.value} is already used by another command.",
            )
        }

        normalizedExpense.shares.forEach { share ->
            val person = personDao.findById(share.personId.value)
                ?: throw RecordNotFoundException("Person ${share.personId.value} was not found.")
            if (person.archivedAt != null) {
                throw RecordNotFoundException("Person ${share.personId.value} is archived.")
            }
            if (debtDao.findAggregateById(share.debtId.value) != null) {
                throw CommandConflictException(
                    "Debt ID ${share.debtId.value} is already used by another command.",
                )
            }
            if (groupExpenseDao.findShareById(share.id.value) != null) {
                throw CommandConflictException(
                    "Group expense share ID ${share.id.value} is already used.",
                )
            }
        }

        groupExpenseDao.insertGroupExpense(
            GroupExpenseEntity(
                id = normalizedExpense.id.value,
                commandId = command.commandId,
                direction = normalizedExpense.direction.name,
                totalAmountMinor = normalizedExpense.totalAmount.minorUnits,
                currencyCode = normalizedExpense.totalAmount.currency.value,
                occurredAt = normalizedExpense.occurredAt.toEpochMilli(),
                description = normalizedExpense.description,
                notes = normalizedExpense.notes,
                createdAt = command.createdAt.toEpochMilli(),
            ),
        )
        normalizedExpense.shares.forEachIndexed { index, share ->
            insertDebtWithReminder(
                DebtCreation(
                    personId = share.personId,
                    debtId = share.debtId,
                    direction = normalizedExpense.direction,
                    originalAmount = share.amount,
                    openedAt = normalizedExpense.occurredAt,
                    createdAt = command.createdAt,
                    dueDate = null,
                    description = normalizedExpense.description,
                    debtNotes = null,
                    dueReminder = null,
                    strongAlarm = null,
                ),
            )
            groupExpenseDao.insertShare(
                GroupExpenseShareEntity(
                    id = share.id.value,
                    groupExpenseId = normalizedExpense.id.value,
                    debtId = share.debtId.value,
                    personId = share.personId.value,
                    amountMinor = share.amount.minorUnits,
                    sequenceNumber = index + 1,
                ),
            )
        }

        toGroupExpenseRecord(
            requireNotNull(groupExpenseDao.findAggregateById(normalizedExpense.id.value)) {
                "Created group expense could not be read back."
            },
        )
    }

    override suspend fun getAccount(debtId: DebtId): AccountOverview? =
        debtDao.findAggregateById(debtId.value)?.let(::toAccountOverview)

    override suspend fun getGroupExpense(groupExpenseId: GroupExpenseId): GroupExpenseRecord? =
        groupExpenseDao.findAggregateById(groupExpenseId.value)?.let(::toGroupExpenseRecord)

    override suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord? =
        documentIdentityDao.findDefault()?.toRecord()

    override suspend fun preparePaymentReceipt(
        command: PreparePaymentReceiptCommand,
    ): IssuedDocumentRecord = database.withTransaction {
        issuedDocumentDao.findByCommandId(command.commandId)?.let { persisted ->
            validatePaymentReceiptReplay(command, persisted)
            return@withTransaction persisted.toRecord()
        }
        issuedDocumentDao.findBySource(
            documentType = DocumentType.PAYMENT_RECEIPT.name,
            ledgerEntryId = command.paymentId.value,
        )?.let { existing ->
            require(existing.debtId == command.debtId.value) {
                "Payment receipt source is linked to another debt."
            }
            return@withTransaction existing.toRecord()
        }

        val account = requireAccount(command.debtId)
        require(account.lifecycleState == DebtLifecycleState.ACTIVE) {
            "Only an active debt can issue a payment receipt."
        }
        val paymentIndex = account.ledger.entries.indexOfFirst { it.id == command.paymentId }
        if (paymentIndex < 0) {
            throw RecordNotFoundException(
                "Payment ${command.paymentId.value} was not found for debt ${command.debtId.value}.",
            )
        }
        val payment = account.ledger.entries[paymentIndex] as? PaymentRecorded
            ?: throw IllegalArgumentException("A payment receipt requires a payment entry.")
        require(payment.id !in account.ledger.reversedPaymentIds) {
            "A reversed payment cannot receive a new receipt."
        }
        require(!command.issuedAt.isBefore(payment.recordedAt)) {
            "A receipt cannot be issued before its payment was recorded."
        }

        val normalizedIdentity = DocumentIdentitySnapshot(
            displayName = command.issuerDisplayName.trim(),
            activityName = command.issuerActivityName.normalizedOptional(),
            phone = command.issuerPhone.normalizedOptional(),
            footerText = command.footerText.normalizedOptional(),
        )
        saveDefaultIdentity(command, normalizedIdentity)

        val issueYear = command.issuedAt.atZone(command.issueZoneId).year
        val sequenceNumber = issuedDocumentDao.nextSequenceNumber(issueYear)
        val documentNumber = "PAY-$issueYear-${sequenceNumber.toString().padStart(5, '0')}"
        val balanceBefore = DebtLedger(
            header = account.ledger.header,
            entries = account.ledger.entries.take(paymentIndex),
        ).balance
        val balanceAfter = DebtLedger(
            header = account.ledger.header,
            entries = account.ledger.entries.take(paymentIndex + 1),
        ).balance
        val snapshot = PaymentReceiptSnapshot(
            version = PAYMENT_RECEIPT_SNAPSHOT_VERSION,
            documentId = command.documentId,
            documentNumber = documentNumber,
            issuedAt = command.issuedAt,
            issueZoneId = command.issueZoneId,
            debtId = command.debtId,
            paymentId = payment.id,
            personId = account.person.id,
            personName = account.person.displayName,
            direction = account.ledger.header.direction,
            originalAmount = account.ledger.header.originalAmount,
            balanceBefore = balanceBefore,
            paymentAmount = payment.amount,
            balanceAfter = balanceAfter,
            paidAt = payment.paidAt,
            paymentNote = payment.note,
            debtDescription = account.ledger.header.description,
            identity = normalizedIdentity,
        )
        val entity = IssuedDocumentEntity(
            id = command.documentId,
            commandId = command.commandId,
            documentType = DocumentType.PAYMENT_RECEIPT.name,
            status = DocumentStatus.PENDING_PDF.name,
            documentNumber = documentNumber,
            issueYear = issueYear,
            sequenceNumber = sequenceNumber,
            debtId = command.debtId.value,
            ledgerEntryId = payment.id.value,
            identityId = command.identityId,
            personId = account.person.id.value,
            personNameSnapshot = account.person.displayName,
            amountMinor = payment.amount.minorUnits,
            currencyCode = payment.amount.currency.value,
            issuedAt = command.issuedAt.toEpochMilli(),
            snapshotVersion = PAYMENT_RECEIPT_SNAPSHOT_VERSION,
            snapshotJson = PaymentReceiptSnapshotCodec.encode(snapshot),
            pdfRelativePath = "documents/$documentNumber.pdf",
            pdfSha256 = null,
            pageCount = null,
            failureCode = null,
            createdAt = command.issuedAt.toEpochMilli(),
            updatedAt = command.issuedAt.toEpochMilli(),
        )
        issuedDocumentDao.insert(entity)
        entity.toRecord()
    }

    override suspend fun getIssuedDocument(documentId: String): IssuedDocumentRecord? =
        issuedDocumentDao.findById(documentId)?.toRecord()

    override suspend fun markDocumentReady(
        documentId: String,
        pdfSha256: String,
        pageCount: Int,
        updatedAt: Instant,
    ): IssuedDocumentRecord = database.withTransaction {
        require(pdfSha256.matches(Regex("[0-9a-f]{64}"))) {
            "PDF checksum must be a lowercase SHA-256 value."
        }
        require(pageCount > 0) { "A PDF must contain at least one page." }
        val existing = issuedDocumentDao.findById(documentId)
            ?: throw RecordNotFoundException("Document $documentId was not found.")
        if (existing.status == DocumentStatus.READY.name) {
            if (existing.pdfSha256 != pdfSha256 || existing.pageCount != pageCount) {
                throw CommandConflictException("Ready document metadata cannot be replaced.")
            }
            return@withTransaction existing.toRecord()
        }
        check(
            issuedDocumentDao.markReady(
                id = documentId,
                pdfSha256 = pdfSha256,
                pageCount = pageCount,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Document $documentId was not marked ready." }
        requireNotNull(issuedDocumentDao.findById(documentId)).toRecord()
    }

    override suspend fun markDocumentFailed(
        documentId: String,
        failureCode: String,
        updatedAt: Instant,
    ): IssuedDocumentRecord = database.withTransaction {
        require(failureCode.matches(Regex("[A-Z0-9_]{1,48}"))) {
            "Document failure code is invalid."
        }
        val existing = issuedDocumentDao.findById(documentId)
            ?: throw RecordNotFoundException("Document $documentId was not found.")
        if (existing.status == DocumentStatus.READY.name) {
            return@withTransaction existing.toRecord()
        }
        check(
            issuedDocumentDao.markFailed(
                id = documentId,
                failureCode = failureCode,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Document $documentId was not marked failed." }
        requireNotNull(issuedDocumentDao.findById(documentId)).toRecord()
    }

    override suspend fun getReminder(reminderId: String): ReminderRecord? =
        reminderDao.findById(reminderId)?.toRecord()

    override suspend fun getRecoverableReminders(): List<ReminderRecord> =
        reminderDao.findRecoverable().map { it.toRecord() }

    override suspend fun updateReminderSchedule(
        reminderId: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ) {
        check(
            reminderDao.updateSchedule(
                id = reminderId,
                triggerAt = triggerAt.toEpochMilli(),
                zoneId = zoneId.id,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $reminderId was not found." }
    }

    override suspend fun markReminderDelivered(reminderId: String, deliveredAt: Instant) {
        updateReminderStatus(
            reminderId = reminderId,
            status = ReminderStatus.DELIVERED,
            failureCode = null,
            deliveredAt = deliveredAt,
            updatedAt = deliveredAt,
        )
    }

    override suspend fun markReminderBlockedByPermission(
        reminderId: String,
        updatedAt: Instant,
    ) {
        updateReminderStatus(
            reminderId = reminderId,
            status = ReminderStatus.BLOCKED_PERMISSION,
            failureCode = FAILURE_NOTIFICATIONS_DISABLED,
            deliveredAt = null,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markReminderCancelled(reminderId: String, updatedAt: Instant) {
        updateReminderStatus(
            reminderId = reminderId,
            status = ReminderStatus.CANCELLED,
            failureCode = null,
            deliveredAt = null,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markReminderFailed(
        reminderId: String,
        failureCode: String,
        updatedAt: Instant,
    ) {
        updateReminderStatus(
            reminderId = reminderId,
            status = ReminderStatus.FAILED,
            failureCode = failureCode,
            deliveredAt = null,
            updatedAt = updatedAt,
        )
    }

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

    override suspend fun updateDueSchedule(
        command: UpdateDueScheduleCommand,
    ): AccountOverview = database.withTransaction {
        auditEventDao.findByCommandId(command.commandId)?.let { persisted ->
            validateDueScheduleReplay(command, persisted)
            return@withTransaction requireAccount(command.debtId)
        }

        val aggregate = debtDao.findAggregateById(command.debtId.value)
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        val current = toAccountOverview(aggregate)
        require(current.lifecycleState == DebtLifecycleState.ACTIVE) {
            "Only an active debt can change its due schedule."
        }
        require(!current.ledger.balance.isZero) {
            "A settled debt cannot receive a new due schedule."
        }
        require(!command.updatedAt.isBefore(current.ledger.header.openedAt)) {
            "Schedule update cannot predate the debt."
        }

        val existingReminder = reminderDao.findDueDateForDebt(command.debtId.value)
        val existingStrongAlarm = reminderDao.findStrongAlarmForDebt(command.debtId.value)
        val before = DueScheduleSnapshot(
            dueDate = current.ledger.header.dueDate,
            dueReminder = existingReminder?.activeDueRequestOrNull(),
            strongAlarm = existingStrongAlarm?.activeStrongAlarmRequestOrNull(),
        )
        val after = DueScheduleSnapshot(
            dueDate = command.dueDate,
            dueReminder = command.dueReminder,
            strongAlarm = command.strongAlarm,
        )
        require(before != after) { "Due schedule is unchanged." }

        command.dueReminder?.let { requested ->
            existingReminder?.let { existing ->
                require(existing.id == requested.id) {
                    "An existing due reminder must keep its stable ID."
                }
            }
        }
        command.strongAlarm?.let { requested ->
            existingStrongAlarm?.let { existing ->
                require(existing.id == requested.id) {
                    "An existing strong alarm must keep its stable ID."
                }
            }
        }

        check(
            debtDao.updateDueDate(
                id = command.debtId.value,
                dueDateEpochDay = command.dueDate?.toEpochDay(),
                updatedAt = command.updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Debt due date was not updated." }

        persistDueReminderChange(
            debtId = command.debtId,
            existing = existingReminder,
            requested = command.dueReminder,
            updatedAt = command.updatedAt,
        )
        persistStrongAlarmChange(
            debtId = command.debtId,
            existing = existingStrongAlarm,
            requested = command.strongAlarm,
            updatedAt = command.updatedAt,
        )

        auditEventDao.insert(
            AuditEventEntity(
                id = command.auditEventId,
                commandId = command.commandId,
                aggregateType = AuditAggregateType.DEBT.name,
                aggregateId = command.debtId.value,
                eventType = AuditEventType.DUE_SCHEDULE_CHANGED.name,
                occurredAt = command.updatedAt.toEpochMilli(),
                actor = AuditActor.LOCAL_USER.name,
                beforeSnapshot = DueScheduleAuditCodec.encode(before),
                afterSnapshot = DueScheduleAuditCodec.encode(after),
                reason = null,
            ),
        )

        requireAccount(command.debtId)
    }

    private suspend fun persistDueReminderChange(
        debtId: DebtId,
        existing: ReminderEntity?,
        requested: DueReminderRequest?,
        updatedAt: Instant,
    ) {
        when {
            requested == null && existing != null -> cancelReminder(existing.id, updatedAt)
            requested != null && existing == null -> reminderDao.insert(
                requested.toEntity(debtId, updatedAt),
            )
            requested != null && existing != null -> rescheduleReminder(
                existing.id,
                requested.triggerAt,
                requested.zoneId,
                updatedAt,
            )
        }
    }

    private suspend fun persistStrongAlarmChange(
        debtId: DebtId,
        existing: ReminderEntity?,
        requested: StrongAlarmRequest?,
        updatedAt: Instant,
    ) {
        when {
            requested == null && existing != null -> cancelReminder(existing.id, updatedAt)
            requested != null && existing == null -> reminderDao.insert(
                requested.toEntity(debtId, updatedAt),
            )
            requested != null && existing != null -> rescheduleReminder(
                existing.id,
                requested.triggerAt,
                requested.zoneId,
                updatedAt,
            )
        }
    }

    private suspend fun cancelReminder(id: String, updatedAt: Instant) {
        check(
            reminderDao.updateStatus(
                id = id,
                status = ReminderStatus.CANCELLED.name,
                failureCode = null,
                deliveredAt = null,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $id was not cancelled." }
    }

    private suspend fun rescheduleReminder(
        id: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ) {
        check(
            reminderDao.updateSchedule(
                id = id,
                triggerAt = triggerAt.toEpochMilli(),
                zoneId = zoneId.id,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $id was not rescheduled." }
    }

    private fun toGroupExpenseRecord(aggregate: GroupExpenseAggregate): GroupExpenseRecord {
        val entity = aggregate.groupExpense
        val orderedShares = aggregate.shares.sortedBy { it.sequenceNumber }
        orderedShares.forEachIndexed { index, share ->
            check(share.sequenceNumber == index + 1) {
                "Group expense share sequence contains a gap or duplicate."
            }
        }
        return GroupExpenseRecord(
            commandId = entity.commandId,
            expense = GroupExpense(
                id = GroupExpenseId(entity.id),
                direction = DebtDirection.valueOf(entity.direction),
                totalAmount = Money(
                    minorUnits = entity.totalAmountMinor,
                    currency = CurrencyCode.of(entity.currencyCode),
                ),
                occurredAt = Instant.ofEpochMilli(entity.occurredAt),
                description = entity.description,
                notes = entity.notes,
                shares = orderedShares.map { share ->
                    GroupExpenseShare(
                        id = GroupExpenseShareId(share.id),
                        debtId = DebtId(share.debtId),
                        personId = PersonId(share.personId),
                        amount = Money(
                            minorUnits = share.amountMinor,
                            currency = CurrencyCode.of(entity.currencyCode),
                        ),
                    )
                },
            ),
            createdAt = Instant.ofEpochMilli(entity.createdAt),
        )
    }

    private suspend fun validateGroupExpenseReplay(
        command: CreateGroupExpenseCommand,
        aggregate: GroupExpenseAggregate,
    ) {
        val persisted = toGroupExpenseRecord(aggregate)
        val expected = GroupExpenseRecord(
            commandId = command.commandId,
            expense = command.expense,
            createdAt = command.createdAt,
        )
        if (persisted != expected) {
            throw CommandConflictException(
                "Group expense command ID was reused with different data.",
            )
        }
        command.expense.shares.forEach { share ->
            val debtAggregate = debtDao.findAggregateById(share.debtId.value)
                ?: throw CommandConflictException("Group expense child debt is missing.")
            val account = toAccountOverview(debtAggregate)
            val expectedDebt = DebtCreation(
                personId = share.personId,
                debtId = share.debtId,
                direction = command.expense.direction,
                originalAmount = share.amount,
                openedAt = command.expense.occurredAt,
                createdAt = command.createdAt,
                dueDate = null,
                description = command.expense.description,
                debtNotes = null,
                dueReminder = null,
                strongAlarm = null,
            )
            if (!debtCreationMatches(expectedDebt, debtAggregate, account)) {
                throw CommandConflictException(
                    "Group expense child debt does not match the original command.",
                )
            }
        }
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

    private suspend fun insertDebtWithReminder(creation: DebtCreation) {
        debtDao.insert(
            DebtEntity(
                id = creation.debtId.value,
                personId = creation.personId.value,
                direction = creation.direction.name,
                originalAmountMinor = creation.originalAmount.minorUnits,
                currencyCode = creation.originalAmount.currency.value,
                openedAt = creation.openedAt.toEpochMilli(),
                dueDateEpochDay = creation.dueDate?.toEpochDay(),
                description = creation.description,
                notes = creation.debtNotes,
                lifecycleState = DebtLifecycleState.ACTIVE.name,
                createdAt = creation.createdAt.toEpochMilli(),
                updatedAt = creation.createdAt.toEpochMilli(),
                closedAt = null,
            ),
        )
        creation.dueReminder?.let { reminder ->
            reminderDao.insert(reminder.toEntity(creation.debtId, creation.createdAt))
        }
        creation.strongAlarm?.let { alarm ->
            reminderDao.insert(alarm.toEntity(creation.debtId, creation.createdAt))
        }
    }

    private fun validateCreatePersonReplay(
        command: CreatePersonWithDebtCommand,
        aggregate: DebtAggregate,
    ) {
        val persisted = toAccountOverview(aggregate)
        val expectedPhone = command.personPhone?.trim()?.ifEmpty { null }
        val expectedEmail = command.personEmail?.trim()?.ifEmpty { null }
        val expectedPersonNotes = command.personNotes?.trim()?.ifEmpty { null }
        val matches = debtCreationMatches(command.toDebtCreation(), aggregate, persisted) &&
            persisted.person.displayName == command.personName.trim() &&
            persisted.person.phone == expectedPhone &&
            persisted.person.email == expectedEmail &&
            persisted.person.notes == expectedPersonNotes &&
            persisted.person.createdAt == command.createdAt
        if (!matches) {
            throw CommandConflictException(
                "Debt ID ${command.debtId.value} is already used by a different command.",
            )
        }
    }

    private fun validateExistingPersonDebtReplay(
        creation: DebtCreation,
        aggregate: DebtAggregate,
    ) {
        val persisted = toAccountOverview(aggregate)
        if (!debtCreationMatches(creation, aggregate, persisted)) {
            throw CommandConflictException(
                "Debt ID ${creation.debtId.value} is already used by a different command.",
            )
        }
    }

    private fun debtCreationMatches(
        creation: DebtCreation,
        aggregate: DebtAggregate,
        persisted: AccountOverview,
    ): Boolean {
        val expectedReminder = creation.dueReminder
        val persistedReminder = persisted.dueReminder
        val reminderMatches = when {
            expectedReminder == null -> persistedReminder == null
            persistedReminder == null -> false
            else -> persistedReminder.id == expectedReminder.id &&
                persistedReminder.debtId == creation.debtId &&
                persistedReminder.triggerAt == expectedReminder.triggerAt &&
                persistedReminder.zoneId == expectedReminder.zoneId &&
                persistedReminder.type == ReminderType.DUE_DATE &&
                persistedReminder.scheduleType == ReminderScheduleType.WORK &&
                persistedReminder.createdAt == creation.createdAt
        }
        val expectedStrongAlarm = creation.strongAlarm
        val persistedStrongAlarm = persisted.strongAlarm
        val strongAlarmMatches = when {
            expectedStrongAlarm == null -> persistedStrongAlarm == null
            persistedStrongAlarm == null -> false
            else -> persistedStrongAlarm.id == expectedStrongAlarm.id &&
                persistedStrongAlarm.debtId == creation.debtId &&
                persistedStrongAlarm.triggerAt == expectedStrongAlarm.triggerAt &&
                persistedStrongAlarm.zoneId == expectedStrongAlarm.zoneId &&
                persistedStrongAlarm.type == ReminderType.STRONG_ALARM &&
                persistedStrongAlarm.scheduleType == ReminderScheduleType.EXACT_ALARM &&
                persistedStrongAlarm.createdAt == creation.createdAt
        }
        return persisted.person.id == creation.personId &&
            persisted.ledger.header.direction == creation.direction &&
            persisted.ledger.header.originalAmount == creation.originalAmount &&
            persisted.ledger.header.openedAt == creation.openedAt &&
            persisted.ledger.header.dueDate == creation.dueDate &&
            persisted.ledger.header.description == creation.description &&
            persisted.notes == creation.debtNotes &&
            aggregate.debt.createdAt == creation.createdAt.toEpochMilli() &&
            reminderMatches &&
            strongAlarmMatches
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

    private fun validateDueScheduleReplay(
        command: UpdateDueScheduleCommand,
        persisted: AuditEventEntity,
    ) {
        val afterSnapshot = persisted.afterSnapshot?.let(DueScheduleAuditCodec::decode)
        val matches = persisted.id == command.auditEventId &&
            persisted.aggregateType == AuditAggregateType.DEBT.name &&
            persisted.aggregateId == command.debtId.value &&
            persisted.eventType == AuditEventType.DUE_SCHEDULE_CHANGED.name &&
            persisted.occurredAt == command.updatedAt.toEpochMilli() &&
            persisted.actor == AuditActor.LOCAL_USER.name &&
            persisted.reason == null &&
            afterSnapshot == DueScheduleSnapshot(command.dueDate, command.dueReminder, command.strongAlarm)
        if (!matches) {
            throw CommandConflictException("Command ID was reused with different schedule data.")
        }
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

        val dueReminders = aggregate.reminders.filter {
            it.subjectType == ReminderSubjectType.DEBT.name &&
                it.reminderType == ReminderType.DUE_DATE.name
        }
        check(dueReminders.size <= 1) { "Debt contains duplicate due reminders." }
        val strongAlarms = aggregate.reminders.filter {
            it.subjectType == ReminderSubjectType.DEBT.name &&
                it.reminderType == ReminderType.STRONG_ALARM.name
        }
        check(strongAlarms.size <= 1) { "Debt contains duplicate strong alarms." }

        return AccountOverview(
            person = aggregate.person.toRecord(),
            ledger = ledger,
            lifecycleState = DebtLifecycleState.valueOf(debt.lifecycleState),
            notes = debt.notes,
            closedAt = debt.closedAt?.let(Instant::ofEpochMilli),
            dueReminder = dueReminders.singleOrNull()?.toRecord(),
            strongAlarm = strongAlarms.singleOrNull()?.toRecord(),
            dueScheduleAuditEvents = aggregate.auditEvents
                .map(::toDueScheduleAuditEvent)
                .sortedWith(
                    compareBy<DueScheduleAuditEvent> { it.occurredAt }.thenBy { it.id },
                ),
            issuedDocuments = aggregate.issuedDocuments
                .map { it.toRecord() }
                .sortedWith(
                    compareBy<IssuedDocumentRecord> { it.issuedAt }.thenBy { it.id },
                ),
        )
    }

    private suspend fun saveDefaultIdentity(
        command: PreparePaymentReceiptCommand,
        snapshot: DocumentIdentitySnapshot,
    ) {
        documentIdentityDao.clearOtherDefaults(command.identityId)
        val existing = documentIdentityDao.findById(command.identityId)
        if (existing == null) {
            documentIdentityDao.insert(
                DocumentIdentityEntity(
                    id = command.identityId,
                    displayName = snapshot.displayName,
                    activityName = snapshot.activityName,
                    phone = snapshot.phone,
                    footerText = snapshot.footerText,
                    isDefault = true,
                    createdAt = command.issuedAt.toEpochMilli(),
                    updatedAt = command.issuedAt.toEpochMilli(),
                ),
            )
        } else {
            check(
                documentIdentityDao.updateDefault(
                    id = command.identityId,
                    displayName = snapshot.displayName,
                    activityName = snapshot.activityName,
                    phone = snapshot.phone,
                    footerText = snapshot.footerText,
                    updatedAt = command.issuedAt.toEpochMilli(),
                ) == 1,
            ) { "Document identity ${command.identityId} was not updated." }
        }
    }

    private fun validatePaymentReceiptReplay(
        command: PreparePaymentReceiptCommand,
        persisted: IssuedDocumentEntity,
    ) {
        val snapshot = PaymentReceiptSnapshotCodec.decode(persisted.snapshotJson)
        val matches = persisted.id == command.documentId &&
            persisted.documentType == DocumentType.PAYMENT_RECEIPT.name &&
            persisted.debtId == command.debtId.value &&
            persisted.ledgerEntryId == command.paymentId.value &&
            persisted.identityId == command.identityId &&
            persisted.issuedAt == command.issuedAt.toEpochMilli() &&
            snapshot.issueZoneId == command.issueZoneId &&
            snapshot.identity == DocumentIdentitySnapshot(
                displayName = command.issuerDisplayName.trim(),
                activityName = command.issuerActivityName.normalizedOptional(),
                phone = command.issuerPhone.normalizedOptional(),
                footerText = command.footerText.normalizedOptional(),
            )
        if (!matches) {
            throw CommandConflictException(
                "Document command ID was reused with different receipt data.",
            )
        }
    }

    private fun IssuedDocumentEntity.toRecord(): IssuedDocumentRecord {
        val snapshot = PaymentReceiptSnapshotCodec.decode(snapshotJson)
        check(snapshotVersion == snapshot.version) { "Document snapshot version is corrupt." }
        check(personId == snapshot.personId.value) { "Document person metadata is corrupt." }
        check(personNameSnapshot == snapshot.personName) { "Document person snapshot is corrupt." }
        check(amountMinor == snapshot.paymentAmount.minorUnits) {
            "Document amount metadata is corrupt."
        }
        check(currencyCode == snapshot.paymentAmount.currency.value) {
            "Document currency metadata is corrupt."
        }
        return IssuedDocumentRecord(
            id = id,
            commandId = commandId,
            type = DocumentType.valueOf(documentType),
            status = DocumentStatus.valueOf(status),
            documentNumber = documentNumber,
            debtId = DebtId(debtId),
            ledgerEntryId = LedgerEntryId(ledgerEntryId),
            identityId = identityId,
            issuedAt = Instant.ofEpochMilli(issuedAt),
            snapshot = snapshot,
            pdfRelativePath = pdfRelativePath,
            pdfSha256 = pdfSha256,
            pageCount = pageCount,
            failureCode = failureCode,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun DocumentIdentityEntity.toRecord(): DocumentIdentityRecord =
        DocumentIdentityRecord(
            id = id,
            displayName = displayName,
            activityName = activityName,
            phone = phone,
            footerText = footerText,
            isDefault = isDefault,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )

    private fun String?.normalizedOptional(): String? = this?.trim()?.ifEmpty { null }

    private fun toDueScheduleAuditEvent(entity: AuditEventEntity): DueScheduleAuditEvent {
        check(entity.aggregateType == AuditAggregateType.DEBT.name) {
            "Unsupported audit aggregate type: ${entity.aggregateType}"
        }
        check(entity.eventType == AuditEventType.DUE_SCHEDULE_CHANGED.name) {
            "Unsupported audit event type: ${entity.eventType}"
        }
        check(entity.actor == AuditActor.LOCAL_USER.name) {
            "Unsupported audit actor: ${entity.actor}"
        }
        check(entity.reason == null) { "Due schedule event contains an unexpected reason." }
        return DueScheduleAuditEvent(
            id = entity.id,
            commandId = entity.commandId,
            debtId = DebtId(entity.aggregateId),
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
            before = DueScheduleAuditCodec.decode(requireNotNull(entity.beforeSnapshot)),
            after = DueScheduleAuditCodec.decode(requireNotNull(entity.afterSnapshot)),
        )
    }

    private suspend fun updateReminderStatus(
        reminderId: String,
        status: ReminderStatus,
        failureCode: String?,
        deliveredAt: Instant?,
        updatedAt: Instant,
    ) {
        check(
            reminderDao.updateStatus(
                id = reminderId,
                status = status.name,
                failureCode = failureCode,
                deliveredAt = deliveredAt?.toEpochMilli(),
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Reminder $reminderId was not found." }
    }

    private fun ReminderEntity.toRecord(): ReminderRecord {
        check(subjectType == ReminderSubjectType.DEBT.name) {
            "Unsupported reminder subject type: $subjectType"
        }
        val type = ReminderType.valueOf(reminderType)
        val schedule = ReminderScheduleType.valueOf(scheduleType)
        check(
            (type == ReminderType.DUE_DATE && schedule == ReminderScheduleType.WORK) ||
                (type == ReminderType.STRONG_ALARM && schedule == ReminderScheduleType.EXACT_ALARM),
        ) { "Unsupported reminder type/schedule combination: $reminderType/$scheduleType" }
        return ReminderRecord(
            id = id,
            debtId = DebtId(subjectId),
            triggerAt = Instant.ofEpochMilli(triggerAt),
            zoneId = ZoneId.of(zoneId),
            status = ReminderStatus.valueOf(status),
            type = type,
            scheduleType = schedule,
            platformRequestCode = platformRequestCode,
            lastFailureCode = lastFailureCode,
            deliveredAt = deliveredAt?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun ReminderEntity.activeDueRequestOrNull(): DueReminderRequest? =
        if (status == ReminderStatus.CANCELLED.name) {
            null
        } else {
            check(reminderType == ReminderType.DUE_DATE.name)
            DueReminderRequest(
                id = id,
                triggerAt = Instant.ofEpochMilli(triggerAt),
                zoneId = ZoneId.of(zoneId),
            )
        }

    private fun ReminderEntity.activeStrongAlarmRequestOrNull(): StrongAlarmRequest? =
        if (status == ReminderStatus.CANCELLED.name) {
            null
        } else {
            check(reminderType == ReminderType.STRONG_ALARM.name)
            StrongAlarmRequest(
                id = id,
                triggerAt = Instant.ofEpochMilli(triggerAt),
                zoneId = ZoneId.of(zoneId),
            )
        }

    private fun DueReminderRequest.toEntity(
        debtId: DebtId,
        createdAt: Instant,
    ): ReminderEntity = ReminderEntity(
        id = id,
        subjectType = ReminderSubjectType.DEBT.name,
        subjectId = debtId.value,
        reminderType = ReminderType.DUE_DATE.name,
        scheduleType = ReminderScheduleType.WORK.name,
        triggerAt = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
        repeatRule = null,
        status = ReminderStatus.SCHEDULED.name,
        platformRequestCode = null,
        lastFailureCode = null,
        deliveredAt = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = createdAt.toEpochMilli(),
    )

    private fun StrongAlarmRequest.toEntity(
        debtId: DebtId,
        createdAt: Instant,
    ): ReminderEntity = ReminderEntity(
        id = id,
        subjectType = ReminderSubjectType.DEBT.name,
        subjectId = debtId.value,
        reminderType = ReminderType.STRONG_ALARM.name,
        scheduleType = ReminderScheduleType.EXACT_ALARM.name,
        triggerAt = triggerAt.toEpochMilli(),
        zoneId = zoneId.id,
        repeatRule = null,
        status = ReminderStatus.SCHEDULED.name,
        platformRequestCode = null,
        lastFailureCode = null,
        deliveredAt = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = createdAt.toEpochMilli(),
    )

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

    private fun CreatePersonWithDebtCommand.toDebtCreation() = DebtCreation(
        personId = personId,
        debtId = debtId,
        direction = direction,
        originalAmount = originalAmount,
        openedAt = openedAt,
        createdAt = createdAt,
        dueDate = dueDate,
        description = description?.trim(),
        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
        strongAlarm = strongAlarm,
    )

    private fun CreateDebtForExistingPersonCommand.toDebtCreation() = DebtCreation(
        personId = personId,
        debtId = debtId,
        direction = direction,
        originalAmount = originalAmount,
        openedAt = openedAt,
        createdAt = createdAt,
        dueDate = dueDate,
        description = description?.trim(),
        debtNotes = debtNotes?.trim(),
        dueReminder = dueReminder,
        strongAlarm = strongAlarm,
    )

    private data class DebtCreation(
        val personId: PersonId,
        val debtId: DebtId,
        val direction: DebtDirection,
        val originalAmount: Money,
        val openedAt: Instant,
        val createdAt: Instant,
        val dueDate: LocalDate?,
        val description: String?,
        val debtNotes: String?,
        val dueReminder: DueReminderRequest?,
        val strongAlarm: StrongAlarmRequest?,
    )

    private enum class LedgerKind {
        PAYMENT,
        PAYMENT_REVERSAL,
    }

    private enum class ReminderSubjectType { DEBT }

    private enum class AuditAggregateType { DEBT }

    private enum class AuditEventType { DUE_SCHEDULE_CHANGED }

    private enum class AuditActor { LOCAL_USER }

    private companion object {
        const val FAILURE_NOTIFICATIONS_DISABLED = "NOTIFICATIONS_DISABLED"
        const val PAYMENT_RECEIPT_SNAPSHOT_VERSION = 1
    }
}
