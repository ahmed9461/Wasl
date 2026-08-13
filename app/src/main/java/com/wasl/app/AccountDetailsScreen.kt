package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDetailsScreen(
    state: AccountDetailsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPayment: () -> Unit,
    onDismissPayment: () -> Unit,
    onPaymentAmountChange: (String) -> Unit,
    onPaymentNoteChange: (String) -> Unit,
    onReviewPayment: () -> Unit,
    onEditPayment: () -> Unit,
    onConfirmPayment: () -> Unit,
    onOpenReversal: (LedgerEntryId) -> Unit,
    onDismissReversal: () -> Unit,
    onReversalReasonChange: (String) -> Unit,
    onConfirmReversal: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.toDisplayText())
        onNoticeShown()
    }

    val account = state.account
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = account?.person?.displayName ?: "تفاصيل الحساب",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("رجوع")
                    }
                },
            )
        },
        floatingActionButton = {
            if (account != null && !account.ledger.balance.isZero) {
                ExtendedFloatingActionButton(
                    onClick = onOpenPayment,
                    modifier = Modifier.testTag("record-payment"),
                ) {
                    Text("تسجيل دفعة")
                }
            }
        },
    ) { scaffoldPadding ->
        when {
            state.isLoading && account == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            account == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadErrorCard(
                        message = state.loadError ?: "الحساب غير موجود.",
                        onRetry = onRetry,
                    )
                }
            }

            else -> {
                AccountDetailsContent(
                    account = account,
                    loadError = state.loadError,
                    scaffoldPadding = scaffoldPadding,
                    onRetry = onRetry,
                    onOpenReversal = onOpenReversal,
                )
            }
        }
    }

    if (state.isPaymentDialogOpen && account != null) {
        PaymentDialog(
            account = account,
            form = state.paymentForm,
            review = state.paymentReview,
            isSaving = state.isRecordingPayment,
            error = state.paymentError,
            onDismiss = onDismissPayment,
            onAmountChange = onPaymentAmountChange,
            onNoteChange = onPaymentNoteChange,
            onReview = onReviewPayment,
            onEdit = onEditPayment,
            onConfirm = onConfirmPayment,
        )
    }

    val reversalPayment = account
        ?.ledger
        ?.entries
        ?.filterIsInstance<PaymentRecorded>()
        ?.firstOrNull { it.id == state.reversalPaymentId }
    if (state.reversalPaymentId != null && reversalPayment != null) {
        ReversalDialog(
            payment = reversalPayment,
            reason = state.reversalReason,
            isSaving = state.isReversingPayment,
            error = state.reversalError,
            onDismiss = onDismissReversal,
            onReasonChange = onReversalReasonChange,
            onConfirm = onConfirmReversal,
        )
    }
}

@Composable
private fun AccountDetailsContent(
    account: AccountOverview,
    loadError: String?,
    scaffoldPadding: PaddingValues,
    onRetry: () -> Unit,
    onOpenReversal: (LedgerEntryId) -> Unit,
) {
    val payments = account.ledger.entries
        .filterIsInstance<PaymentRecorded>()
        .associateBy { it.id }
    val reversedPaymentIds = account.ledger.reversedPaymentIds

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 16.dp,
            end = 20.dp,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (loadError != null) {
            item("load-error") {
                LoadErrorCard(message = loadError, onRetry = onRetry)
            }
        }

        item("summary") {
            AccountSummaryCard(account)
        }

        item("timeline-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "سجل العمليات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "الأصل محفوظ، وكل دفعة أو عكس يظهر كسجل مستقل.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("debt-created") {
            TimelineCard(
                title = "تم إنشاء الدين",
                timestamp = account.ledger.header.openedAt,
                body = account.ledger.header.description,
                money = account.ledger.header.originalAmount,
            )
        }

        items(
            items = account.ledger.entries,
            key = { it.id.value },
        ) { entry ->
            when (entry) {
                is PaymentRecorded -> PaymentTimelineCard(
                    payment = entry,
                    isReversed = entry.id in reversedPaymentIds,
                    onReverse = { onOpenReversal(entry.id) },
                )

                is PaymentReversed -> ReversalTimelineCard(
                    reversal = entry,
                    payment = payments[entry.paymentId],
                )
            }
        }

        if (account.ledger.entries.isEmpty()) {
            item("no-ledger-entries") {
                Text(
                    text = "لم تُسجل دفعات بعد.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(account: AccountOverview) {
    val ledger = account.ledger
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (ledger.header.direction) {
                        DebtDirection.RECEIVABLE -> "لي عنده"
                        DebtDirection.PAYABLE -> "عليّ له"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (ledger.state) {
                        DebtState.OPEN -> "مفتوح"
                        DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
                        DebtState.SETTLED -> "مسدد بالكامل"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ledger.header.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            DetailMoneyRow("أصل الدين", ledger.header.originalAmount)
            DetailMoneyRow("المدفوع", ledger.paidAmount)
            DetailMoneyRow(
                label = "المتبقي",
                money = ledger.balance,
                valueModifier = Modifier.testTag("account-remaining"),
            )
            HorizontalDivider()
            MetadataRow("تاريخ الإنشاء", formatInstant(ledger.header.openedAt))
            ledger.header.dueDate?.let { dueDate ->
                MetadataRow("تاريخ الاستحقاق", "\u2066$dueDate\u2069")
            }
            account.dueReminder?.let { reminder ->
                MetadataRow("موعد التذكير", formatInstant(reminder.triggerAt))
                MetadataRow(
                    "حالة التذكير",
                    when (reminder.status) {
                        ReminderStatus.SCHEDULED -> "مجدول"
                        ReminderStatus.DELIVERED -> "تم إظهاره"
                        ReminderStatus.BLOCKED_PERMISSION -> "بانتظار إذن الإشعارات"
                        ReminderStatus.FAILED -> "ستُعاد المحاولة"
                        ReminderStatus.CANCELLED -> "ملغى"
                    },
                )
            }
            account.closedAt?.let { closedAt ->
                MetadataRow("تاريخ الإغلاق", formatInstant(closedAt))
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    account: AccountOverview,
    form: PaymentForm,
    review: PaymentReview?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (review == null) "تسجيل دفعة" else "تأكيد الدفعة")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = account.person.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (review == null) {
                    Text(
                        text = "المتبقي: ${formatMoney(account.ledger.balance)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = form.amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment-amount"),
                        label = { Text("مبلغ الدفعة") },
                        supportingText = {
                            Text("العملة: ${account.ledger.balance.currency.value}")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("بيان الدفعة — اختياري") },
                        minLines = 2,
                        maxLines = 3,
                        enabled = !isSaving,
                    )
                } else {
                    Text("راجع البيانات قبل إضافة الحدث المالي إلى السجل.")
                    HorizontalDivider()
                    DetailMoneyRow("قيمة الدفعة", review.amount)
                    DetailMoneyRow("المتبقي بعدها", review.remainingAfter)
                    form.note.trim().takeIf { it.isNotEmpty() }?.let { note ->
                        Text(
                            text = "البيان: $note",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (review == null) onReview else onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag(
                    if (review == null) "payment-review" else "payment-confirm",
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    when {
                        isSaving -> "جارٍ التسجيل"
                        review == null -> "مراجعة"
                        error != null -> "إعادة المحاولة"
                        else -> "تأكيد التسجيل"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = if (review == null) onDismiss else onEdit,
                enabled = !isSaving,
            ) {
                Text(if (review == null) "إلغاء" else "تعديل")
            }
        },
    )
}

@Composable
private fun ReversalDialog(
    payment: PaymentRecorded,
    reason: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عكس دفعة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "لن تُحذف الدفعة. سيُضاف حدث عكس موثق يعيد أثرها إلى الرصيد.",
                    lineHeight = 22.sp,
                )
                DetailMoneyRow("قيمة الدفعة", payment.amount)
                MetadataRow("تاريخ الدفعة", formatInstant(payment.paidAt))
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reversal-reason"),
                    label = { Text("سبب العكس") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving,
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("reversal-confirm"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ العكس" else if (error != null) "إعادة المحاولة" else "تأكيد العكس")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("إلغاء")
            }
        },
    )
}

@Composable
private fun PaymentTimelineCard(
    payment: PaymentRecorded,
    isReversed: Boolean,
    onReverse: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("دفعة مسجلة", fontWeight = FontWeight.Bold)
                Text(
                    text = if (isReversed) "معكوسة" else "فعّالة",
                    color = if (isReversed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            DetailMoneyRow("المبلغ", payment.amount)
            MetadataRow("وقت السداد", formatInstant(payment.paidAt))
            payment.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!isReversed) {
                TextButton(
                    onClick = onReverse,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("reverse-payment-${payment.id.value}"),
                ) {
                    Text("عكس الدفعة")
                }
            }
        }
    }
}

@Composable
private fun ReversalTimelineCard(
    reversal: PaymentReversed,
    payment: PaymentRecorded?,
) {
    TimelineCard(
        title = "تم عكس دفعة",
        timestamp = reversal.recordedAt,
        body = reversal.reason,
        money = payment?.amount,
    )
}

@Composable
private fun TimelineCard(
    title: String,
    timestamp: Instant,
    body: String?,
    money: Money?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            money?.let { DetailMoneyRow("المبلغ", it) }
            MetadataRow("الوقت", formatInstant(timestamp))
            body?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DetailMoneyRow(
    label: String,
    money: Money,
    valueModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(
            text = formatMoney(money),
            modifier = valueModifier,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, textAlign = TextAlign.End)
    }
}

@Composable
private fun LoadErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, lineHeight = 22.sp)
            Button(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text("إعادة المحاولة")
            }
        }
    }
}

private fun formatInstant(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.US)
    return "\u2066${formatter.format(instant.atZone(ZoneId.systemDefault()))}\u2069"
}

private fun AccountOperationNotice.toDisplayText(): String = when (this) {
    is AccountOperationNotice.PaymentRecordedNotice ->
        "تم تسجيل ${formatMoney(amount)} في حساب $personName."

    is AccountOperationNotice.PaymentReversedNotice ->
        "تم عكس ${formatMoney(amount)} في حساب $personName دون حذف السجل."
}
