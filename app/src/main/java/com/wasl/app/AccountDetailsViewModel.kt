package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.StrongAlarmRequest
import com.wasl.app.data.ResolvePaymentPromiseCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.UnavailablePaymentPromiseStore
import com.wasl.app.data.UpdateDueScheduleCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.document.UnavailablePaymentReceiptService
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.reminder.ReminderTime
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PaymentRecorded
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

data class PaymentForm(
    val amount: String = "",
    val note: String = "",
)

data class PaymentReview(
    val amount: Money,
    val remainingAfter: Money,
)

data class DueScheduleForm(
    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
    val strongAlarmEnabled: Boolean = false,
    val strongAlarmTime: LocalTime = ReminderTime.defaultDueTime,
)

data class ReceiptIdentityForm(
    val identityId: String? = null,
    val displayName: String = "",
    val activityName: String = "",
    val phone: String = "",
    val footerText: String = "",
    val banner: DocumentBannerAsset? = null,
)

data class PaymentPromiseForm(
    val promisedDate: LocalDate? = null,
    val note: String = "",
)

data class PaymentPromiseResolutionForm(
    val promiseId: String,
    val status: PaymentPromiseStatus,
    val note: String = "",
)

sealed interface AccountOperationNotice {
    data class PaymentRecordedNotice(
        val personName: String,
        val amount: Money,
    ) : AccountOperationNotice

    data class PaymentReversedNotice(
        val personName: String,
        val amount: Money,
    ) : AccountOperationNotice

    data class DueScheduleUpdatedNotice(
        val personName: String,
        val dueDate: LocalDate?,
        val reminderEnabled: Boolean,
        val strongAlarmEnabled: Boolean,
        val platformSyncPending: Boolean,
    ) : AccountOperationNotice

    data class PaymentReceiptIssuedNotice(
        val documentNumber: String,
    ) : AccountOperationNotice

    data class PaymentPromiseCreatedNotice(
        val personName: String,
        val promisedDate: LocalDate,
    ) : AccountOperationNotice

    data class PaymentPromiseResolvedNotice(
        val personName: String,
        val promisedDate: LocalDate,
        val status: PaymentPromiseStatus,
    ) : AccountOperationNotice
}

data class AccountDetailsUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val account: AccountOverview? = null,
    val isPaymentDialogOpen: Boolean = false,
    val paymentForm: PaymentForm = PaymentForm(),
    val paymentReview: PaymentReview? = null,
    val isRecordingPayment: Boolean = false,
    val paymentError: String? = null,
    val reversalPaymentId: LedgerEntryId? = null,
    val reversalReason: String = "",
    val isReversingPayment: Boolean = false,
    val reversalError: String? = null,
    val isDueScheduleDialogOpen: Boolean = false,
    val dueScheduleForm: DueScheduleForm = DueScheduleForm(),
    val isUpdatingDueSchedule: Boolean = false,
    val dueScheduleError: String? = null,
    val receiptPaymentId: LedgerEntryId? = null,
    val receiptIdentityForm: ReceiptIdentityForm = ReceiptIdentityForm(),
    val isLoadingReceiptIdentity: Boolean = false,
    val isIssuingReceipt: Boolean = false,
    val receiptError: String? = null,
    val retryingReceiptId: String? = null,
    val receiptRecoveryErrorDocumentId: String? = null,
    val receiptReadyToOpen: IssuedDocumentRecord? = null,
    val paymentPromises: List<PaymentPromiseRecord> = emptyList(),
    val paymentPromiseLoadError: String? = null,
    val isPaymentPromiseDialogOpen: Boolean = false,
    val paymentPromiseForm: PaymentPromiseForm = PaymentPromiseForm(),
    val isCreatingPaymentPromise: Boolean = false,
    val paymentPromiseError: String? = null,
    val paymentPromiseResolutionForm: PaymentPromiseResolutionForm? = null,
    val isResolvingPaymentPromise: Boolean = false,
    val paymentPromiseResolutionError: String? = null,
    val notice: AccountOperationNotice? = null,
)

class AccountDetailsViewModel(
    private val repository: WaslRepository,
    private val debtId: DebtId,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val paymentReceiptService: PaymentReceiptService = UnavailablePaymentReceiptService,
    private val paymentPromiseStore: PaymentPromiseStore = UnavailablePaymentPromiseStore,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountDetailsUiState())
    val uiState: StateFlow<AccountDetailsUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var promiseObservationJob: Job? = null
    private var pendingPaymentCommand: RecordPaymentCommand? = null
    private var pendingReversalCommand: ReversePaymentCommand? = null
    private var pendingReversalAmount: Money? = null
    private var pendingDueScheduleCommand: UpdateDueScheduleCommand? = null
    private var pendingReceiptCommand: PreparePaymentReceiptCommand? = null
    private var pendingPromiseCommand: CreatePaymentPromiseCommand? = null
    private var pendingPromiseResolutionCommand: ResolvePaymentPromiseCommand? = null

    init {
        observeAccount()
        observePaymentPromises()
    }

    fun retryLoad() {
        observeAccount()
        observePaymentPromises()
    }

    fun openPaymentDialog() {
        val account = _uiState.value.account ?: return
        if (account.ledger.balance.isZero) return

        pendingPaymentCommand = null
        _uiState.update {
            it.copy(
                isPaymentDialogOpen = true,
                paymentForm = PaymentForm(),
                paymentReview = null,
                paymentError = null,
                notice = null,
            )
        }
    }

    fun dismissPaymentDialog() {
        if (_uiState.value.isRecordingPayment) return
        pendingPaymentCommand = null
        _uiState.update {
            it.copy(
                isPaymentDialogOpen = false,
                paymentForm = PaymentForm(),
                paymentReview = null,
                paymentError = null,
            )
        }
    }

    fun updatePaymentAmount(value: String) {
        pendingPaymentCommand = null
        _uiState.update {
            it.copy(
                paymentForm = it.paymentForm.copy(amount = value),
                paymentReview = null,
                paymentError = null,
            )
        }
    }

    fun updatePaymentNote(value: String) {
        pendingPaymentCommand = null
        _uiState.update {
            it.copy(
                paymentForm = it.paymentForm.copy(note = value),
                paymentReview = null,
                paymentError = null,
            )
        }
    }

    fun reviewPayment() {
        val state = _uiState.value
        if (state.isRecordingPayment) return
        val account = state.account ?: return
        val amount = try {
            MoneyInputParser.parse(
                raw = state.paymentForm.amount,
                currency = account.ledger.header.originalAmount.currency,
            )
        } catch (_: IllegalArgumentException) {
            _uiState.update {
                it.copy(paymentError = "تحقق من المبلغ ودقة العملة.")
            }
            return
        }

        if (amount.minorUnits > account.ledger.balance.minorUnits) {
            _uiState.update {
                it.copy(paymentError = "المبلغ أكبر من المتبقي في هذا الحساب.")
            }
            return
        }

        pendingPaymentCommand = null
        _uiState.update {
            it.copy(
                paymentReview = PaymentReview(
                    amount = amount,
                    remainingAfter = account.ledger.balance.minus(amount),
                ),
                paymentError = null,
            )
        }
    }

    fun editPayment() {
        if (_uiState.value.isRecordingPayment) return
        pendingPaymentCommand = null
        _uiState.update { it.copy(paymentReview = null, paymentError = null) }
    }

    fun confirmPayment() {
        val state = _uiState.value
        if (state.isRecordingPayment) return
        val account = state.account ?: return
        val review = state.paymentReview ?: return

        val command = pendingPaymentCommand ?: run {
            if (review.amount.minorUnits > account.ledger.balance.minorUnits) {
                _uiState.update {
                    it.copy(
                        paymentReview = null,
                        paymentError = "تغيّر المتبقي. راجع مبلغ الدفعة من جديد.",
                    )
                }
                return
            }

            val recordedAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(
                        paymentError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.",
                    )
                }
                return
            }
            RecordPaymentCommand(
                commandId = idFactory(),
                entryId = LedgerEntryId(idFactory()),
                debtId = debtId,
                amount = review.amount,
                paidAt = recordedAt,
                recordedAt = recordedAt,
                note = state.paymentForm.note.trim().ifEmpty { null },
            ).also { pendingPaymentCommand = it }
        }

        _uiState.update { it.copy(isRecordingPayment = true, paymentError = null) }
        viewModelScope.launch {
            try {
                repository.recordPayment(command)
                pendingPaymentCommand = null
                _uiState.update {
                    it.copy(
                        isPaymentDialogOpen = false,
                        paymentForm = PaymentForm(),
                        paymentReview = null,
                        isRecordingPayment = false,
                        paymentError = null,
                        notice = AccountOperationNotice.PaymentRecordedNotice(
                            personName = account.person.displayName,
                            amount = command.amount,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingPaymentCommand = null
                _uiState.update {
                    it.copy(
                        paymentReview = null,
                        isRecordingPayment = false,
                        paymentError = "لم يعد المبلغ صالحًا للرصيد الحالي. راجعه وأعد المحاولة.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingPaymentCommand = null
                _uiState.update {
                    it.copy(
                        paymentReview = null,
                        isRecordingPayment = false,
                        paymentError = "تعذر العثور على الحساب.",
                    )
                }
            } catch (_: CommandConflictException) {
                pendingPaymentCommand = null
                _uiState.update {
                    it.copy(
                        paymentReview = null,
                        isRecordingPayment = false,
                        paymentError = "تعذر تأكيد أمر الدفعة بأمان. راجع البيانات وأعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isRecordingPayment = false,
                        paymentError = "تعذر تأكيد نتيجة الحفظ. أعد المحاولة بنفس البيانات للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun openReversalDialog(paymentId: LedgerEntryId) {
        val account = _uiState.value.account ?: return
        val payment = account.findPayment(paymentId) ?: return
        if (payment.id in account.ledger.reversedPaymentIds) return

        pendingReversalCommand = null
        pendingReversalAmount = payment.amount
        _uiState.update {
            it.copy(
                reversalPaymentId = paymentId,
                reversalReason = "",
                reversalError = null,
                notice = null,
            )
        }
    }

    fun dismissReversalDialog() {
        if (_uiState.value.isReversingPayment) return
        pendingReversalCommand = null
        pendingReversalAmount = null
        _uiState.update {
            it.copy(
                reversalPaymentId = null,
                reversalReason = "",
                reversalError = null,
            )
        }
    }

    fun updateReversalReason(value: String) {
        pendingReversalCommand = null
        _uiState.update { it.copy(reversalReason = value, reversalError = null) }
    }

    fun confirmReversal() {
        val state = _uiState.value
        if (state.isReversingPayment) return
        val account = state.account ?: return
        val paymentId = state.reversalPaymentId ?: return
        val reason = state.reversalReason.trim()
        if (reason.isEmpty()) {
            _uiState.update { it.copy(reversalError = "اكتب سبب عكس الدفعة.") }
            return
        }

        val command = pendingReversalCommand ?: run {
            val payment = account.findPayment(paymentId)
            if (payment == null || paymentId in account.ledger.reversedPaymentIds) {
                _uiState.update {
                    it.copy(reversalError = "لم تعد هذه الدفعة قابلة للعكس.")
                }
                return
            }
            val recordedAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(
                        reversalError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.",
                    )
                }
                return
            }
            pendingReversalAmount = payment.amount
            ReversePaymentCommand(
                commandId = idFactory(),
                entryId = LedgerEntryId(idFactory()),
                debtId = debtId,
                paymentId = paymentId,
                recordedAt = recordedAt,
                reason = reason,
            ).also { pendingReversalCommand = it }
        }
        val reversedAmount = requireNotNull(pendingReversalAmount)

        _uiState.update { it.copy(isReversingPayment = true, reversalError = null) }
        viewModelScope.launch {
            try {
                repository.reversePayment(command)
                pendingReversalCommand = null
                pendingReversalAmount = null
                _uiState.update {
                    it.copy(
                        reversalPaymentId = null,
                        reversalReason = "",
                        isReversingPayment = false,
                        reversalError = null,
                        notice = AccountOperationNotice.PaymentReversedNotice(
                            personName = account.person.displayName,
                            amount = reversedAmount,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingReversalCommand = null
                pendingReversalAmount = null
                _uiState.update {
                    it.copy(
                        isReversingPayment = false,
                        reversalError = "لم تعد هذه الدفعة قابلة للعكس. حدّث الحساب وراجع السجل.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingReversalCommand = null
                pendingReversalAmount = null
                _uiState.update {
                    it.copy(
                        isReversingPayment = false,
                        reversalError = "تعذر العثور على الحساب أو الدفعة.",
                    )
                }
            } catch (_: CommandConflictException) {
                pendingReversalCommand = null
                pendingReversalAmount = null
                _uiState.update {
                    it.copy(
                        isReversingPayment = false,
                        reversalError = "تعذر تأكيد أمر العكس بأمان. راجع السجل وأعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isReversingPayment = false,
                        reversalError = "تعذر تأكيد نتيجة العكس. أعد المحاولة بنفس السبب للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun openReceiptDialog(paymentId: LedgerEntryId) {
        val account = _uiState.value.account ?: return
        val payment = account.findPayment(paymentId) ?: return
        if (payment.id in account.ledger.reversedPaymentIds) return
        account.issuedDocuments.firstOrNull { it.ledgerEntryId == paymentId }?.let { document ->
            if (document.status == com.wasl.app.data.DocumentStatus.READY) {
                _uiState.update { it.copy(receiptReadyToOpen = document) }
            } else {
                retryPaymentReceipt(document.id)
            }
            return
        }

        pendingReceiptCommand = null
        _uiState.update {
            it.copy(
                receiptPaymentId = paymentId,
                receiptIdentityForm = ReceiptIdentityForm(),
                isLoadingReceiptIdentity = true,
                receiptError = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            try {
                val identity = paymentReceiptService.getDefaultIdentity()
                _uiState.update { current ->
                    if (current.receiptPaymentId != paymentId) current else {
                        current.copy(
                            receiptIdentityForm = identity?.let {
                                ReceiptIdentityForm(
                                    identityId = it.id,
                                    displayName = it.displayName,
                                    activityName = it.activityName.orEmpty(),
                                    phone = it.phone.orEmpty(),
                                    footerText = it.footerText.orEmpty(),
                                    banner = it.banner,
                                )
                            } ?: ReceiptIdentityForm(),
                            isLoadingReceiptIdentity = false,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { current ->
                    if (current.receiptPaymentId != paymentId) current else {
                        current.copy(
                            isLoadingReceiptIdentity = false,
                            receiptError = "تعذر قراءة الهوية المحفوظة. يمكنك إدخال الاسم والمتابعة.",
                        )
                    }
                }
            }
        }
    }

    fun dismissReceiptDialog() {
        if (_uiState.value.isIssuingReceipt) return
        pendingReceiptCommand = null
        _uiState.update {
            it.copy(
                receiptPaymentId = null,
                receiptIdentityForm = ReceiptIdentityForm(),
                isLoadingReceiptIdentity = false,
                receiptError = null,
            )
        }
    }

    fun updateReceiptIssuerName(value: String) = updateReceiptForm {
        copy(displayName = value)
    }

    fun updateReceiptActivityName(value: String) = updateReceiptForm {
        copy(activityName = value)
    }

    fun updateReceiptPhone(value: String) = updateReceiptForm {
        copy(phone = value)
    }

    fun updateReceiptFooter(value: String) = updateReceiptForm {
        copy(footerText = value)
    }

    fun confirmPaymentReceipt() {
        val state = _uiState.value
        if (state.isIssuingReceipt || state.isLoadingReceiptIdentity) return
        val account = state.account ?: return
        val paymentId = state.receiptPaymentId ?: return
        val payment = account.findPayment(paymentId)
        if (payment == null || paymentId in account.ledger.reversedPaymentIds) {
            _uiState.update { it.copy(receiptError = "لم تعد هذه الدفعة صالحة لإصدار إيصال.") }
            return
        }
        val issuerName = state.receiptIdentityForm.displayName.trim()
        if (issuerName.isEmpty()) {
            _uiState.update { it.copy(receiptError = "اكتب اسم مُصدر الإيصال.") }
            return
        }

        val command = pendingReceiptCommand ?: run {
            val issuedAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(receiptError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.")
                }
                return
            }
            PreparePaymentReceiptCommand(
                commandId = idFactory(),
                documentId = idFactory(),
                identityId = state.receiptIdentityForm.identityId ?: idFactory(),
                debtId = debtId,
                paymentId = paymentId,
                issuerDisplayName = issuerName,
                issuerActivityName = state.receiptIdentityForm.activityName.trim().ifEmpty { null },
                issuerPhone = state.receiptIdentityForm.phone.trim().ifEmpty { null },
                footerText = state.receiptIdentityForm.footerText.trim().ifEmpty { null },
                issuerBanner = state.receiptIdentityForm.banner,
                issuedAt = issuedAt,
                issueZoneId = zoneIdProvider(),
            ).also { pendingReceiptCommand = it }
        }

        _uiState.update { it.copy(isIssuingReceipt = true, receiptError = null) }
        viewModelScope.launch {
            try {
                val document = paymentReceiptService.issue(command)
                pendingReceiptCommand = null
                _uiState.update {
                    it.copy(
                        receiptPaymentId = null,
                        receiptIdentityForm = ReceiptIdentityForm(),
                        isIssuingReceipt = false,
                        receiptError = null,
                        receiptReadyToOpen = document,
                        notice = AccountOperationNotice.PaymentReceiptIssuedNotice(
                            documentNumber = document.documentNumber,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingReceiptCommand = null
                _uiState.update {
                    it.copy(
                        isIssuingReceipt = false,
                        receiptError = "تعذر إصدار الإيصال من هذه الدفعة. حدّث الحساب وراجع السجل.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingReceiptCommand = null
                _uiState.update {
                    it.copy(isIssuingReceipt = false, receiptError = "تعذر العثور على الدفعة.")
                }
            } catch (_: CommandConflictException) {
                pendingReceiptCommand = null
                _uiState.update {
                    it.copy(
                        isIssuingReceipt = false,
                        receiptError = "تعذر تأكيد إصدار الإيصال بأمان. أعد فتح الحساب وحاول مجددًا.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isIssuingReceipt = false,
                        receiptError = "حُفظت محاولة الإيصال، لكن تعذر تجهيز PDF. أعد المحاولة للاسترداد.",
                    )
                }
            }
        }
    }

    fun retryPaymentReceipt(documentId: String) {
        if (_uiState.value.retryingReceiptId != null) return
        _uiState.update {
            it.copy(
                retryingReceiptId = documentId,
                receiptRecoveryErrorDocumentId = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            try {
                val document = paymentReceiptService.retry(documentId)
                _uiState.update {
                    it.copy(
                        retryingReceiptId = null,
                        receiptRecoveryErrorDocumentId = null,
                        receiptReadyToOpen = document,
                        notice = AccountOperationNotice.PaymentReceiptIssuedNotice(
                            documentNumber = document.documentNumber,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        retryingReceiptId = null,
                        receiptRecoveryErrorDocumentId = documentId,
                    )
                }
            }
        }
    }

    fun receiptReadyHandled() {
        _uiState.update { it.copy(receiptReadyToOpen = null) }
    }

    private fun updateReceiptForm(transform: ReceiptIdentityForm.() -> ReceiptIdentityForm) {
        pendingReceiptCommand = null
        _uiState.update {
            it.copy(
                receiptIdentityForm = it.receiptIdentityForm.transform(),
                receiptError = null,
            )
        }
    }

    fun openDueScheduleDialog() {
        val account = _uiState.value.account ?: return
        if (account.ledger.balance.isZero) return
        pendingDueScheduleCommand = null
        val zoneId = zoneIdProvider()
        val activeStrongAlarm = account.strongAlarm?.takeIf {
            it.status != ReminderStatus.CANCELLED
        }
        _uiState.update {
            it.copy(
                isDueScheduleDialogOpen = true,
                dueScheduleForm = DueScheduleForm(
                    dueDate = account.ledger.header.dueDate,
                    remindOnDueDate = account.dueReminder
                        ?.status
                        ?.let { it != ReminderStatus.CANCELLED }
                        ?: false,
                    strongAlarmEnabled = activeStrongAlarm != null,
                    strongAlarmTime = activeStrongAlarm
                        ?.triggerAt
                        ?.atZone(zoneId)
                        ?.toLocalTime()
                        ?.withSecond(0)
                        ?.withNano(0)
                        ?: ReminderTime.defaultDueTime,
                ),
                dueScheduleError = null,
                notice = null,
            )
        }
    }

    fun dismissDueScheduleDialog() {
        if (_uiState.value.isUpdatingDueSchedule) return
        pendingDueScheduleCommand = null
        _uiState.update {
            it.copy(
                isDueScheduleDialogOpen = false,
                dueScheduleForm = DueScheduleForm(),
                dueScheduleError = null,
            )
        }
    }

    fun updateDueScheduleDate(value: LocalDate?) {
        pendingDueScheduleCommand = null
        _uiState.update {
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    dueDate = value,
                    remindOnDueDate = if (value == null) false else {
                        it.dueScheduleForm.remindOnDueDate
                    },
                    strongAlarmEnabled = if (value == null) false else {
                        it.dueScheduleForm.strongAlarmEnabled
                    },
                ),
                dueScheduleError = null,
            )
        }
    }

    fun updateDueScheduleReminder(value: Boolean) {
        pendingDueScheduleCommand = null
        _uiState.update {
            val enabled = value && it.dueScheduleForm.dueDate != null
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    remindOnDueDate = enabled,
                    strongAlarmEnabled = it.dueScheduleForm.strongAlarmEnabled && enabled,
                ),
                dueScheduleError = null,
            )
        }
    }

    fun updateDueScheduleStrongAlarm(value: Boolean) {
        pendingDueScheduleCommand = null
        _uiState.update {
            val enabled = value && it.dueScheduleForm.dueDate != null
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    strongAlarmEnabled = enabled,
                    remindOnDueDate = if (enabled) true else it.dueScheduleForm.remindOnDueDate,
                ),
                dueScheduleError = null,
            )
        }
    }

    fun updateDueScheduleStrongAlarmTime(value: LocalTime) {
    pendingDueScheduleCommand = null
    _uiState.update {
        it.copy(
            dueScheduleForm = it.dueScheduleForm.copy(
                strongAlarmTime = value.withSecond(0).withNano(0),
            ),
            dueScheduleError = null,
        )
    }
    }

    fun confirmDueSchedule() {
        val state = _uiState.value
        if (state.isUpdatingDueSchedule) return
        val account = state.account ?: return
        val form = state.dueScheduleForm
        val zoneId = zoneIdProvider()
        val now = Instant.now(clock)
        val today = now.atZone(zoneId).toLocalDate()

        if (form.dueDate?.isBefore(today) == true) {
            _uiState.update {
                it.copy(dueScheduleError = "اختر تاريخ استحقاق اليوم أو بعده، أو ألغِ التاريخ.")
            }
            return
        }
        if ((form.remindOnDueDate || form.strongAlarmEnabled) && form.dueDate == null) {
            _uiState.update {
                it.copy(dueScheduleError = "اختر تاريخ الاستحقاق قبل تفعيل المتابعة أو المنبه.")
            }
            return
        }

        val currentReminderEnabled = account.dueReminder
            ?.status
            ?.let { it != ReminderStatus.CANCELLED }
            ?: false
        val currentStrongAlarmEnabled = account.strongAlarm
            ?.status
            ?.let { it != ReminderStatus.CANCELLED }
            ?: false
        val currentStrongAlarmTime = account.strongAlarm
            ?.takeIf { it.status != ReminderStatus.CANCELLED }
            ?.triggerAt
            ?.atZone(zoneId)
            ?.toLocalTime()
            ?.withSecond(0)
            ?.withNano(0)
            ?: ReminderTime.defaultDueTime
        if (pendingDueScheduleCommand == null &&
            form.dueDate == account.ledger.header.dueDate &&
            form.remindOnDueDate == currentReminderEnabled &&
            form.strongAlarmEnabled == currentStrongAlarmEnabled &&
            (!form.strongAlarmEnabled || form.strongAlarmTime == currentStrongAlarmTime)
        ) {
            _uiState.update { it.copy(dueScheduleError = "لم تغيّر الموعد أو المتابعة أو المنبه.") }
            return
        }

        val command = pendingDueScheduleCommand ?: run {
            val updatedAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(
                        dueScheduleError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.",
                    )
                }
                return
            }
            val reminder = if (form.remindOnDueDate) {
                DueReminderRequest(
                    id = account.dueReminder?.id ?: idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            }
            val strongAlarm = if (form.strongAlarmEnabled) {
                StrongAlarmRequest(
                    id = account.strongAlarm?.id ?: idFactory(),
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
            }
            UpdateDueScheduleCommand(
                commandId = idFactory(),
                auditEventId = idFactory(),
                debtId = debtId,
                dueDate = form.dueDate,
                dueReminder = reminder,
                strongAlarm = strongAlarm,
                updatedAt = updatedAt,
            ).also { pendingDueScheduleCommand = it }
        }

        _uiState.update { it.copy(isUpdatingDueSchedule = true, dueScheduleError = null) }
        viewModelScope.launch {
            try {
                val updated = repository.updateDueSchedule(command)
                val activeReminders = listOfNotNull(updated.dueReminder, updated.strongAlarm)
                    .filter { it.status != ReminderStatus.CANCELLED }
                val activeIds = activeReminders.mapTo(mutableSetOf()) { it.id }
                val schedulingFailed = activeReminders.map { reminder ->
                    runCatching { reminderScheduler.schedule(reminder) }
                        .onFailure { runCatching { reminderScheduler.requestRecovery() } }
                        .isFailure
                }.any { it }
                val cancellationFailed = listOfNotNull(account.dueReminder, account.strongAlarm)
                    .filter { it.id !in activeIds }
                    .map { old -> runCatching { reminderScheduler.cancel(old.id) }.isFailure }
                    .any { it }
                val persistedReminderEnabled = updated.dueReminder
                    ?.status
                    ?.let { it != ReminderStatus.CANCELLED }
                    ?: false
                val persistedStrongAlarmEnabled = updated.strongAlarm
                    ?.status
                    ?.let { it != ReminderStatus.CANCELLED }
                    ?: false
                val platformSyncFailed = schedulingFailed || cancellationFailed
                pendingDueScheduleCommand = null
                _uiState.update {
                    it.copy(
                        isDueScheduleDialogOpen = false,
                        dueScheduleForm = DueScheduleForm(),
                        isUpdatingDueSchedule = false,
                        dueScheduleError = null,
                        notice = AccountOperationNotice.DueScheduleUpdatedNotice(
                            personName = account.person.displayName,
                            dueDate = updated.ledger.header.dueDate,
                            reminderEnabled = persistedReminderEnabled,
                            strongAlarmEnabled = persistedStrongAlarmEnabled,
                            platformSyncPending = platformSyncFailed,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingDueScheduleCommand = null
                _uiState.update {
                    it.copy(
                        isUpdatingDueSchedule = false,
                        dueScheduleError = "لم يعد هذا التعديل صالحًا للحساب الحالي.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingDueScheduleCommand = null
                _uiState.update {
                    it.copy(
                        isUpdatingDueSchedule = false,
                        dueScheduleError = "تعذر العثور على الحساب.",
                    )
                }
            } catch (_: CommandConflictException) {
                pendingDueScheduleCommand = null
                _uiState.update {
                    it.copy(
                        isUpdatingDueSchedule = false,
                        dueScheduleError = "تعذر تأكيد تعديل الموعد بأمان. راجع البيانات وأعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingDueSchedule = false,
                        dueScheduleError = "تعذر تأكيد نتيجة التعديل. أعد المحاولة بنفس البيانات للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun openPaymentPromiseDialog() {
        val account = _uiState.value.account ?: return
        if (account.ledger.balance.isZero) return
        pendingPromiseCommand = null
        _uiState.update {
            it.copy(
                isPaymentPromiseDialogOpen = true,
                paymentPromiseForm = PaymentPromiseForm(),
                paymentPromiseError = null,
                notice = null,
            )
        }
    }

    fun dismissPaymentPromiseDialog() {
        if (_uiState.value.isCreatingPaymentPromise) return
        pendingPromiseCommand = null
        _uiState.update {
            it.copy(
                isPaymentPromiseDialogOpen = false,
                paymentPromiseForm = PaymentPromiseForm(),
                paymentPromiseError = null,
            )
        }
    }

    fun updatePaymentPromiseDate(value: LocalDate?) {
        pendingPromiseCommand = null
        _uiState.update {
            it.copy(
                paymentPromiseForm = it.paymentPromiseForm.copy(promisedDate = value),
                paymentPromiseError = null,
            )
        }
    }

    fun updatePaymentPromiseNote(value: String) {
        pendingPromiseCommand = null
        _uiState.update {
            it.copy(
                paymentPromiseForm = it.paymentPromiseForm.copy(note = value),
                paymentPromiseError = null,
            )
        }
    }

    fun confirmPaymentPromise() {
        val state = _uiState.value
        if (state.isCreatingPaymentPromise) return
        val account = state.account ?: return
        if (account.ledger.balance.isZero) {
            _uiState.update { it.copy(paymentPromiseError = "الحساب مسدد ولا يقبل وعدًا جديدًا.") }
            return
        }
        val promisedDate = state.paymentPromiseForm.promisedDate ?: run {
            _uiState.update { it.copy(paymentPromiseError = "اختر تاريخ الوعد بالسداد.") }
            return
        }
        val command = pendingPromiseCommand ?: run {
            val createdAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(
                        paymentPromiseError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.",
                    )
                }
                return
            }
            CreatePaymentPromiseCommand(
                commandId = idFactory(),
                promiseId = idFactory(),
                debtId = debtId,
                promisedDate = promisedDate,
                note = state.paymentPromiseForm.note.trim().ifEmpty { null },
                createdAt = createdAt,
            ).also { pendingPromiseCommand = it }
        }

        _uiState.update { it.copy(isCreatingPaymentPromise = true, paymentPromiseError = null) }
        viewModelScope.launch {
            try {
                paymentPromiseStore.createPaymentPromise(command)
                pendingPromiseCommand = null
                _uiState.update {
                    it.copy(
                        isPaymentPromiseDialogOpen = false,
                        paymentPromiseForm = PaymentPromiseForm(),
                        isCreatingPaymentPromise = false,
                        paymentPromiseError = null,
                        notice = AccountOperationNotice.PaymentPromiseCreatedNotice(
                            personName = account.person.displayName,
                            promisedDate = command.promisedDate,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingPromiseCommand = null
                _uiState.update {
                    it.copy(
                        isCreatingPaymentPromise = false,
                        paymentPromiseError = "لم يعد هذا الوعد صالحًا للحساب الحالي.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingPromiseCommand = null
                _uiState.update {
                    it.copy(
                        isCreatingPaymentPromise = false,
                        paymentPromiseError = "تعذر العثور على الحساب.",
                    )
                }
            } catch (_: CommandConflictException) {
                pendingPromiseCommand = null
                _uiState.update {
                    it.copy(
                        isCreatingPaymentPromise = false,
                        paymentPromiseError = "تعذر تأكيد الوعد بأمان. راجع البيانات وأعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingPaymentPromise = false,
                        paymentPromiseError = "تعذر تأكيد نتيجة حفظ الوعد. أعد المحاولة بنفس البيانات للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun openPaymentPromiseResolution(promiseId: String, status: PaymentPromiseStatus) {
        if (status == PaymentPromiseStatus.PENDING) return
        val promise = _uiState.value.paymentPromises.firstOrNull { it.id == promiseId } ?: return
        if (promise.status != PaymentPromiseStatus.PENDING) return
        pendingPromiseResolutionCommand = null
        _uiState.update {
            it.copy(
                paymentPromiseResolutionForm = PaymentPromiseResolutionForm(
                    promiseId = promiseId,
                    status = status,
                ),
                paymentPromiseResolutionError = null,
                notice = null,
            )
        }
    }

    fun dismissPaymentPromiseResolution() {
        if (_uiState.value.isResolvingPaymentPromise) return
        pendingPromiseResolutionCommand = null
        _uiState.update {
            it.copy(
                paymentPromiseResolutionForm = null,
                paymentPromiseResolutionError = null,
            )
        }
    }

    fun updatePaymentPromiseResolutionNote(value: String) {
        pendingPromiseResolutionCommand = null
        _uiState.update {
            it.copy(
                paymentPromiseResolutionForm = it.paymentPromiseResolutionForm?.copy(note = value),
                paymentPromiseResolutionError = null,
            )
        }
    }

    fun confirmPaymentPromiseResolution() {
        val state = _uiState.value
        if (state.isResolvingPaymentPromise) return
        val account = state.account ?: return
        val form = state.paymentPromiseResolutionForm ?: return
        val promise = state.paymentPromises.firstOrNull { it.id == form.promiseId } ?: run {
            _uiState.update {
                it.copy(paymentPromiseResolutionError = "لم يعد الوعد موجودًا في السجل.")
            }
            return
        }
        if (promise.status != PaymentPromiseStatus.PENDING) {
            _uiState.update {
                it.copy(paymentPromiseResolutionError = "تم حسم هذا الوعد مسبقًا.")
            }
            return
        }
        val command = pendingPromiseResolutionCommand ?: run {
            val resolvedAt = operationTimestamp(account) ?: run {
                _uiState.update {
                    it.copy(
                        paymentPromiseResolutionError = "وقت الجهاز أقدم من آخر عملية. صحح الوقت ثم أعد المحاولة.",
                    )
                }
                return
            }
            ResolvePaymentPromiseCommand(
                commandId = idFactory(),
                promiseId = promise.id,
                debtId = debtId,
                status = form.status,
                resolvedAt = resolvedAt,
                note = form.note.trim().ifEmpty { null },
            ).also { pendingPromiseResolutionCommand = it }
        }

        _uiState.update {
            it.copy(isResolvingPaymentPromise = true, paymentPromiseResolutionError = null)
        }
        viewModelScope.launch {
            try {
                paymentPromiseStore.resolvePaymentPromise(command)
                pendingPromiseResolutionCommand = null
                _uiState.update {
                    it.copy(
                        paymentPromiseResolutionForm = null,
                        isResolvingPaymentPromise = false,
                        paymentPromiseResolutionError = null,
                        notice = AccountOperationNotice.PaymentPromiseResolvedNotice(
                            personName = account.person.displayName,
                            promisedDate = promise.promisedDate,
                            status = command.status,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                pendingPromiseResolutionCommand = null
                _uiState.update {
                    it.copy(
                        isResolvingPaymentPromise = false,
                        paymentPromiseResolutionError = "لم يعد هذا الوعد قابلًا للحسم.",
                    )
                }
            } catch (_: RecordNotFoundException) {
                pendingPromiseResolutionCommand = null
                _uiState.update {
                    it.copy(
                        isResolvingPaymentPromise = false,
                        paymentPromiseResolutionError = "تعذر العثور على الوعد.",
                    )
                }
            } catch (_: CommandConflictException) {
                pendingPromiseResolutionCommand = null
                _uiState.update {
                    it.copy(
                        isResolvingPaymentPromise = false,
                        paymentPromiseResolutionError = "تعذر تأكيد حسم الوعد بأمان. راجع السجل وأعد المحاولة.",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingPaymentPromise = false,
                        paymentPromiseResolutionError = "تعذر تأكيد نتيجة الحسم. أعد المحاولة بنفس القرار للتحقق بأمان.",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun observeAccount() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        observationJob = viewModelScope.launch {
            repository.observeAccount(debtId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر قراءة تفاصيل الحساب المحفوظة.",
                        )
                    }
                }
                .collect { account ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = if (account == null) "الحساب غير موجود." else null,
                            account = account,
                        )
                    }
                }
        }
    }

    private fun observePaymentPromises() {
        promiseObservationJob?.cancel()
        _uiState.update { it.copy(paymentPromiseLoadError = null) }
        promiseObservationJob = viewModelScope.launch {
            paymentPromiseStore.observePaymentPromises(debtId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(paymentPromiseLoadError = "تعذر قراءة وعود السداد المحفوظة.")
                    }
                }
                .collect { promises ->
                    _uiState.update {
                        it.copy(
                            paymentPromises = promises,
                            paymentPromiseLoadError = null,
                        )
                    }
                }
        }
    }

    private fun operationTimestamp(account: AccountOverview): Instant? {
        val now = Instant.now(clock)
        if (now.isBefore(account.ledger.header.openedAt)) return null
        val lastRecordedAt = account.ledger.entries.lastOrNull()?.recordedAt
        if (lastRecordedAt != null && now.isBefore(lastRecordedAt)) return null
        val lastAuditAt = account.dueScheduleAuditEvents.lastOrNull()?.occurredAt
        if (lastAuditAt != null && now.isBefore(lastAuditAt)) return null
        val lastPromiseAt = _uiState.value.paymentPromises.maxOfOrNull { it.updatedAt }
        if (lastPromiseAt != null && now.isBefore(lastPromiseAt)) return null
        return now
    }

    private fun AccountOverview.findPayment(id: LedgerEntryId): PaymentRecorded? =
        ledger.entries.filterIsInstance<PaymentRecorded>().firstOrNull { it.id == id }

    class Factory(
        private val repository: WaslRepository,
        private val debtId: DebtId,
        private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
        private val paymentReceiptService: PaymentReceiptService =
            UnavailablePaymentReceiptService,
        private val paymentPromiseStore: PaymentPromiseStore = UnavailablePaymentPromiseStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountDetailsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return AccountDetailsViewModel(
                repository = repository,
                debtId = debtId,
                reminderScheduler = reminderScheduler,
                paymentReceiptService = paymentReceiptService,
                paymentPromiseStore = paymentPromiseStore,
            ) as T
        }
    }
}
