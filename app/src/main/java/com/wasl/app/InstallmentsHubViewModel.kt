package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreateInstallmentPlanCommand
import com.wasl.app.data.InstallmentPlanItemInput
import com.wasl.app.data.InstallmentPlanRecord
import com.wasl.app.data.InstallmentPlanStore
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.ReviseInstallmentPlanCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.DebtId
import com.wasl.domain.InstallmentSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstallmentHubAccount(
    val account: AccountOverview,
    val activePlan: InstallmentPlanRecord?,
)

data class InstallmentEditorForm(
    val debtId: DebtId,
    val currentPlanId: String? = null,
    val count: String = "3",
    val firstDueDate: LocalDate? = null,
    val reason: String = "",
)

data class InstallmentsHubUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val accounts: List<InstallmentHubAccount> = emptyList(),
    val editor: InstallmentEditorForm? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val notice: String? = null,
)

private sealed interface PendingInstallmentWrite {
    data class Create(val command: CreateInstallmentPlanCommand) : PendingInstallmentWrite
    data class Revise(val command: ReviseInstallmentPlanCommand) : PendingInstallmentWrite
}

class InstallmentsHubViewModel(
    private val repository: WaslRepository,
    private val store: InstallmentPlanStore,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(InstallmentsHubUiState())
    val uiState: StateFlow<InstallmentsHubUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var pendingWrite: PendingInstallmentWrite? = null

    init {
        observe()
    }

    fun retryLoad() = observe()

    fun openEditor(debtId: DebtId) {
        val item = _uiState.value.accounts.firstOrNull { it.account.ledger.header.id == debtId } ?: return
        if (item.account.ledger.balance.isZero) return
        val activePlan = item.activePlan
        pendingWrite = null
        _uiState.update {
            it.copy(
                editor = InstallmentEditorForm(
                    debtId = debtId,
                    currentPlanId = activePlan?.id,
                    count = activePlan?.installments?.size?.toString() ?: "3",
                    firstDueDate = activePlan?.installments?.firstOrNull()?.dueDate
                        ?: item.account.ledger.header.dueDate
                        ?: currentDate(),
                    reason = "",
                ),
                saveError = null,
                notice = null,
            )
        }
    }

    fun dismissEditor() {
        if (_uiState.value.isSaving) return
        pendingWrite = null
        _uiState.update { it.copy(editor = null, saveError = null) }
    }

    fun updateCount(value: String) {
        pendingWrite = null
        _uiState.update {
            it.copy(
                editor = it.editor?.copy(count = value.filter(Char::isDigit)),
                saveError = null,
            )
        }
    }

    fun updateFirstDueDate(value: LocalDate?) {
        pendingWrite = null
        _uiState.update {
            it.copy(editor = it.editor?.copy(firstDueDate = value), saveError = null)
        }
    }

    fun updateReason(value: String) {
        pendingWrite = null
        _uiState.update {
            it.copy(editor = it.editor?.copy(reason = value), saveError = null)
        }
    }

    fun savePlan() {
        val state = _uiState.value
        if (state.isSaving) return
        val form = state.editor ?: return
        val hubAccount = state.accounts.firstOrNull {
            it.account.ledger.header.id == form.debtId
        } ?: run {
            _uiState.update { it.copy(saveError = "لم يعد الحساب موجودًا في القائمة.") }
            return
        }
        val account = hubAccount.account
        if (account.ledger.balance.isZero) {
            pendingWrite = null
            _uiState.update { it.copy(saveError = "الحساب مسدد ولا يحتاج خطة أقساط جديدة.") }
            return
        }
        val count = form.count.toIntOrNull()
        if (count == null || count !in 1..InstallmentSchedule.MAX_INSTALLMENTS) {
            _uiState.update {
                it.copy(saveError = "اختر عدد أقساط بين 1 و${InstallmentSchedule.MAX_INSTALLMENTS}.")
            }
            return
        }
        val firstDueDate = form.firstDueDate ?: run {
            _uiState.update { it.copy(saveError = "اختر تاريخ أول قسط.") }
            return
        }

        val schedule = try {
            InstallmentSchedule.equalMonthly(
                total = account.ledger.header.originalAmount,
                count = count,
                firstDueDate = firstDueDate,
            )
        } catch (_: IllegalArgumentException) {
            _uiState.update {
                it.copy(saveError = "لا يمكن توزيع المبلغ على هذا العدد من الأقساط.")
            }
            return
        }

        val write = pendingWrite ?: run {
            val changedAt = safeOperationTimestamp(hubAccount) ?: run {
                _uiState.update {
                    it.copy(saveError = "وقت الجهاز أقدم من آخر عملية محفوظة. صحح الوقت ثم أعد المحاولة.")
                }
                return
            }
            val newPlanId = idFactory()
            val items = schedule.map { item ->
                InstallmentPlanItemInput(
                    id = idFactory(),
                    sequenceNumber = item.sequenceNumber,
                    dueDate = item.dueDate,
                    amount = item.amount,
                )
            }
            if (hubAccount.activePlan == null) {
                PendingInstallmentWrite.Create(
                    CreateInstallmentPlanCommand(
                        commandId = idFactory(),
                        planId = newPlanId,
                        debtId = form.debtId,
                        installments = items,
                        createdAt = changedAt,
                    ),
                )
            } else {
                PendingInstallmentWrite.Revise(
                    ReviseInstallmentPlanCommand(
                        commandId = idFactory(),
                        planId = newPlanId,
                        debtId = form.debtId,
                        supersedesPlanId = requireNotNull(form.currentPlanId),
                        installments = items,
                        createdAt = changedAt,
                        reason = form.reason.trim().ifEmpty { null },
                    ),
                )
            }.also { pendingWrite = it }
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val saved = when (write) {
                    is PendingInstallmentWrite.Create -> store.createInstallmentPlan(write.command)
                    is PendingInstallmentWrite.Revise -> store.reviseInstallmentPlan(write.command)
                }
                pendingWrite = null
                _uiState.update {
                    it.copy(
                        editor = null,
                        isSaving = false,
                        saveError = null,
                        notice = if (saved.revisionNumber == 1) {
                            "تم إنشاء خطة الأقساط وحفظها مع الحساب."
                        } else {
                            "تم حفظ النسخة ${saved.revisionNumber} من خطة الأقساط مع الاحتفاظ بالتاريخ السابق."
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingWrite = null
                _uiState.update {
                    it.copy(isSaving = false, saveError = "لم تعد هذه الخطة صالحة للحساب الحالي.")
                }
            } catch (_: RecordNotFoundException) {
                pendingWrite = null
                _uiState.update {
                    it.copy(isSaving = false, saveError = "تعذر العثور على الحساب أو الخطة الحالية.")
                }
            } catch (_: CommandConflictException) {
                pendingWrite = null
                _uiState.update {
                    it.copy(isSaving = false, saveError = "تغيرت الخطة قبل الحفظ. حدّث البيانات وأعد المحاولة.")
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "تعذر تأكيد نتيجة الحفظ. أعد المحاولة بنفس البيانات للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun observe() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        observationJob = viewModelScope.launch {
            combine(
                repository.observeAccounts(),
                store.observeActiveInstallmentPlans(),
            ) { accounts, activePlans ->
                val plansByDebt = activePlans.associateBy { it.debtId }
                accounts.map { account ->
                    InstallmentHubAccount(
                        account = account,
                        activePlan = plansByDebt[account.ledger.header.id],
                    )
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(isLoading = false, loadError = "تعذر قراءة خطط الأقساط المحفوظة.")
                }
            }.collect { accounts ->
                _uiState.update {
                    it.copy(isLoading = false, loadError = null, accounts = accounts)
                }
            }
        }
    }

    private fun safeOperationTimestamp(item: InstallmentHubAccount): Instant? {
        val now = Instant.now(clock)
        val account = item.account
        if (now.isBefore(account.ledger.header.openedAt)) return null
        val lastLedgerAt = account.ledger.entries.maxOfOrNull { it.recordedAt }
        if (lastLedgerAt != null && now.isBefore(lastLedgerAt)) return null
        val planCreatedAt = item.activePlan?.createdAt
        if (planCreatedAt != null && now.isBefore(planCreatedAt)) return null
        return now
    }

    private fun currentDate(): LocalDate = Instant.now(clock).atZone(zoneIdProvider()).toLocalDate()

    class Factory(
        private val repository: WaslRepository,
        private val store: InstallmentPlanStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InstallmentsHubViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return InstallmentsHubViewModel(repository, store) as T
        }
    }
}
