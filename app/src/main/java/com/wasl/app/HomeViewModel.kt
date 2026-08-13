package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.reminder.ReminderTime
import com.wasl.domain.BalanceSummary
import com.wasl.domain.BalanceSummaryCalculator
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateDebtForm(
    val personName: String = "",
    val amount: String = "",
    val currency: CurrencyCode = CurrencyCode.YER,
    val direction: DebtDirection = DebtDirection.RECEIVABLE,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val accounts: List<AccountOverview> = emptyList(),
    val balanceSummary: BalanceSummary = BalanceSummary(
        receivableByCurrency = emptyMap(),
        payableByCurrency = emptyMap(),
    ),
    val isCreateDialogOpen: Boolean = false,
    val createForm: CreateDebtForm = CreateDebtForm(),
    val isSaving: Boolean = false,
    val formError: String? = null,
    val successMessage: String? = null,
)

class HomeViewModel(
    private val repository: WaslRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var pendingCreateIdentity: PendingCreateIdentity? = null

    init {
        viewModelScope.launch {
            repository.observeAccounts()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            loadError = "تعذر قراءة الحسابات المحفوظة.",
                        )
                    }
                }
                .collect { accounts ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            loadError = null,
                            accounts = accounts,
                            balanceSummary = BalanceSummaryCalculator.calculate(
                                accounts.map { it.ledger },
                            ),
                        )
                    }
                }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogOpen = true,
                formError = null,
                successMessage = null,
            )
        }
    }

    fun dismissCreateDialog() {
        if (_uiState.value.isSaving) return
        pendingCreateIdentity = null
        _uiState.update {
            it.copy(
                isCreateDialogOpen = false,
                createForm = CreateDebtForm(),
                formError = null,
            )
        }
    }

    fun updatePersonName(value: String) = updateForm { copy(personName = value) }

    fun updateAmount(value: String) = updateForm { copy(amount = value) }

    fun updateCurrency(value: CurrencyCode) = updateForm { copy(currency = value) }

    fun updateDirection(value: DebtDirection) = updateForm { copy(direction = value) }

    fun updateDescription(value: String) = updateForm { copy(description = value) }

    fun updateDueDate(value: LocalDate?) = updateForm {
        copy(
            dueDate = value,
            remindOnDueDate = if (value == null) false else remindOnDueDate,
        )
    }

    fun updateRemindOnDueDate(value: Boolean) = updateForm {
        copy(remindOnDueDate = value && dueDate != null)
    }

    fun createPersonWithDebt() {
        val state = _uiState.value
        if (state.isSaving) return

        val form = state.createForm
        val personName = form.personName.trim()
        if (personName.isEmpty()) {
            _uiState.update { it.copy(formError = "اكتب اسم الشخص.") }
            return
        }

        val amount = try {
            MoneyInputParser.parse(form.amount, form.currency)
        } catch (_: IllegalArgumentException) {
            _uiState.update {
                it.copy(formError = "تحقق من المبلغ ودقة العملة المختارة.")
            }
            return
        }

        val zoneId = zoneIdProvider()
        val now = Instant.now(clock)
        val today = now.atZone(zoneId).toLocalDate()
        if (form.dueDate?.isBefore(today) == true) {
            _uiState.update { it.copy(formError = "اختر تاريخ استحقاق اليوم أو بعده.") }
            return
        }
        if (form.remindOnDueDate && form.dueDate == null) {
            _uiState.update { it.copy(formError = "اختر تاريخ الاستحقاق قبل تفعيل التذكير.") }
            return
        }

        val identity = pendingCreateIdentity ?: PendingCreateIdentity(
            personId = PersonId(idFactory()),
            debtId = DebtId(idFactory()),
            reminder = if (form.remindOnDueDate) {
                DueReminderRequest(
                    id = idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            },
            timestamp = now,
        ).also { pendingCreateIdentity = it }
        val command = CreatePersonWithDebtCommand(
            personId = identity.personId,
            debtId = identity.debtId,
            personName = personName,
            direction = form.direction,
            originalAmount = amount,
            openedAt = identity.timestamp,
            createdAt = identity.timestamp,
            dueDate = form.dueDate,
            description = form.description.trim().ifEmpty { null },
            dueReminder = identity.reminder,
        )

        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                val created = repository.createPersonWithDebt(command)
                val schedulingFailed = created.dueReminder?.let { dueReminder ->
                    runCatching { reminderScheduler.schedule(dueReminder) }.isFailure
                } ?: false
                pendingCreateIdentity = null
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isCreateDialogOpen = false,
                        createForm = CreateDebtForm(),
                        successMessage = if (schedulingFailed) {
                            "تم حفظ الحساب والتذكير، وستُعاد محاولة الجدولة تلقائيًا."
                        } else if (created.dueReminder != null) {
                            "تم حفظ الحساب وجدولة التذكير."
                        } else {
                            "تم حفظ الحساب والدين بنجاح."
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        formError = "لم يُحفظ الحساب. أعد المحاولة دون تغيير البيانات.",
                    )
                }
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun updateForm(transform: CreateDebtForm.() -> CreateDebtForm) {
        _uiState.update {
            it.copy(
                createForm = it.createForm.transform(),
                formError = null,
            )
        }
    }

    private data class PendingCreateIdentity(
        val personId: PersonId,
        val debtId: DebtId,
        val reminder: DueReminderRequest?,
        val timestamp: Instant,
    )

    class Factory(
        private val repository: WaslRepository,
        private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return HomeViewModel(
                repository = repository,
                reminderScheduler = reminderScheduler,
            ) as T
        }
    }
}
