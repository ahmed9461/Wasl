package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AttachmentRecord
import com.wasl.app.data.AttachmentStore
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.WaslRepository
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import com.wasl.domain.PersonId
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonBalanceGroup(
    val currency: CurrencyCode,
    val direction: DebtDirection,
    val originalAmount: Money,
    val paidAmount: Money,
    val balance: Money,
    val accountCount: Int,
)

enum class PersonTimelineEventType {
    ACCOUNT_OPENED,
    PAYMENT_RECORDED,
    PAYMENT_REVERSED,
    PROMISE_CREATED,
    PROMISE_RESOLVED,
    CLAIM_CREATED,
    CLAIM_RESOLVED,
    DOCUMENT_ISSUED,
    ATTACHMENT_ADDED,
}

data class PersonTimelineEvent(
    val id: String,
    val debtId: DebtId,
    val occurredAt: Instant,
    val type: PersonTimelineEventType,
    val title: String,
    val detail: String? = null,
    val currency: CurrencyCode,
    val direction: DebtDirection,
)

data class PersonAccountExtras(
    val debtId: DebtId,
    val promises: List<PaymentPromiseRecord> = emptyList(),
    val claims: List<PaymentClaimRecord> = emptyList(),
    val attachments: List<AttachmentRecord> = emptyList(),
)

data class PersonTimelineUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val person: PersonRecord? = null,
    val accounts: List<AccountOverview> = emptyList(),
    val balanceGroups: List<PersonBalanceGroup> = emptyList(),
    val timeline: List<PersonTimelineEvent> = emptyList(),
)

internal object PersonTimelineBuilder {
    fun balanceGroups(accounts: List<AccountOverview>): List<PersonBalanceGroup> = accounts
        .groupBy { it.ledger.header.originalAmount.currency to it.ledger.header.direction }
        .map { (key, groupedAccounts) ->
            val (currency, direction) = key
            PersonBalanceGroup(
                currency = currency,
                direction = direction,
                originalAmount = Money(
                    groupedAccounts.sumOf { it.ledger.header.originalAmount.minorUnits },
                    currency,
                ),
                paidAmount = Money(
                    groupedAccounts.sumOf { it.ledger.paidAmount.minorUnits },
                    currency,
                ),
                balance = Money(
                    groupedAccounts.sumOf { it.ledger.balance.minorUnits },
                    currency,
                ),
                accountCount = groupedAccounts.size,
            )
        }
        .sortedWith(compareBy<PersonBalanceGroup> { it.currency.value }.thenBy { it.direction.name })

    fun timeline(
        accounts: List<AccountOverview>,
        extras: List<PersonAccountExtras>,
    ): List<PersonTimelineEvent> {
        val extrasByDebt = extras.associateBy { it.debtId }
        return buildList {
            accounts.forEach { account ->
                val debtId = account.ledger.header.id
                val currency = account.ledger.header.originalAmount.currency
                val direction = account.ledger.header.direction
                add(
                    PersonTimelineEvent(
                        id = "opened:${debtId.value}",
                        debtId = debtId,
                        occurredAt = account.ledger.header.openedAt,
                        type = PersonTimelineEventType.ACCOUNT_OPENED,
                        title = "فتح الحساب",
                        detail = account.ledger.header.description,
                        currency = currency,
                        direction = direction,
                    ),
                )
                account.ledger.entries.forEach { entry ->
                    when (entry) {
                        is PaymentRecorded -> add(
                            PersonTimelineEvent(
                                id = "ledger:${entry.id.value}",
                                debtId = debtId,
                                occurredAt = entry.recordedAt,
                                type = PersonTimelineEventType.PAYMENT_RECORDED,
                                title = "تسجيل دفعة",
                                detail = entry.note,
                                currency = currency,
                                direction = direction,
                            ),
                        )
                        is PaymentReversed -> add(
                            PersonTimelineEvent(
                                id = "ledger:${entry.id.value}",
                                debtId = debtId,
                                occurredAt = entry.recordedAt,
                                type = PersonTimelineEventType.PAYMENT_REVERSED,
                                title = "عكس دفعة",
                                detail = entry.reason,
                                currency = currency,
                                direction = direction,
                            ),
                        )
                    }
                }
                account.issuedDocuments.forEach { document ->
                    add(
                        PersonTimelineEvent(
                            id = "document:${document.id}",
                            debtId = debtId,
                            occurredAt = document.issuedAt,
                            type = PersonTimelineEventType.DOCUMENT_ISSUED,
                            title = "إصدار مستند ${document.documentNumber}",
                            detail = document.type.name,
                            currency = currency,
                            direction = direction,
                        ),
                    )
                }
                val accountExtras = extrasByDebt[debtId] ?: PersonAccountExtras(debtId)
                accountExtras.promises.forEach { promise ->
                    add(
                        PersonTimelineEvent(
                            id = "promise-created:${promise.id}",
                            debtId = debtId,
                            occurredAt = promise.createdAt,
                            type = PersonTimelineEventType.PROMISE_CREATED,
                            title = "تسجيل وعد سداد",
                            detail = promise.note,
                            currency = currency,
                            direction = direction,
                        ),
                    )
                    promise.resolvedAt?.let { resolvedAt ->
                        add(
                            PersonTimelineEvent(
                                id = "promise-resolved:${promise.id}",
                                debtId = debtId,
                                occurredAt = resolvedAt,
                                type = PersonTimelineEventType.PROMISE_RESOLVED,
                                title = "إنهاء وعد سداد",
                                detail = promise.resolutionNote ?: promise.status.name,
                                currency = currency,
                                direction = direction,
                            ),
                        )
                    }
                }
                accountExtras.claims.forEach { claim ->
                    add(
                        PersonTimelineEvent(
                            id = "claim-created:${claim.id}",
                            debtId = debtId,
                            occurredAt = claim.claimedAt,
                            type = PersonTimelineEventType.CLAIM_CREATED,
                            title = "مطالبة بالسداد",
                            detail = claim.note,
                            currency = currency,
                            direction = direction,
                        ),
                    )
                    claim.resolvedAt?.let { resolvedAt ->
                        add(
                            PersonTimelineEvent(
                                id = "claim-resolved:${claim.id}",
                                debtId = debtId,
                                occurredAt = resolvedAt,
                                type = PersonTimelineEventType.CLAIM_RESOLVED,
                                title = "إنهاء مطالبة السداد",
                                detail = claim.resolutionNote ?: claim.status.name,
                                currency = currency,
                                direction = direction,
                            ),
                        )
                    }
                }
                accountExtras.attachments.forEach { attachment ->
                    add(
                        PersonTimelineEvent(
                            id = "attachment:${attachment.id}",
                            debtId = debtId,
                            occurredAt = attachment.createdAt,
                            type = PersonTimelineEventType.ATTACHMENT_ADDED,
                            title = "إضافة مرفق",
                            detail = attachment.displayName,
                            currency = currency,
                            direction = direction,
                        ),
                    )
                }
            }
        }.sortedWith(
            compareByDescending<PersonTimelineEvent> { it.occurredAt }
                .thenByDescending { it.id },
        )
    }
}

class PersonTimelineViewModel(
    private val personId: PersonId,
    private val repository: WaslRepository,
    private val promiseStore: PaymentPromiseStore,
    private val claimStore: PaymentClaimStore,
    private val attachmentStore: AttachmentStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonTimelineUiState())
    val uiState: StateFlow<PersonTimelineUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        observe()
    }

    fun retryLoad() = observe()

    private fun observe() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        observationJob = viewModelScope.launch {
            repository.observeAccounts()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(isLoading = false, loadError = "تعذر قراءة حسابات الشخص.")
                    }
                }
                .collectLatest { allAccounts ->
                    val accounts = allAccounts.filter { it.person.id == personId }
                    if (accounts.isEmpty()) {
                        _uiState.value = PersonTimelineUiState(
                            isLoading = false,
                            loadError = "لم يعد هذا الشخص أو أي حساب تابع له متاحًا.",
                        )
                        return@collectLatest
                    }
                    val person = accounts.first().person
                    val extraFlows = accounts.map { account ->
                        val debtId = account.ledger.header.id
                        combine(
                            promiseStore.observePaymentPromises(debtId),
                            claimStore.observeClaims(debtId),
                            attachmentStore.observeForDebt(debtId),
                        ) { promises, claims, attachments ->
                            PersonAccountExtras(debtId, promises, claims, attachments)
                        }.catch { error ->
                            if (error is CancellationException) throw error
                            emit(PersonAccountExtras(debtId))
                        }
                    }
                    val combinedExtras = if (extraFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(extraFlows) { values -> values.toList() }
                    }
                    combinedExtras.collect { extras ->
                        _uiState.value = PersonTimelineUiState(
                            isLoading = false,
                            person = person,
                            accounts = accounts.sortedByDescending { it.ledger.header.openedAt },
                            balanceGroups = PersonTimelineBuilder.balanceGroups(accounts),
                            timeline = PersonTimelineBuilder.timeline(accounts, extras),
                        )
                    }
                }
        }
    }

    class Factory(
        private val personId: PersonId,
        private val repository: WaslRepository,
        private val promiseStore: PaymentPromiseStore,
        private val claimStore: PaymentClaimStore,
        private val attachmentStore: AttachmentStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PersonTimelineViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return PersonTimelineViewModel(
                personId = personId,
                repository = repository,
                promiseStore = promiseStore,
                claimStore = claimStore,
                attachmentStore = attachmentStore,
            ) as T
        }
    }
}
