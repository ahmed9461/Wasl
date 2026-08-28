package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.InstallmentPlanStore
import com.wasl.app.data.InstallmentRecord
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.UnavailableInstallmentPlanStore
import com.wasl.app.data.UnavailablePaymentClaimStore
import com.wasl.app.data.UnavailablePaymentPromiseStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.domain.DebtId
import com.wasl.domain.DueState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayItem(
    val account: AccountOverview,
    val dueState: DueState,
    val daysOverdue: Long,
) {
    init {
        require(dueState == DueState.DUE_TODAY || dueState == DueState.OVERDUE) {
            "Today items must be due today or overdue."
        }
        require(daysOverdue >= 0L) { "Days overdue cannot be negative." }
        require((dueState == DueState.OVERDUE) == (daysOverdue > 0L)) {
            "Days overdue must match the due state."
        }
    }
}

data class TodayPromiseItem(
    val account: AccountOverview,
    val promise: PaymentPromiseRecord,
    val daysOverdue: Long,
) {
    init {
        require(daysOverdue >= 0L) { "Promise days overdue cannot be negative." }
    }

    val isOverdue: Boolean
        get() = daysOverdue > 0L
}

data class TodayInstallmentItem(
    val account: AccountOverview,
    val installment: InstallmentRecord,
    val daysOverdue: Long,
) {
    init {
        require(installment.debtId == account.ledger.header.id) {
            "Today installment must belong to its account."
        }
        require(!installment.isPaid) { "Paid installments do not belong in Today." }
        require(daysOverdue >= 0L) { "Installment days overdue cannot be negative." }
    }

    val isOverdue: Boolean
        get() = daysOverdue > 0L
}

data class TodayClaimItem(
    val account: AccountOverview,
    val claim: PaymentClaimRecord,
    val daysOverdue: Long,
) {
    init {
        require(claim.debtId == account.ledger.header.id) {
            "Today payment claim must belong to its account."
        }
        require(claim.followUpDate != null) {
            "Today payment claim requires a resolved follow-up date."
        }
        require(daysOverdue >= 0L) { "Payment claim days overdue cannot be negative." }
    }

    val isOverdue: Boolean
        get() = daysOverdue > 0L
}

enum class TodayNotice {
    REMINDER_RECOVERY_REQUESTED,
    REMINDER_RECOVERY_FAILED,
}

data class TodayUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val items: List<TodayItem> = emptyList(),
    val promiseItems: List<TodayPromiseItem> = emptyList(),
    val installmentItems: List<TodayInstallmentItem> = emptyList(),
    val claimItems: List<TodayClaimItem> = emptyList(),
    val isRequestingReminderRecovery: Boolean = false,
    val notice: TodayNotice? = null,
) {
    val overdueItems: List<TodayItem>
        get() = items.filter { it.dueState == DueState.OVERDUE }

    val dueTodayItems: List<TodayItem>
        get() = items.filter { it.dueState == DueState.DUE_TODAY }

    val overduePromiseItems: List<TodayPromiseItem>
        get() = promiseItems.filter { it.isOverdue }

    val dueTodayPromiseItems: List<TodayPromiseItem>
        get() = promiseItems.filterNot { it.isOverdue }

    val overdueInstallmentItems: List<TodayInstallmentItem>
        get() = installmentItems.filter { it.isOverdue }

    val dueTodayInstallmentItems: List<TodayInstallmentItem>
        get() = installmentItems.filterNot { it.isOverdue }

    val overdueClaimItems: List<TodayClaimItem>
        get() = claimItems.filter { it.isOverdue }

    val dueTodayClaimItems: List<TodayClaimItem>
        get() = claimItems.filterNot { it.isOverdue }

    val totalAttentionItems: Int
        get() = items.size + promiseItems.size + installmentItems.size + claimItems.size
}

class TodayViewModel(
    private val repository: WaslRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val paymentPromiseStore: PaymentPromiseStore = UnavailablePaymentPromiseStore,
    private val paymentClaimStore: PaymentClaimStore =
        (repository as? PaymentClaimStore) ?: UnavailablePaymentClaimStore,
    private val installmentPlanStore: InstallmentPlanStore =
        (repository as? InstallmentPlanStore) ?: UnavailableInstallmentPlanStore,
) : ViewModel() {
    private val initialDate = currentLocalDate()
    private val _uiState = MutableStateFlow(TodayUiState(today = initialDate))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var observedDate: LocalDate? = null

    init {
        observeDate(initialDate)
    }

    fun refreshForCurrentDate() {
        val currentDate = currentLocalDate()
        if (currentDate != observedDate || observationJob?.isActive != true) {
            observeDate(currentDate)
        }
    }

    fun retryLoad() {
        observeDate(currentLocalDate())
    }

    fun retryReminderRecovery() {
        if (_uiState.value.isRequestingReminderRecovery) return
        _uiState.update { it.copy(isRequestingReminderRecovery = true, notice = null) }
        try {
            reminderScheduler.requestRecovery()
            _uiState.update {
                it.copy(
                    isRequestingReminderRecovery = false,
                    notice = TodayNotice.REMINDER_RECOVERY_REQUESTED,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isRequestingReminderRecovery = false,
                    notice = TodayNotice.REMINDER_RECOVERY_FAILED,
                )
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun observeDate(date: LocalDate) {
        observationJob?.cancel()
        observedDate = date
        _uiState.update {
            val dateChanged = it.today != date
            it.copy(
                today = date,
                isLoading = true,
                loadError = null,
                items = if (dateChanged) emptyList() else it.items,
                promiseItems = if (dateChanged) emptyList() else it.promiseItems,
                installmentItems = if (dateChanged) emptyList() else it.installmentItems,
                claimItems = if (dateChanged) emptyList() else it.claimItems,
            )
        }
        observationJob = viewModelScope.launch {
            combine(
                repository.observeDueAccounts(date),
                repository.observeAccounts(),
                paymentPromiseStore.observePendingPaymentPromises(date),
                installmentPlanStore.observeActionableInstallments(date),
                paymentClaimStore.observeOpenClaims(date),
            ) { dueAccounts, allAccounts, promises, installments, claims ->
                val accountsByDebtId = allAccounts.associateBy { it.ledger.header.id }
                TodayUiProjection(
                    items = buildTodayItems(dueAccounts, date),
                    promiseItems = buildTodayPromiseItems(promises, accountsByDebtId, date),
                    installmentItems = buildTodayInstallmentItems(
                        installments,
                        accountsByDebtId,
                        date,
                    ),
                    claimItems = buildTodayClaimItems(claims, accountsByDebtId, date),
                )
            }.catch { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "تعذر قراءة استحقاقات ووعود وأقساط ومطالبات اليوم المحفوظة.",
                    )
                }
            }.collect { projection ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = null,
                        items = projection.items,
                        promiseItems = projection.promiseItems,
                        installmentItems = projection.installmentItems,
                        claimItems = projection.claimItems,
                    )
                }
            }
        }
    }

    private fun currentLocalDate(): LocalDate =
        Instant.now(clock).atZone(zoneIdProvider()).toLocalDate()

    private fun buildTodayItems(
        accounts: List<AccountOverview>,
        date: LocalDate,
    ): List<TodayItem> = accounts.mapNotNull { account ->
        val dueDate = account.ledger.header.dueDate ?: return@mapNotNull null
        when (val dueState = account.ledger.dueState(date)) {
            DueState.DUE_TODAY -> TodayItem(account, dueState, 0L)
            DueState.OVERDUE -> TodayItem(
                account,
                dueState,
                ChronoUnit.DAYS.between(dueDate, date),
            )
            DueState.NO_DUE_DATE,
            DueState.UPCOMING,
            DueState.SETTLED -> null
        }
    }.sortedWith(
        compareBy<TodayItem> { it.account.ledger.header.dueDate }
            .thenBy { it.account.person.displayName }
            .thenBy { it.account.ledger.header.id.value },
    )

    private fun buildTodayPromiseItems(
        promises: List<PaymentPromiseRecord>,
        accountsByDebtId: Map<DebtId, AccountOverview>,
        date: LocalDate,
    ): List<TodayPromiseItem> = promises.mapNotNull { promise ->
        val account = accountsByDebtId[promise.debtId] ?: return@mapNotNull null
        if (promise.promisedDate.isAfter(date)) return@mapNotNull null
        TodayPromiseItem(
            account = account,
            promise = promise,
            daysOverdue = ChronoUnit.DAYS.between(promise.promisedDate, date),
        )
    }.sortedWith(
        compareBy<TodayPromiseItem> { it.promise.promisedDate }
            .thenBy { it.account.person.displayName }
            .thenBy { it.promise.id },
    )

    private fun buildTodayInstallmentItems(
        installments: List<InstallmentRecord>,
        accountsByDebtId: Map<DebtId, AccountOverview>,
        date: LocalDate,
    ): List<TodayInstallmentItem> = installments.mapNotNull { installment ->
        val account = accountsByDebtId[installment.debtId] ?: return@mapNotNull null
        if (installment.isPaid || installment.dueDate.isAfter(date)) return@mapNotNull null
        TodayInstallmentItem(
            account = account,
            installment = installment,
            daysOverdue = ChronoUnit.DAYS.between(installment.dueDate, date),
        )
    }.sortedWith(
        compareBy<TodayInstallmentItem> { it.installment.dueDate }
            .thenBy { it.account.person.displayName }
            .thenBy { it.installment.sequenceNumber },
    )

    private fun buildTodayClaimItems(
        claims: List<PaymentClaimRecord>,
        accountsByDebtId: Map<DebtId, AccountOverview>,
        date: LocalDate,
    ): List<TodayClaimItem> = claims.mapNotNull { claim ->
        val followUpDate = claim.followUpDate ?: return@mapNotNull null
        if (followUpDate.isAfter(date)) return@mapNotNull null
        val account = accountsByDebtId[claim.debtId] ?: return@mapNotNull null
        TodayClaimItem(
            account = account,
            claim = claim,
            daysOverdue = ChronoUnit.DAYS.between(followUpDate, date),
        )
    }.sortedWith(
        compareBy<TodayClaimItem> { it.claim.followUpDate }
            .thenBy { it.account.person.displayName }
            .thenBy { it.claim.id },
    )

    private data class TodayUiProjection(
        val items: List<TodayItem>,
        val promiseItems: List<TodayPromiseItem>,
        val installmentItems: List<TodayInstallmentItem>,
        val claimItems: List<TodayClaimItem>,
    )

    class Factory(
        private val repository: WaslRepository,
        private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
        private val clock: Clock = Clock.systemUTC(),
        private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
        private val paymentPromiseStore: PaymentPromiseStore = UnavailablePaymentPromiseStore,
        private val paymentClaimStore: PaymentClaimStore =
            (repository as? PaymentClaimStore) ?: UnavailablePaymentClaimStore,
        private val installmentPlanStore: InstallmentPlanStore =
            (repository as? InstallmentPlanStore) ?: UnavailableInstallmentPlanStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return TodayViewModel(
                repository = repository,
                clock = clock,
                zoneIdProvider = zoneIdProvider,
                reminderScheduler = reminderScheduler,
                paymentPromiseStore = paymentPromiseStore,
                paymentClaimStore = paymentClaimStore,
                installmentPlanStore = installmentPlanStore,
            ) as T
        }
    }
}
