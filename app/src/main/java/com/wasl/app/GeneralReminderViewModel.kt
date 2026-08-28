package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.UnavailableGeneralReminderStore
import com.wasl.app.data.UpsertGeneralReminderCommand
import com.wasl.app.reminder.GeneralReminderService
import com.wasl.domain.DebtId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GeneralReminderUiFrequency {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class GeneralReminderForm(
    val date: LocalDate? = null,
    val time: LocalTime = LocalTime.of(9, 0),
    val frequency: GeneralReminderUiFrequency = GeneralReminderUiFrequency.ONCE,
)

data class GeneralReminderUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val reminder: GeneralReminderRecord? = null,
    val isDialogOpen: Boolean = false,
    val form: GeneralReminderForm = GeneralReminderForm(),
    val isSaving: Boolean = false,
    val mutationError: String? = null,
    val platformSyncPending: Boolean = false,
)

class GeneralReminderViewModel(
    private val store: GeneralReminderStore,
    private val service: GeneralReminderService?,
    private val debtId: DebtId,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(GeneralReminderUiState())
    val uiState: StateFlow<GeneralReminderUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeReminder()
    }

    fun retryLoad() {
        observeReminder()
    }

    fun openDialog() {
        if (_uiState.value.isSaving) return
        val zone = zoneIdProvider()
        val existing = _uiState.value.reminder
        val form = existing
            ?.takeIf { it.status != ReminderStatus.CANCELLED }
            ?.let { reminder ->
                val local = reminder.triggerAt.atZone(reminder.zoneId)
                GeneralReminderForm(
                    date = local.toLocalDate(),
                    time = local.toLocalTime().withSecond(0).withNano(0),
                    frequency = reminder.repeatRule.toUiFrequency(),
                )
            }
            ?: GeneralReminderForm(
                date = Instant.now(clock).atZone(zone).toLocalDate().plusDays(1),
                time = LocalTime.of(9, 0),
            )
        _uiState.update {
            it.copy(
                isDialogOpen = true,
                form = form,
                mutationError = null,
                platformSyncPending = false,
            )
        }
    }

    fun dismissDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                isDialogOpen = false,
                mutationError = null,
            )
        }
    }

    fun updateDate(value: LocalDate?) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                form = it.form.copy(date = value),
                mutationError = null,
            )
        }
    }

    fun updateTime(value: LocalTime) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                form = it.form.copy(time = value.withSecond(0).withNano(0)),
                mutationError = null,
            )
        }
    }

    fun updateFrequency(value: GeneralReminderUiFrequency) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                form = it.form.copy(frequency = value),
                mutationError = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val service = service ?: run {
            _uiState.update { it.copy(mutationError = "التذكير العام غير متاح في هذه الجلسة.") }
            return
        }
        val date = state.form.date ?: run {
            _uiState.update { it.copy(mutationError = "اختر تاريخ التذكير.") }
            return
        }
        val zone = zoneIdProvider()
        val now = Instant.now(clock)
        val triggerAt = LocalDateTime.of(date, state.form.time)
            .atZone(zone)
            .toInstant()
        if (!triggerAt.isAfter(now)) {
            _uiState.update { it.copy(mutationError = "اختر وقتًا قادمًا للتذكير.") }
            return
        }
        val repeatRule = when (state.form.frequency) {
            GeneralReminderUiFrequency.ONCE -> null
            GeneralReminderUiFrequency.DAILY ->
                GeneralReminderRepeatRule(GeneralReminderFrequency.DAILY)
            GeneralReminderUiFrequency.WEEKLY ->
                GeneralReminderRepeatRule(GeneralReminderFrequency.WEEKLY)
            GeneralReminderUiFrequency.MONTHLY ->
                GeneralReminderRepeatRule.forTrigger(
                    frequency = GeneralReminderFrequency.MONTHLY,
                    triggerAt = triggerAt,
                    zoneId = zone,
                )
        }
        val reminderId = state.reminder?.id ?: idFactory()
        val command = UpsertGeneralReminderCommand(
            reminderId = reminderId,
            debtId = debtId,
            triggerAt = triggerAt,
            zoneId = zone,
            repeatRule = repeatRule,
            updatedAt = now,
        )
        _uiState.update { it.copy(isSaving = true, mutationError = null) }
        viewModelScope.launch {
            try {
                val result = service.save(command)
                _uiState.update {
                    it.copy(
                        isDialogOpen = false,
                        isSaving = false,
                        mutationError = null,
                        platformSyncPending = result.platformSyncPending,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mutationError = "لم يعد هذا التذكير صالحًا. راجع الموعد ثم أعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mutationError = "تعذر تأكيد حفظ التذكير. أعد المحاولة للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun cancel() {
        val state = _uiState.value
        if (state.isSaving) return
        val reminder = state.reminder ?: return
        val service = service ?: run {
            _uiState.update { it.copy(mutationError = "التذكير العام غير متاح في هذه الجلسة.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, mutationError = null) }
        viewModelScope.launch {
            try {
                val result = service.cancel(reminder.id)
                _uiState.update {
                    it.copy(
                        isDialogOpen = false,
                        isSaving = false,
                        mutationError = null,
                        platformSyncPending = result.platformSyncPending,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mutationError = "تعذر إلغاء التذكير بأمان. أعد المحاولة.",
                    )
                }
            }
        }
    }

    fun clearPlatformSyncNotice() {
        _uiState.update { it.copy(platformSyncPending = false) }
    }

    private fun observeReminder() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        observationJob = viewModelScope.launch {
            store.observeReminderForDebt(debtId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر قراءة تذكير المتابعة المحفوظ.",
                        )
                    }
                }
                .collect { reminder ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            reminder = reminder,
                        )
                    }
                }
        }
    }

    class Factory(
        private val store: GeneralReminderStore = UnavailableGeneralReminderStore,
        private val service: GeneralReminderService? = null,
        private val debtId: DebtId,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GeneralReminderViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return GeneralReminderViewModel(
                store = store,
                service = service,
                debtId = debtId,
            ) as T
        }
    }
}

private fun GeneralReminderRepeatRule?.toUiFrequency(): GeneralReminderUiFrequency = when (this?.frequency) {
    null -> GeneralReminderUiFrequency.ONCE
    GeneralReminderFrequency.DAILY -> GeneralReminderUiFrequency.DAILY
    GeneralReminderFrequency.WEEKLY -> GeneralReminderUiFrequency.WEEKLY
    GeneralReminderFrequency.MONTHLY -> GeneralReminderUiFrequency.MONTHLY
}
