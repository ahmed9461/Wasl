package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.app.data.WaslRepository
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PaymentRecorded
import java.time.Clock
import java.time.Instant
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

sealed interface AccountOperationNotice {
    val personName: String
    val amount: Money

    data class PaymentRecordedNotice(
        override val personName: String,
        override val amount: Money,
    ) : AccountOperationNotice

    data class PaymentReversedNotice(
        override val personName: String,
        override val amount: Money,
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
    val notice: AccountOperationNotice? = null,
)

class AccountDetailsViewModel(
    private val repository: WaslRepository,
    private val debtId: DebtId,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountDetailsUiState())
    val uiState: StateFlow<AccountDetailsUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var pendingPaymentCommand: RecordPaymentCommand? = null
    private var pendingReversalCommand: ReversePaymentCommand? = null
    private var pendingReversalAmount: Money? = null

    init {
        observeAccount()
    }

    fun retryLoad() {
        observeAccount()
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
                // The commit result can be unknown. Keep the exact command for an idempotent retry.
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
                // Preserve the exact command: retrying it cannot append a second reversal.
                _uiState.update {
                    it.copy(
                        isReversingPayment = false,
                        reversalError = "تعذر تأكيد نتيجة العكس. أعد المحاولة بنفس السبب للتحقق بأمان.",
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

    private fun operationTimestamp(account: AccountOverview): Instant? {
        val now = Instant.now(clock)
        if (now.isBefore(account.ledger.header.openedAt)) return null
        val lastRecordedAt = account.ledger.entries.lastOrNull()?.recordedAt
        if (lastRecordedAt != null && now.isBefore(lastRecordedAt)) return null
        return now
    }

    private fun AccountOverview.findPayment(id: LedgerEntryId): PaymentRecorded? =
        ledger.entries.filterIsInstance<PaymentRecorded>().firstOrNull { it.id == id }

    class Factory(
        private val repository: WaslRepository,
        private val debtId: DebtId,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountDetailsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return AccountDetailsViewModel(repository, debtId) as T
        }
    }
}
