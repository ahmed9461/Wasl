package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimFollowUpResolver
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.ResolvePaymentClaimCommand
import com.wasl.domain.DebtId
import java.time.Clock
import java.time.LocalDate
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

data class PaymentClaimForm(
    val followUpKind: PaymentClaimFollowUpKind = PaymentClaimFollowUpKind.TODAY,
    val customDate: LocalDate? = null,
    val note: String = "",
)

data class PaymentClaimResolutionForm(
    val claimId: String,
    val status: PaymentClaimStatus,
    val note: String = "",
)

data class PaymentClaimUiState(
    val claims: List<PaymentClaimRecord> = emptyList(),
    val loadError: String? = null,
    val isCreateDialogOpen: Boolean = false,
    val form: PaymentClaimForm = PaymentClaimForm(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val resolution: PaymentClaimResolutionForm? = null,
    val isResolving: Boolean = false,
    val resolutionError: String? = null,
    val notice: String? = null,
)

class PaymentClaimViewModel(
    private val debtId: DebtId,
    private val store: PaymentClaimStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentClaimUiState())
    val uiState: StateFlow<PaymentClaimUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var pendingCreate: CreatePaymentClaimCommand? = null
    private var pendingResolution: ResolvePaymentClaimCommand? = null

    init {
        observeClaims()
    }

    fun retryLoad() = observeClaims()

    fun openCreate() {
        pendingCreate = null
        _uiState.update {
            it.copy(
                isCreateDialogOpen = true,
                form = PaymentClaimForm(),
                saveError = null,
                notice = null,
            )
        }
    }

    fun dismissCreate() {
        if (_uiState.value.isSaving) return
        pendingCreate = null
        _uiState.update {
            it.copy(
                isCreateDialogOpen = false,
                form = PaymentClaimForm(),
                saveError = null,
            )
        }
    }

    fun updateKind(kind: PaymentClaimFollowUpKind) {
        pendingCreate = null
        _uiState.update {
            it.copy(
                form = it.form.copy(
                    followUpKind = kind,
                    customDate = if (kind == PaymentClaimFollowUpKind.CUSTOM) it.form.customDate else null,
                ),
                saveError = null,
            )
        }
    }

    fun updateCustomDate(date: LocalDate?) {
        pendingCreate = null
        _uiState.update {
            it.copy(form = it.form.copy(customDate = date), saveError = null)
        }
    }

    fun updateNote(note: String) {
        pendingCreate = null
        _uiState.update { it.copy(form = it.form.copy(note = note), saveError = null) }
    }

    fun confirmCreate() {
        val state = _uiState.value
        if (state.isSaving) return
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, zoneIdProvider())
        val followUpDate = try {
            PaymentClaimFollowUpResolver.resolve(
                kind = state.form.followUpKind,
                today = today,
                customDate = state.form.customDate,
            )
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(saveError = "اختر تاريخ متابعة صحيحًا وغير ماضٍ.") }
            return
        }
        val command = pendingCreate ?: CreatePaymentClaimCommand(
            commandId = idFactory(),
            claimId = idFactory(),
            debtId = debtId,
            claimedAt = now,
            followUpKind = state.form.followUpKind,
            followUpDate = followUpDate,
            note = state.form.note.trim().ifEmpty { null },
            createdAt = now,
        ).also { pendingCreate = it }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                store.createClaim(command)
                pendingCreate = null
                _uiState.update {
                    it.copy(
                        isCreateDialogOpen = false,
                        form = PaymentClaimForm(),
                        isSaving = false,
                        saveError = null,
                        notice = "تم تسجيل المطالبة وحفظ موعد المتابعة دون تغيير الرصيد.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = error.message ?: "تعذر تسجيل المطالبة. حاول مرة أخرى.",
                    )
                }
            }
        }
    }

    fun openResolution(claimId: String, status: PaymentClaimStatus) {
        if (status == PaymentClaimStatus.ACTIVE) return
        pendingResolution = null
        _uiState.update {
            it.copy(
                resolution = PaymentClaimResolutionForm(claimId = claimId, status = status),
                resolutionError = null,
                notice = null,
            )
        }
    }

    fun dismissResolution() {
        if (_uiState.value.isResolving) return
        pendingResolution = null
        _uiState.update { it.copy(resolution = null, resolutionError = null) }
    }

    fun updateResolutionNote(note: String) {
        pendingResolution = null
        _uiState.update {
            it.copy(
                resolution = it.resolution?.copy(note = note),
                resolutionError = null,
            )
        }
    }

    fun confirmResolution() {
        val state = _uiState.value
        if (state.isResolving) return
        val form = state.resolution ?: return
        val now = clock.instant()
        val command = pendingResolution ?: ResolvePaymentClaimCommand(
            commandId = idFactory(),
            claimId = form.claimId,
            debtId = debtId,
            status = form.status,
            resolvedAt = now,
            note = form.note.trim().ifEmpty { null },
        ).also { pendingResolution = it }

        _uiState.update { it.copy(isResolving = true, resolutionError = null) }
        viewModelScope.launch {
            try {
                store.resolveClaim(command)
                pendingResolution = null
                _uiState.update {
                    it.copy(
                        resolution = null,
                        isResolving = false,
                        resolutionError = null,
                        notice = if (command.status == PaymentClaimStatus.RESOLVED) {
                            "تم حسم المطالبة مع الاحتفاظ بها في السجل."
                        } else {
                            "تم إلغاء المطالبة مع الاحتفاظ بها في السجل."
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        resolutionError = error.message ?: "تعذر حسم المطالبة. حاول مرة أخرى.",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun observeClaims() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            store.observeClaims(debtId)
                .catch { error ->
                    _uiState.update {
                        it.copy(loadError = error.message ?: "تعذر تحميل سجل المطالبات.")
                    }
                }
                .collect { claims ->
                    _uiState.update {
                        it.copy(
                            claims = claims.sortedByDescending(PaymentClaimRecord::createdAt),
                            loadError = null,
                        )
                    }
                }
        }
    }

    class Factory(
        private val debtId: DebtId,
        private val store: PaymentClaimStore,
        private val clock: Clock = Clock.systemUTC(),
        private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PaymentClaimViewModel(
            debtId = debtId,
            store = store,
            clock = clock,
            zoneIdProvider = zoneIdProvider,
        ) as T
    }
}
