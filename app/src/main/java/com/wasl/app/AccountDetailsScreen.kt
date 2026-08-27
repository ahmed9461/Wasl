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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DueReminderRequest
import com.wasl.app.data.DueScheduleAuditEvent
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.LedgerEntry
import com.wasl.domain.Money
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    onOpenReceiptDialog: (LedgerEntryId) -> Unit,
    onDismissReceiptDialog: () -> Unit,
    onReceiptIssuerNameChange: (String) -> Unit,
    onReceiptActivityNameChange: (String) -> Unit,
    onReceiptPhoneChange: (String) -> Unit,
    onReceiptFooterChange: (String) -> Unit,
    onConfirmPaymentReceipt: () -> Unit,
    onRetryPaymentReceipt: (String) -> Unit,
    onOpenReceipt: (IssuedDocumentRecord) -> Unit,
    onShareReceipt: (IssuedDocumentRecord) -> Unit,
    onReceiptReadyHandled: () -> Unit,
    onOpenDueSchedule: () -> Unit,
    onDismissDueSchedule: () -> Unit,
    onDueScheduleDateChange: (LocalDate?) -> Unit,
    onDueScheduleReminderChange: (Boolean) -> Unit,
    onDueScheduleStrongAlarmChange: (Boolean) -> Unit,
    onDueScheduleStrongAlarmTimeChange: (LocalTime) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onConfirmDueSchedule: () -> Unit,
    onOpenPaymentPromise: () -> Unit,
    onDismissPaymentPromise: () -> Unit,
    onPaymentPromiseDateChange: (LocalDate?) -> Unit,
    onPaymentPromiseNoteChange: (String) -> Unit,
    onConfirmPaymentPromise: () -> Unit,
    onOpenPaymentPromiseResolution: (String, PaymentPromiseStatus) -> Unit,
    onDismissPaymentPromiseResolution: () -> Unit,
    onPaymentPromiseResolutionNoteChange: (String) -> Unit,
    onConfirmPaymentPromiseResolution: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onNoticeShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.toDisplayText())
        onNoticeShown()
    }
    LaunchedEffect(state.receiptReadyToOpen) {
        val document = state.receiptReadyToOpen ?: return@LaunchedEffect
        onOpenReceipt(document)
        onReceiptReadyHandled()
    }

    val account = state.account
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = account?.person?.displayName ?: "تفاصيل الحساب",
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                        )
                        if (account != null) {
                            Text(
                                text = "تفاصيل الحساب وسجل العمليات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text("تسجيل دفعة", fontWeight = FontWeight.Bold)
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
                    onOpenReceiptDialog = onOpenReceiptDialog,
                    onRetryPaymentReceipt = onRetryPaymentReceipt,
                    onOpenReceipt = onOpenReceipt,
                    onShareReceipt = onShareReceipt,
                    retryingReceiptId = state.retryingReceiptId,
                    receiptRecoveryErrorDocumentId = state.receiptRecoveryErrorDocumentId,
                    onOpenDueSchedule = onOpenDueSchedule,
                    paymentPromises = state.paymentPromises,
                    paymentPromiseLoadError = state.paymentPromiseLoadError,
                    onOpenPaymentPromise = onOpenPaymentPromise,
                    onOpenPaymentPromiseResolution = onOpenPaymentPromiseResolution,
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

    val receiptPayment = account
        ?.ledger
        ?.entries
        ?.filterIsInstance<PaymentRecorded>()
        ?.firstOrNull { it.id == state.receiptPaymentId }
    if (state.receiptPaymentId != null && receiptPayment != null) {
        PaymentReceiptDialog(
            payment = receiptPayment,
            form = state.receiptIdentityForm,
            isLoadingIdentity = state.isLoadingReceiptIdentity,
            isSaving = state.isIssuingReceipt,
            error = state.receiptError,
            onDismiss = onDismissReceiptDialog,
            onIssuerNameChange = onReceiptIssuerNameChange,
            onActivityNameChange = onReceiptActivityNameChange,
            onPhoneChange = onReceiptPhoneChange,
            onFooterChange = onReceiptFooterChange,
            onConfirm = onConfirmPaymentReceipt,
        )
    }

    if (state.isDueScheduleDialogOpen && account != null) {
        DueScheduleDialog(
            form = state.dueScheduleForm,
            isSaving = state.isUpdatingDueSchedule,
            error = state.dueScheduleError,
            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onDismiss = onDismissDueSchedule,
            onDueDateChange = onDueScheduleDateChange,
            onReminderChange = onDueScheduleReminderChange,
            onStrongAlarmChange = onDueScheduleStrongAlarmChange,
            onStrongAlarmTimeChange = onDueScheduleStrongAlarmTimeChange,
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
            onConfirm = onConfirmDueSchedule,
        )
    }

    if (state.isPaymentPromiseDialogOpen && account != null) {
        PaymentPromiseDialog(
            form = state.paymentPromiseForm,
            isSaving = state.isCreatingPaymentPromise,
            error = state.paymentPromiseError,
            onDismiss = onDismissPaymentPromise,
            onDateChange = onPaymentPromiseDateChange,
            onNoteChange = onPaymentPromiseNoteChange,
            onConfirm = onConfirmPaymentPromise,
        )
    }

    val promiseResolutionForm = state.paymentPromiseResolutionForm
    val promiseToResolve = promiseResolutionForm?.let { form ->
        state.paymentPromises.firstOrNull { it.id == form.promiseId }
    }
    if (promiseResolutionForm != null && promiseToResolve != null) {
        PaymentPromiseResolutionDialog(
            promise = promiseToResolve,
            form = promiseResolutionForm,
            isSaving = state.isResolvingPaymentPromise,
            error = state.paymentPromiseResolutionError,
            onDismiss = onDismissPaymentPromiseResolution,
            onNoteChange = onPaymentPromiseResolutionNoteChange,
            onConfirm = onConfirmPaymentPromiseResolution,
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
    onOpenReceiptDialog: (LedgerEntryId) -> Unit,
    onRetryPaymentReceipt: (String) -> Unit,
    onOpenReceipt: (IssuedDocumentRecord) -> Unit,
    onShareReceipt: (IssuedDocumentRecord) -> Unit,
    retryingReceiptId: String?,
    receiptRecoveryErrorDocumentId: String?,
    onOpenDueSchedule: () -> Unit,
    paymentPromises: List<PaymentPromiseRecord>,
    paymentPromiseLoadError: String?,
    onOpenPaymentPromise: () -> Unit,
    onOpenPaymentPromiseResolution: (String, PaymentPromiseStatus) -> Unit,
) {
    val payments = account.ledger.entries
        .filterIsInstance<PaymentRecorded>()
        .associateBy { it.id }
    val reversedPaymentIds = account.ledger.reversedPaymentIds
    val documentsByPayment = account.issuedDocuments.associateBy { it.ledgerEntryId }
    val timelineItems = buildList<AccountTimelineItem> {
        account.ledger.entries.forEach { entry ->
            add(AccountTimelineItem.Ledger(entry))
        }
        account.dueScheduleAuditEvents.forEach { event ->
            add(AccountTimelineItem.DueSchedule(event))
        }
    }.sortedWith(compareBy<AccountTimelineItem> { it.timestamp }.thenBy { it.key })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 16.dp,
            end = 20.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (loadError != null) {
            item("load-error") {
                LoadErrorCard(message = loadError, onRetry = onRetry)
            }
        }

        item("summary") {
            AccountSummaryCard(account, onOpenDueSchedule)
        }

        if (account.ledger.header.direction == DebtDirection.PAYABLE) {
            item("payment-claims") {
                PaymentClaimsSectionHost(
                    debtId = account.ledger.header.id,
                    canAddClaim = !account.ledger.balance.isZero,
                )
            }
        }

        item("payment-promises") {
            PaymentPromisesSection(
                promises = paymentPromises,
                loadError = paymentPromiseLoadError,
                canAddPromise = !account.ledger.balance.isZero,
                onAddPromise = onOpenPaymentPromise,
                onResolvePromise = onOpenPaymentPromiseResolution,
            )
        }

        item("timeline-heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "سجل موثق",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            items = timelineItems,
            key = { it.key },
        ) { item ->
            when (item) {
                is AccountTimelineItem.Ledger -> when (val entry = item.entry) {
                    is PaymentRecorded -> PaymentTimelineCard(
                        payment = entry,
                        isReversed = entry.id in reversedPaymentIds,
                        document = documentsByPayment[entry.id],
                        isRetryingReceipt = retryingReceiptId == documentsByPayment[entry.id]?.id,
                        receiptRecoveryFailed = receiptRecoveryErrorDocumentId ==
                            documentsByPayment[entry.id]?.id,
                        onReverse = { onOpenReversal(entry.id) },
                        onIssueReceipt = { onOpenReceiptDialog(entry.id) },
                        onRetryReceipt = { documentId -> onRetryPaymentReceipt(documentId) },
                        onOpenReceipt = onOpenReceipt,
                        onShareReceipt = onShareReceipt,
                    )

                    is PaymentReversed -> ReversalTimelineCard(
                        reversal = entry,
                        payment = payments[entry.paymentId],
                    )
                }

                is AccountTimelineItem.DueSchedule -> DueScheduleAuditTimelineCard(item.event)
            }
        }

        if (account.ledger.entries.isEmpty()) {
            item("no-ledger-entries") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = "لم تُسجل دفعات بعد.",
                        modifier = Modifier.padding(18.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    account: AccountOverview,
    onOpenDueSchedule: () -> Unit,
) {
    val ledger = account.ledger
    val receivable = ledger.header.direction == DebtDirection.RECEIVABLE
    val heroContainer = if (receivable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val heroContent = if (receivable) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = heroContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = if (receivable) "لي عنده" else "عليّ له",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = when (ledger.state) {
                        DebtState.SETTLED -> MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                        DebtState.PARTIALLY_PAID -> MaterialTheme.colorScheme.tertiaryContainer
                        DebtState.OPEN -> MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
                    },
                ) {
                    Text(
                        text = when (ledger.state) {
                            DebtState.OPEN -> "مفتوح"
                            DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
                            DebtState.SETTLED -> "مسدد بالكامل"
                        },
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ledger.header.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroContent,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "المتبقي",
                    style = MaterialTheme.typography.labelLarge,
                    color = heroContent,
                )
                Text(
                    text = formatMoney(ledger.balance),
                    modifier = Modifier.testTag("account-remaining"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = heroContent,
                    textAlign = TextAlign.Start,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FinancialMetric(
                    modifier = Modifier.weight(1f),
                    label = "أصل الدين",
                    money = ledger.header.originalAmount,
                )
                FinancialMetric(
                    modifier = Modifier.weight(1f),
                    label = "المدفوع",
                    money = ledger.paidAmount,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    MetadataRow("تاريخ الإنشاء", formatInstant(ledger.header.openedAt))
                    MetadataRow(
                        "تاريخ الاستحقاق",
                        ledger.header.dueDate?.let(::formatDate) ?: "غير محدد",
                    )
                    account.dueReminder?.let { reminder ->
                        MetadataRow("موعد المتابعة الأساسي", formatInstant(reminder.triggerAt))
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
                    account.strongAlarm
                        ?.takeIf { it.status != ReminderStatus.CANCELLED }
                        ?.let { alarm ->
                            MetadataRow("المنبه القوي", formatInstant(alarm.triggerAt))
                        }
                    account.closedAt?.let { closedAt ->
                        MetadataRow("تاريخ الإغلاق", formatInstant(closedAt))
                    }
                }
            }

            if (!ledger.balance.isZero) {
                TextButton(
                    onClick = onOpenDueSchedule,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("edit-due-schedule"),
                ) {
                    Text(
                        if (ledger.header.dueDate == null) {
                            "إضافة موعد ومتابعة"
                        } else {
                            "تعديل الموعد والمتابعة"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialMetric(
    modifier: Modifier,
    label: String,
    money: Money,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(money),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DueScheduleDialog(
    form: DueScheduleForm,
    isSaving: Boolean,
    error: String?,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onDismiss: () -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onStrongAlarmTimeChange: (LocalTime) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onConfirm: () -> Unit,
) {
    var showDatePicker by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showStrongAlarmTimePicker by remember { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الموعد والمتابعة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "سيُحفظ أي تغيير في سجل العمليات دون تعديل أصل الدين أو الدفعات.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("تاريخ الاستحقاق", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit-due-date"),
                    ) {
                        Text(form.dueDate?.let(::formatDate) ?: "اختيار تاريخ")
                    }
                    if (form.dueDate != null) {
                        TextButton(
                            onClick = { onDueDateChange(null) },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("remove-due-date"),
                        ) {
                            Text("إزالة")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("متابعة ذكية للاستحقاق", fontWeight = FontWeight.SemiBold)
                        Text(
                            "قبل الموعد بيوم، يوم الموعد، بعد يومين، ثم أسبوعيًا حتى السداد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.remindOnDueDate,
                        onCheckedChange = onReminderChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("edit-due-reminder"),
                    )
                }
                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "ستُحفظ المتابعة، لكنها لن تظهر حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("منبه قوي إضافي", fontWeight = FontWeight.SemiBold)
                        Text(
                            "منبه دقيق في الوقت الذي تختاره يوم الاستحقاق. المتابعة الذكية تبقى كبديل آمن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.strongAlarmEnabled,
                        onCheckedChange = onStrongAlarmChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("edit-strong-alarm"),
                    )
                }
            if (form.strongAlarmEnabled) {
        OutlinedButton(
            onClick = { showStrongAlarmTimePicker = true },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit-strong-alarm-time"),
        ) {
            Text("وقت المنبه: ${formatLocalTime(form.strongAlarmTime)}")
        }
    }
                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "المنبه القوي محفوظ، لكن Android يحتاج إذن «المنبهات والتذكيرات» لتشغيله بدقة. ستستمر المتابعة الذكية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("exact-alarm-permission-warning"),
                        )
                        TextButton(
                            onClick = onRequestExactAlarmAccess,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("request-exact-alarm-access"),
                        ) {
                            Text("السماح بالمنبه الدقيق")
                        }
                    }
                }
                error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("save-due-schedule"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else if (error != null) "إعادة المحاولة" else "حفظ التعديل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("إلغاء")
            }
        },
    )

if (showStrongAlarmTimePicker) {
val timePickerState = rememberTimePickerState(
    initialHour = form.strongAlarmTime.hour,
    initialMinute = form.strongAlarmTime.minute,
    is24Hour = true,
)
AlertDialog(
    onDismissRequest = { showStrongAlarmTimePicker = false },
    title = { Text("وقت المنبه القوي") },
    text = { TimePicker(state = timePickerState) },
    confirmButton = {
        TextButton(
            onClick = {
                onStrongAlarmTimeChange(
                    LocalTime.of(timePickerState.hour, timePickerState.minute),
                )
                showStrongAlarmTimePicker = false
            },
            modifier = Modifier.testTag("confirm-strong-alarm-time"),
        ) { Text("اختيار") }
    },
    dismissButton = {
        TextButton(onClick = { showStrongAlarmTimePicker = false }) { Text("إلغاء") }
    },
)
}

    if (showDatePicker) {
        val initialSelection = form.dueDate
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialSelection,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            onDueDateChange(
                                Instant.ofEpochMilli(selected)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text("اختيار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            },
        ) {
            DatePicker(state = pickerState)
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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (review == null) "تسجيل دفعة" else "تأكيد الدفعة")
                Text(
                    text = if (review == null) {
                        "أضف السداد وسيُحفظ كحدث مستقل في السجل."
                    } else {
                        "راجع الأرقام قبل اعتماد العملية."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = account.person.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "المتبقي: ${formatMoney(account.ledger.balance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (review == null) {
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
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("بيان الدفعة — اختياري") },
                        minLines = 2,
                        maxLines = 3,
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
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
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
private fun PaymentReceiptDialog(
    payment: PaymentRecorded,
    form: ReceiptIdentityForm,
    isLoadingIdentity: Boolean,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onIssuerNameChange: (String) -> Unit,
    onActivityNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onFooterChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إصدار إيصال سداد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "ستُثبّت بيانات الدفعة والهوية في نسخة تاريخية لا تتغير لاحقًا.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DetailMoneyRow("مبلغ السداد", payment.amount)
                MetadataRow("وقت السداد", formatInstant(payment.paidAt))
                HorizontalDivider()
                OutlinedTextField(
                    value = form.displayName,
                    onValueChange = onIssuerNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt-issuer-name"),
                    label = { Text("اسم مُصدر الإيصال") },
                    singleLine = true,
                    enabled = !isSaving && !isLoadingIdentity,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = form.activityName,
                    onValueChange = onActivityNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("النشاط — اختياري") },
                    singleLine = true,
                    enabled = !isSaving && !isLoadingIdentity,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = form.phone,
                    onValueChange = onPhoneChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الهاتف — اختياري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = !isSaving && !isLoadingIdentity,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = form.footerText,
                    onValueChange = onFooterChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("عبارة الإيصال — اختياري") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving && !isLoadingIdentity,
                    shape = MaterialTheme.shapes.medium,
                )
                if (isLoadingIdentity) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("جارٍ تحميل الهوية المحفوظة")
                    }
                }
                error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving && !isLoadingIdentity,
                modifier = Modifier.testTag("receipt-confirm"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ تجهيز PDF" else if (error != null) "إعادة المحاولة" else "إصدار وفتح")
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
                    shape = MaterialTheme.shapes.medium,
                )
                error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
    document: IssuedDocumentRecord?,
    isRetryingReceipt: Boolean,
    receiptRecoveryFailed: Boolean,
    onReverse: () -> Unit,
    onIssueReceipt: () -> Unit,
    onRetryReceipt: (String) -> Unit,
    onOpenReceipt: (IssuedDocumentRecord) -> Unit,
    onShareReceipt: (IssuedDocumentRecord) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("دفعة مسجلة", fontWeight = FontWeight.Bold)
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isReversed) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        text = if (isReversed) "معكوسة" else "فعّالة",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = if (isReversed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            DetailMoneyRow("المبلغ", payment.amount)
            MetadataRow("وقت السداد", formatInstant(payment.paidAt))
            payment.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            document?.let { receipt ->
                HorizontalDivider()
                MetadataRow("إيصال السداد", receipt.documentNumber)
                when (receipt.status) {
                    DocumentStatus.READY -> {
                        if (isReversed) {
                            Text(
                                "صدر هذا الإيصال قبل عكس الدفعة، وتبقى نسخته التاريخية محفوظة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(
                                onClick = { onShareReceipt(receipt) },
                                modifier = Modifier.testTag("share-receipt-${receipt.id}"),
                            ) {
                                Text("مشاركة")
                            }
                            TextButton(
                                onClick = { onOpenReceipt(receipt) },
                                modifier = Modifier.testTag("open-receipt-${receipt.id}"),
                            ) {
                                Text("فتح PDF")
                            }
                        }
                    }

                    DocumentStatus.PENDING_PDF, DocumentStatus.FAILED -> {
                        Text(
                            if (receiptRecoveryFailed) {
                                "تعذر تجهيز PDF. بيانات الإيصال محفوظة ويمكن إعادة المحاولة."
                            } else {
                                "بيانات الإيصال محفوظة، وملف PDF يحتاج إلى تجهيز."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (receiptRecoveryFailed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        TextButton(
                            onClick = { onRetryReceipt(receipt.id) },
                            enabled = !isRetryingReceipt,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("retry-receipt-${receipt.id}"),
                        ) {
                            if (isRetryingReceipt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                            }
                            Text(if (isRetryingReceipt) "جارٍ التجهيز" else "إعادة تجهيز PDF")
                        }
                    }
                }
            }
            if (!isReversed) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (document == null) {
                        TextButton(
                            onClick = onIssueReceipt,
                            modifier = Modifier.testTag("issue-receipt-${payment.id.value}"),
                        ) {
                            Text("إصدار إيصال")
                        }
                    }
                    TextButton(
                        onClick = onReverse,
                        modifier = Modifier.testTag("reverse-payment-${payment.id.value}"),
                    ) {
                        Text("عكس الدفعة")
                    }
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
private fun DueScheduleAuditTimelineCard(event: DueScheduleAuditEvent) {
    val title = when {
        event.before.dueDate == null && event.after.dueDate != null ->
            "تم تعيين تاريخ الاستحقاق"

        event.before.dueDate != null && event.after.dueDate == null ->
            "تم إلغاء تاريخ الاستحقاق"

        event.before.dueDate != event.after.dueDate ->
            "تم تعديل تاريخ الاستحقاق"

        else -> "تم تعديل متابعة الاستحقاق"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("due-schedule-audit-${event.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            MetadataRow("الوقت", formatInstant(event.occurredAt))
            MetadataRow(
                "الموعد السابق",
                event.before.dueDate?.let(::formatDate) ?: "غير محدد",
            )
            MetadataRow(
                "الموعد الجديد",
                event.after.dueDate?.let(::formatDate) ?: "غير محدد",
            )
            MetadataRow("التذكير السابق", reminderSummary(event.before.dueReminder))
            MetadataRow("التذكير الجديد", reminderSummary(event.after.dueReminder))
        }
    }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
            Text(
                text = message,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
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

private fun formatLocalTime(time: LocalTime): String =
    "%02d:%02d".format(Locale.US, time.hour, time.minute)

private fun formatDate(date: LocalDate): String = "\u2066$date\u2069"

private fun reminderSummary(reminder: DueReminderRequest?): String =
    reminder?.let { "متابعة ذكية — ${formatInstant(it.triggerAt)}" } ?: "غير مفعل"

private sealed interface AccountTimelineItem {
    val key: String
    val timestamp: Instant

    data class Ledger(val entry: LedgerEntry) : AccountTimelineItem {
        override val key: String = "ledger:${entry.id.value}"
        override val timestamp: Instant = entry.recordedAt
    }

    data class DueSchedule(val event: DueScheduleAuditEvent) : AccountTimelineItem {
        override val key: String = "due-schedule:${event.id}"
        override val timestamp: Instant = event.occurredAt
    }
}

private fun AccountOperationNotice.toDisplayText(): String = when (this) {
    is AccountOperationNotice.PaymentRecordedNotice ->
        "تم تسجيل ${formatMoney(amount)} في حساب $personName."

    is AccountOperationNotice.PaymentReversedNotice ->
        "تم عكس ${formatMoney(amount)} في حساب $personName دون حذف السجل."

    is AccountOperationNotice.DueScheduleUpdatedNotice -> when {
        platformSyncPending ->
            "تم حفظ الموعد في حساب $personName، وستُستكمل مزامنة التذكير تلقائيًا."

        dueDate == null -> "تم إلغاء موعد الاستحقاق والتذكير في حساب $personName."
        strongAlarmEnabled && reminderEnabled ->
            "تم تحديث موعد الاستحقاق وتفعيل المتابعة الذكية والمنبه القوي في حساب $personName."
        reminderEnabled -> "تم تحديث موعد الاستحقاق وتفعيل المتابعة الذكية في حساب $personName."
        else -> "تم تحديث موعد الاستحقاق دون متابعة في حساب $personName."
    }

    is AccountOperationNotice.PaymentReceiptIssuedNotice ->
        "تم تجهيز إيصال السداد $documentNumber وحفظه في سجل الدفعة."

    is AccountOperationNotice.PaymentPromiseCreatedNotice ->
        "تم تسجيل وعد بالسداد بتاريخ ${formatDate(promisedDate)} في حساب $personName."

    is AccountOperationNotice.PaymentPromiseResolvedNotice -> when (status) {
        PaymentPromiseStatus.KEPT ->
            "تم تسجيل الوفاء بوعد ${formatDate(promisedDate)} في حساب $personName."
        PaymentPromiseStatus.MISSED ->
            "تم تسجيل أن وعد ${formatDate(promisedDate)} لم يُنفذ في حساب $personName."
        PaymentPromiseStatus.CANCELLED ->
            "تم إلغاء وعد ${formatDate(promisedDate)} مع الاحتفاظ به في سجل $personName."
        PaymentPromiseStatus.PENDING ->
            "تم تحديث وعد ${formatDate(promisedDate)} في حساب $personName."
    }
}
