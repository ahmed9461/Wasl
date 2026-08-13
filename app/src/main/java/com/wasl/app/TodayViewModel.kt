package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderScheduler
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

enum class TodayNotice {
    REMINDER_RECOVERY_REQUESTED,
    REMINDER_RECOVERY_FAILED,
}

data class TodayUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val items: List<TodayItem> = emptyList(),
    val isRequestingReminderRecovery: Boolean = false,
    val notice: TodayNotice? = null,
) {
    val overdueItems: List<TodayItem>
        get() = items.filter { it.dueState == DueState.OVERDUE }

    val dueTodayItems: List<TodayItem>
        get() = items.filter { it.dueState == DueState.DUE_TODAY }
}

class TodayViewModel(
    private val repository: WaslRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
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
        _uiState.update {
            it.copy(
                isRequestingReminderRecovery = true,
                notice = null,
            )
        }
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
            )
        }
        observationJob = viewModelScope.launch {
            repository.observeDueAccounts(date)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر قراءة مستحقات اليوم المحفوظة.",
                        )
                    }
                }
                .collect { accounts ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            items = buildTodayItems(accounts, date),
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
            DueState.DUE_TODAY -> TodayItem(
                account = account,
                dueState = dueState,
                daysOverdue = 0L,
            )

            DueState.OVERDUE -> TodayItem(
                account = account,
                dueState = dueState,
                daysOverdue = ChronoUnit.DAYS.between(dueDate, date),
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

    class Factory(
        private val repository: WaslRepository,
        private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
        private val clock: Clock = Clock.systemUTC(),
        private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
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
            ) as T
        }
    }
}
