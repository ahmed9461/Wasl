package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CreateDebtForExistingPersonCommand
import com.wasl.app.data.CreateGroupExpenseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.StrongAlarmRequest
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.reminder.ReminderTime
import com.wasl.domain.BalanceSummary
import com.wasl.domain.BalanceSummaryCalculator
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.GroupExpense
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.GroupExpenseShare
import com.wasl.domain.GroupExpenseShareId
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DebtPersonMode {
    NEW,
    EXISTING,
}

data class ExistingPersonSelection(
    val id: PersonId,
    val displayName: String,
)

data class CreateDebtForm(
    val personMode: DebtPersonMode = DebtPersonMode.NEW,
    val personName: String = "",
    val selectedPerson: ExistingPersonSelection? = null,
    val amount: String = "",
    val currency: CurrencyCode = CurrencyCode.YER,
    val direction: DebtDirection = DebtDirection.RECEIVABLE,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
    val strongAlarmEnabled: Boolean = false,
    val strongAlarmTime: LocalTime = ReminderTime.defaultDueTime,
)

enum class GroupExpenseEditorStep {
    EDIT,
    REVIEW,
}

data class GroupExpenseParticipantDraft(
    val person: ExistingPersonSelection,
    val amount: String = "",
)

data class GroupExpenseForm(
    val currency: CurrencyCode = CurrencyCode.YER,
    val direction: DebtDirection = DebtDirection.RECEIVABLE,
    val description: String = "",
    val notes: String = "",
    val participants: List<GroupExpenseParticipantDraft> = emptyList(),
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val accounts: List<AccountOverview> = emptyList(),
    val balanceSummary: BalanceSummary = BalanceSummary(
        receivableByCurrency = emptyMap(),
        payableByCurrency = emptyMap(),
    ),
    val isCreateTypePickerOpen: Boolean = false,
    val isCreateDialogOpen: Boolean = false,
    val createForm: CreateDebtForm = CreateDebtForm(),
    val isGroupExpenseDialogOpen: Boolean = false,
    val groupExpenseStep: GroupExpenseEditorStep = GroupExpenseEditorStep.EDIT,
    val groupExpenseForm: GroupExpenseForm = GroupExpenseForm(),
    val groupExpensePreview: GroupExpense? = null,
    val isSaving: Boolean = false,
    val formError: String? = null,
    val groupExpenseError: String? = null,
    val successMessage: String? = null,
    val peopleQuery: String = "",
    val selectablePeople: List<PersonRecord> = emptyList(),
    val isPeopleLoading: Boolean = true,
    val peopleLoadError: String? = null,
    val hasMorePeople: Boolean = false,
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
    private var pendingGroupExpenseCommand: CreateGroupExpenseCommand? = null
    private val peopleQuery = MutableStateFlow("")
    private val peopleRetry = MutableStateFlow(0)

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
        viewModelScope.launch {
            combine(peopleQuery, peopleRetry) { query, _ -> query }
                .collectLatest { query ->
                    _uiState.update {
                        it.copy(
                            isPeopleLoading = true,
                            peopleLoadError = null,
                        )
                    }
                    try {
                        repository.observePeople(query, PEOPLE_SELECTION_QUERY_LIMIT)
                            .collect { people ->
                                _uiState.update {
                                    it.copy(
                                        isPeopleLoading = false,
                                        peopleLoadError = null,
                                        selectablePeople = people.take(PEOPLE_SELECTION_LIMIT),
                                        hasMorePeople = people.size > PEOPLE_SELECTION_LIMIT,
                                    )
                                }
                            }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _uiState.update {
                            it.copy(
                                isPeopleLoading = false,
                                peopleLoadError = "تعذر قراءة الأشخاص المحفوظين.",
                                selectablePeople = emptyList(),
                                hasMorePeople = false,
                            )
                        }
                    }
                }
        }
    }

    fun openCreateTypePicker() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                isCreateTypePickerOpen = true,
                formError = null,
                groupExpenseError = null,
                successMessage = null,
            )
        }
    }

    fun dismissCreateTypePicker() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isCreateTypePickerOpen = false) }
    }

    fun openCreateDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                isCreateTypePickerOpen = false,
                isCreateDialogOpen = true,
                formError = null,
                successMessage = null,
            )
        }
    }

    fun openGroupExpenseDialog() {
        if (_uiState.value.isSaving) return
        pendingGroupExpenseCommand = null
        peopleQuery.value = ""
        _uiState.update {
            it.copy(
                isCreateTypePickerOpen = false,
                isGroupExpenseDialogOpen = true,
                groupExpenseStep = GroupExpenseEditorStep.EDIT,
                groupExpenseForm = GroupExpenseForm(),
                groupExpensePreview = null,
                groupExpenseError = null,
                peopleQuery = "",
                successMessage = null,
            )
        }
    }

    fun dismissGroupExpenseDialog() {
        if (_uiState.value.isSaving) return
        pendingGroupExpenseCommand = null
        peopleQuery.value = ""
        _uiState.update {
            it.copy(
                isGroupExpenseDialogOpen = false,
                groupExpenseStep = GroupExpenseEditorStep.EDIT,
                groupExpenseForm = GroupExpenseForm(),
                groupExpensePreview = null,
                groupExpenseError = null,
                peopleQuery = "",
            )
        }
    }

    fun dismissCreateDialog() {
        if (_uiState.value.isSaving) return
        pendingCreateIdentity = null
        peopleQuery.value = ""
        _uiState.update {
            it.copy(
                isCreateDialogOpen = false,
                createForm = CreateDebtForm(),
                formError = null,
                peopleQuery = "",
            )
        }
    }

    fun updatePersonMode(value: DebtPersonMode) {
        pendingCreateIdentity = null
        updateForm {
            copy(
                personMode = value,
                selectedPerson = if (value == DebtPersonMode.NEW) null else selectedPerson,
            )
        }
    }

    fun updatePersonName(value: String) = updateForm { copy(personName = value) }

    fun updatePeopleQuery(value: String) {
        _uiState.update { it.copy(peopleQuery = value, peopleLoadError = null) }
        peopleQuery.value = value
    }

    fun selectExistingPerson(personId: PersonId) {
        val person = _uiState.value.selectablePeople.firstOrNull { it.id == personId } ?: return
        pendingCreateIdentity = null
        updateForm {
            copy(
                personMode = DebtPersonMode.EXISTING,
                selectedPerson = ExistingPersonSelection(person.id, person.displayName),
            )
        }
    }

    fun retryPeople() {
        peopleRetry.value += 1
    }

    fun toggleGroupParticipant(personId: PersonId) {
        val state = _uiState.value
        val selected = state.groupExpenseForm.participants.firstOrNull { it.person.id == personId }
        if (selected != null) {
            updateGroupExpenseForm {
                copy(participants = participants.filterNot { it.person.id == personId })
            }
            return
        }
        val person = state.selectablePeople.firstOrNull { it.id == personId } ?: return
        updateGroupExpenseForm {
            copy(
                participants = participants + GroupExpenseParticipantDraft(
                    person = ExistingPersonSelection(person.id, person.displayName),
                ),
            )
        }
    }

    fun updateGroupParticipantAmount(personId: PersonId, value: String) = updateGroupExpenseForm {
        copy(
            participants = participants.map { participant ->
                if (participant.person.id == personId) participant.copy(amount = value) else participant
            },
        )
    }

    fun updateGroupCurrency(value: CurrencyCode) = updateGroupExpenseForm { copy(currency = value) }

    fun updateGroupDirection(value: DebtDirection) = updateGroupExpenseForm { copy(direction = value) }

    fun updateGroupDescription(value: String) = updateGroupExpenseForm { copy(description = value) }

    fun updateGroupNotes(value: String) = updateGroupExpenseForm { copy(notes = value) }

    fun reviewGroupExpense() {
        val state = _uiState.value
        if (state.isSaving) return
        val form = state.groupExpenseForm
        val description = form.description.trim()
        if (description.isEmpty()) {
            _uiState.update { it.copy(groupExpenseError = "اكتب وصف العملية الجماعية.") }
            return
        }
        if (form.participants.size < 2) {
            _uiState.update { it.copy(groupExpenseError = "اختر شخصين على الأقل للعملية الجماعية.") }
            return
        }

        val parsed = try {
            form.participants.map { participant ->
                val amount = MoneyInputParser.parse(participant.amount, form.currency)
                require(amount.minorUnits > 0L)
                participant to amount
            }
        } catch (_: IllegalArgumentException) {
            _uiState.update {
                it.copy(groupExpenseError = "تحقق من مبلغ كل حصة ودقة العملة المختارة.")
            }
            return
        }

        val now = Instant.now(clock)
        val shares = parsed.map { (participant, amount) ->
            GroupExpenseShare(
                id = GroupExpenseShareId(idFactory()),
                debtId = DebtId(idFactory()),
                personId = participant.person.id,
                amount = amount,
            )
        }
        val total = parsed.fold(Money.zero(form.currency)) { sum, (_, amount) -> sum.plus(amount) }
        val command = CreateGroupExpenseCommand(
            commandId = idFactory(),
            expense = GroupExpense(
                id = GroupExpenseId(idFactory()),
                direction = form.direction,
                totalAmount = total,
                occurredAt = now,
                description = description,
                notes = form.notes.trim().ifEmpty { null },
                shares = shares,
            ),
            createdAt = now,
        )
        pendingGroupExpenseCommand = command
        _uiState.update {
            it.copy(
                groupExpenseStep = GroupExpenseEditorStep.REVIEW,
                groupExpensePreview = command.expense,
                groupExpenseError = null,
            )
        }
    }

    fun editGroupExpenseReview() {
        if (_uiState.value.isSaving) return
        pendingGroupExpenseCommand = null
        _uiState.update {
            it.copy(
                groupExpenseStep = GroupExpenseEditorStep.EDIT,
                groupExpensePreview = null,
                groupExpenseError = null,
            )
        }
    }

    fun confirmGroupExpense() {
        val state = _uiState.value
        if (state.isSaving) return
        val command = pendingGroupExpenseCommand
        if (command == null || state.groupExpenseStep != GroupExpenseEditorStep.REVIEW) {
            _uiState.update { it.copy(groupExpenseError = "راجع العملية الجماعية قبل الحفظ.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, groupExpenseError = null) }
        viewModelScope.launch {
            try {
                val created = repository.createGroupExpense(command)
                pendingGroupExpenseCommand = null
                peopleQuery.value = ""
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isGroupExpenseDialogOpen = false,
                        groupExpenseStep = GroupExpenseEditorStep.EDIT,
                        groupExpenseForm = GroupExpenseForm(),
                        groupExpensePreview = null,
                        groupExpenseError = null,
                        peopleQuery = "",
                        successMessage = "تم حفظ العملية الجماعية وربط ${created.expense.shares.size} حسابات بنجاح.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: RecordNotFoundException) {
                pendingGroupExpenseCommand = null
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        groupExpenseStep = GroupExpenseEditorStep.EDIT,
                        groupExpensePreview = null,
                        groupExpenseError = "أحد الأشخاص المحددين لم يعد متاحًا. راجع المشاركين.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        groupExpenseError = "لم تُحفظ العملية الجماعية. أعد المحاولة دون تغيير البيانات.",
                    )
                }
            }
        }
    }

    fun updateAmount(value: String) = updateForm { copy(amount = value) }

    fun updateCurrency(value: CurrencyCode) = updateForm { copy(currency = value) }

    fun updateDirection(value: DebtDirection) = updateForm { copy(direction = value) }

    fun updateDescription(value: String) = updateForm { copy(description = value) }

    fun updateDueDate(value: LocalDate?) = updateForm {
        copy(
            dueDate = value,
            remindOnDueDate = if (value == null) false else remindOnDueDate,
            strongAlarmEnabled = if (value == null) false else strongAlarmEnabled,
        )
    }

    fun updateRemindOnDueDate(value: Boolean) = updateForm {
        val enabled = value && dueDate != null
        copy(
            remindOnDueDate = enabled,
            strongAlarmEnabled = strongAlarmEnabled && enabled,
        )
    }

    fun updateStrongAlarm(value: Boolean) = updateForm {
        val enabled = value && dueDate != null
        copy(
            strongAlarmEnabled = enabled,
            remindOnDueDate = if (enabled) true else remindOnDueDate,
        )
    }

    fun updateStrongAlarmTime(value: LocalTime) = updateForm {
        copy(strongAlarmTime = value.withSecond(0).withNano(0))
    }

    fun createDebt() {
        val state = _uiState.value
        if (state.isSaving) return

        val form = state.createForm
        val personName = form.personName.trim()
        val selectedPerson = form.selectedPerson
        when (form.personMode) {
            DebtPersonMode.NEW -> if (personName.isEmpty()) {
                _uiState.update { it.copy(formError = "اكتب اسم الشخص.") }
                return
            }

            DebtPersonMode.EXISTING -> if (selectedPerson == null) {
                _uiState.update { it.copy(formError = "اختر شخصًا محفوظًا.") }
                return
            }
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
        if ((form.remindOnDueDate || form.strongAlarmEnabled) && form.dueDate == null) {
            _uiState.update {
                it.copy(formError = "اختر تاريخ الاستحقاق قبل تفعيل المتابعة أو المنبه.")
            }
            return
        }

        val identity = pendingCreateIdentity ?: PendingCreateIdentity(
            personId = selectedPerson?.id ?: PersonId(idFactory()),
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
            strongAlarm = if (form.strongAlarmEnabled) {
                StrongAlarmRequest(
                    id = idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                        time = form.strongAlarmTime,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            },
            timestamp = now,
        ).also { pendingCreateIdentity = it }

        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                val created = when (form.personMode) {
                    DebtPersonMode.NEW -> repository.createPersonWithDebt(
                        CreatePersonWithDebtCommand(
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
                            strongAlarm = identity.strongAlarm,
                        ),
                    )

                    DebtPersonMode.EXISTING -> repository.createDebtForExistingPerson(
                        CreateDebtForExistingPersonCommand(
                            personId = identity.personId,
                            debtId = identity.debtId,
                            direction = form.direction,
                            originalAmount = amount,
                            openedAt = identity.timestamp,
                            createdAt = identity.timestamp,
                            dueDate = form.dueDate,
                            description = form.description.trim().ifEmpty { null },
                            dueReminder = identity.reminder,
                            strongAlarm = identity.strongAlarm,
                        ),
                    )
                }
                val schedulingFailed = listOfNotNull(
                    created.dueReminder,
                    created.strongAlarm,
                ).map { reminder ->
                    runCatching { reminderScheduler.schedule(reminder) }.isFailure
                }.any { it }
                if (schedulingFailed) {
                    runCatching { reminderScheduler.requestRecovery() }
                }
                pendingCreateIdentity = null
                peopleQuery.value = ""
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isCreateDialogOpen = false,
                        createForm = CreateDebtForm(),
                        peopleQuery = "",
                        successMessage = if (schedulingFailed && created.strongAlarm != null) {
                            "تم حفظ الحساب والمتابعة. المنبه القوي يحتاج إذن Android للمنبهات الدقيقة."
                        } else if (schedulingFailed) {
                            "تم حفظ الحساب والمتابعة، وستُعاد محاولة الجدولة تلقائيًا."
                        } else if (created.strongAlarm != null) {
                            "تم حفظ الحساب وتفعيل المتابعة والمنبه القوي."
                        } else if (created.dueReminder != null) {
                            "تم حفظ الحساب وتفعيل المتابعة الذكية."
                        } else if (form.personMode == DebtPersonMode.EXISTING) {
                            "تم حفظ دين جديد للشخص ${created.person.displayName} بنجاح."
                        } else {
                            "تم حفظ الحساب والدين بنجاح."
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: RecordNotFoundException) {
                pendingCreateIdentity = null
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        createForm = it.createForm.copy(selectedPerson = null),
                        formError = "لم يعد الشخص المحدد متاحًا. اختر شخصًا آخر.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        formError = "لم يُحفظ الدين. أعد المحاولة دون تغيير البيانات.",
                    )
                }
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun updateForm(transform: CreateDebtForm.() -> CreateDebtForm) {
        pendingCreateIdentity = null
        _uiState.update {
            it.copy(
                createForm = it.createForm.transform(),
                formError = null,
            )
        }
    }

    private fun updateGroupExpenseForm(transform: GroupExpenseForm.() -> GroupExpenseForm) {
        pendingGroupExpenseCommand = null
        _uiState.update {
            it.copy(
                groupExpenseStep = GroupExpenseEditorStep.EDIT,
                groupExpenseForm = it.groupExpenseForm.transform(),
                groupExpensePreview = null,
                groupExpenseError = null,
            )
        }
    }

    private data class PendingCreateIdentity(
        val personId: PersonId,
        val debtId: DebtId,
        val reminder: DueReminderRequest?,
        val strongAlarm: StrongAlarmRequest?,
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

    private companion object {
        const val PEOPLE_SELECTION_LIMIT = 20
        const val PEOPLE_SELECTION_QUERY_LIMIT = PEOPLE_SELECTION_LIMIT + 1
    }
}
