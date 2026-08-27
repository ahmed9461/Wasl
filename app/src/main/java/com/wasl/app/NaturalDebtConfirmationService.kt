package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.WaslRepository
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first

sealed interface NaturalDebtConfirmationResult {
    data class Saved(
        val account: AccountOverview,
        val promiseCreated: Boolean,
        val warning: String? = null,
    ) : NaturalDebtConfirmationResult

    data class InvalidDraft(
        val missingFields: Set<NaturalDraftField>,
    ) : NaturalDebtConfirmationResult

    data class AmbiguousPerson(
        val personName: String,
        val matchingPersonIds: List<PersonId>,
    ) : NaturalDebtConfirmationResult
}

class NaturalDebtConfirmationService(
    private val repository: WaslRepository,
    private val paymentPromiseStore: PaymentPromiseStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun confirmAndSave(draft: NaturalEntryDraft): NaturalDebtConfirmationResult {
        if (!draft.canPreviewAsDebt) {
            return NaturalDebtConfirmationResult.InvalidDraft(draft.missingRequiredFields)
        }
        val personName = requireNotNull(draft.personName).trim()
        val currency = requireNotNull(draft.currency)
        val direction = requireNotNull(draft.direction)
        val amountMinorUnits = requireNotNull(draft.amountMinorUnits)
        val entryDate = requireNotNull(draft.entryDate)
        val exactPeople = repository.observePeople(personName, limit = 20)
            .first()
            .filter { normalizeName(it.displayName) == normalizeName(personName) }

        if (exactPeople.size > 1) {
            return NaturalDebtConfirmationResult.AmbiguousPerson(
                personName = personName,
                matchingPersonIds = exactPeople.map(PersonRecord::id),
            )
        }

        val now = clock.instant()
        val zoneId = zoneIdProvider()
        val openedAt = entryDate
            .atTime(now.atZone(zoneId).toLocalTime())
            .atZone(zoneId)
            .toInstant()
        require(!openedAt.isAfter(now)) {
            "Natural debt entry date cannot be in the future."
        }
        val debtId = DebtId(newId())
        val originalAmount = Money(amountMinorUnits, currency)
        val sourceNote = "الإدخال الطبيعي الأصلي: ${draft.sourceText.trim()}"

        val account = exactPeople.singleOrNull()?.let { person ->
            repository.createDebtForExistingPerson(
                CreateDebtForExistingPersonCommand(
                    personId = person.id,
                    debtId = debtId,
                    direction = direction,
                    originalAmount = originalAmount,
                    openedAt = openedAt,
                    createdAt = now,
                    debtNotes = sourceNote,
                ),
            )
        } ?: repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId(newId()),
                debtId = debtId,
                personName = personName,
                direction = direction,
                originalAmount = originalAmount,
                openedAt = openedAt,
                createdAt = now,
                debtNotes = sourceNote,
            ),
        )

        val promisedDate = draft.promisedDate
        if (promisedDate == null) {
            return NaturalDebtConfirmationResult.Saved(
                account = account,
                promiseCreated = false,
            )
        }

        return runCatching {
            paymentPromiseStore.createPaymentPromise(
                CreatePaymentPromiseCommand(
                    commandId = "natural-promise-create:${newId()}",
                    promiseId = newId(),
                    debtId = debtId,
                    promisedDate = promisedDate,
                    note = "مستخرج من الإدخال الطبيعي بعد تأكيد المستخدم.",
                    createdAt = now,
                ),
            )
        }.fold(
            onSuccess = {
                NaturalDebtConfirmationResult.Saved(
                    account = account,
                    promiseCreated = true,
                )
            },
            onFailure = {
                NaturalDebtConfirmationResult.Saved(
                    account = account,
                    promiseCreated = false,
                    warning = "تم حفظ الحساب، لكن تعذر حفظ الوعد المستخرج. افتح الحساب وأضف الوعد يدويًا.",
                )
            },
        )
    }

    private fun normalizeName(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
