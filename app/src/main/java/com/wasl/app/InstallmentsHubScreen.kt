package com.wasl.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.data.InstallmentPlanRecord
import com.wasl.app.data.InstallmentRecord
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val installmentDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)

@Composable
fun InstallmentsHubRoute(
    repository: com.wasl.app.data.WaslRepository,
    store: com.wasl.app.data.InstallmentPlanStore,
    onBack: () -> Unit,
    onOpenAccount: (DebtId) -> Unit,
) {
    val viewModel: InstallmentsHubViewModel = viewModel(
        key = "installments-hub",
        factory = InstallmentsHubViewModel.Factory(repository, store),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InstallmentsHubScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retryLoad,
        onOpenEditor = viewModel::openEditor,
        onDismissEditor = viewModel::dismissEditor,
        onCountChange = viewModel::updateCount,
        onFirstDueDateChange = viewModel::updateFirstDueDate,
        onReasonChange = viewModel::updateReason,
        onSave = viewModel::savePlan,
        onOpenAccount = onOpenAccount,
        onNoticeShown = viewModel::clearNotice,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsHubScreen(
    state: InstallmentsHubUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEditor: (DebtId) -> Unit,
    onDismissEditor: () -> Unit,
    onCountChange: (String) -> Unit,
    onFirstDueDateChange: (LocalDate?) -> Unit,
    onReasonChange: (String) -> Unit,
    onSave: () -> Unit,
    onOpenAccount: (DebtId) -> Unit,
    onNoticeShown: () -> Unit,
) {
    BackHandler(onBack = onBack)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WaslTheme {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(state.notice) {
                val notice = state.notice ?: return@LaunchedEffect
                snackbar.showSnackbar(notice)
                onNoticeShown()
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.testTag("installments-hub"),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 3.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onBack) { Text("رجوع") }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "الأقساط",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "جدول السداد يتبع الرصيد المالي الفعلي للحساب",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    when {
                        state.isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("جارٍ قراءة خطط الأقساط…")
                            }
                        }

                        state.loadError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(state.loadError, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onRetry) { Text("إعادة المحاولة") }
                            }
                        }

                        else -> {
                            InstallmentsList(
                                modifier = Modifier.padding(padding),
                                accounts = state.accounts,
                                onOpenEditor = onOpenEditor,
                                onOpenAccount = onOpenAccount,
                            )
                        }
                    }
                }

                state.editor?.let { editor ->
                    InstallmentPlanEditorDialog(
                        form = editor,
                        isSaving = state.isSaving,
                        error = state.saveError,
                        onDismiss = onDismissEditor,
                        onCountChange = onCountChange,
                        onDateChange = onFirstDueDateChange,
                        onReasonChange = onReasonChange,
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallmentsList(
    modifier: Modifier,
    accounts: List<InstallmentHubAccount>,
    onOpenEditor: (DebtId) -> Unit,
    onOpenAccount: (DebtId) -> Unit,
) {
    if (accounts.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("لا توجد حسابات بعد.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "أنشئ حسابًا أولًا ثم عد إلى الأقساط.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "لا يوجد رصيد منفصل للقسط",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "أي دفعة أو عكس تسجله في الحساب ينعكس تلقائيًا على الأقساط بالترتيب، لذلك يبقى Ledger هو مصدر الحقيقة المالي الوحيد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        items(
            items = accounts,
            key = { it.account.ledger.header.id.value },
        ) { item ->
            InstallmentAccountCard(
                item = item,
                onOpenEditor = { onOpenEditor(item.account.ledger.header.id) },
                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
            )
        }
    }
}

@Composable
private fun InstallmentAccountCard(
    item: InstallmentHubAccount,
    onOpenEditor: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val account = item.account
    val debtId = account.ledger.header.id.value
    val plan = item.activePlan
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installment-plan-$debtId"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.person.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (account.ledger.header.direction == DebtDirection.RECEIVABLE) {
                            "لي عنده"
                        } else {
                            "عليّ له"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المتبقي", style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatInstallmentMoney(account.ledger.balance),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (plan == null) {
                Text(
                    "بدون خطة أقساط",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "يمكن توزيع أصل الدين على أقساط شهرية متساوية بدقة العملة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    modifier = Modifier.testTag("create-installment-plan-$debtId"),
                    onClick = onOpenEditor,
                ) {
                    Text("إنشاء خطة أقساط")
                }
            } else {
                ActivePlanSummary(plan)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        modifier = Modifier.testTag("revise-installment-plan-$debtId"),
                        onClick = onOpenEditor,
                    ) {
                        Text("تعديل الجدول")
                    }
                    Button(
                        modifier = Modifier.testTag("open-installment-account-$debtId"),
                        onClick = onOpenAccount,
                    ) {
                        Text("فتح الحساب للسداد")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePlanSummary(plan: InstallmentPlanRecord) {
    val totalMinor = plan.installments.sumOf { it.scheduledAmount.minorUnits }
    val paidMinor = plan.installments.sumOf { it.paidAmount.minorUnits }
    val progress = if (totalMinor == 0L) 0f else paidMinor.toFloat() / totalMinor.toFloat()
    val next = plan.installments.firstOrNull { !it.isPaid }
    val today = LocalDate.now()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "الخطة الحالية · نسخة ${plan.revisionNumber}",
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${plan.installments.count { it.isPaid }}/${plan.installments.size} مسدد",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    next?.let {
        val label = when {
            it.isOverdue(today) -> "القسط التالي متأخر"
            it.isDueToday(today) -> "القسط التالي اليوم"
            else -> "القسط التالي"
        }
        Text(
            "$label: ${formatInstallmentMoney(it.remainingAmount)} · ${installmentDateFormatter.format(it.dueDate)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (it.isOverdue(today)) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    } ?: Text("جميع أقساط الجدول مغطاة بالدفعات المسجلة.")

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
    plan.installments.take(6).forEach { installment ->
        InstallmentRow(installment)
    }
    if (plan.installments.size > 6) {
        Text(
            "و ${plan.installments.size - 6} أقساط أخرى",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstallmentRow(installment: InstallmentRecord) {
    val status = when {
        installment.isPaid -> "مسدد"
        installment.isPartiallyPaid -> "جزئي"
        installment.isOverdue(LocalDate.now()) -> "متأخر"
        else -> "قادم"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${installment.sequenceNumber}. ${installmentDateFormatter.format(installment.dueDate)}")
        Text(
            "$status · ${formatInstallmentMoney(installment.remainingAmount)}",
            color = if (status == "متأخر") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentPlanEditorDialog(
    form: InstallmentEditorForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCountChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onReasonChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(if (form.currentPlanId == null) "إنشاء خطة أقساط" else "تعديل خطة الأقساط")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "سيتم توزيع أصل الدين كاملًا بالتساوي، وأي فرق من أصغر وحدة نقدية يوزع على الأقساط الأولى.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installment-editor-count"),
                    value = form.count,
                    onValueChange = onCountChange,
                    label = { Text("عدد الأقساط") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSaving,
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12).forEach { count ->
                        FilterChip(
                            selected = form.count == count.toString(),
                            onClick = { onCountChange(count.toString()) },
                            label = { Text("$count") },
                            enabled = !isSaving,
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installment-editor-date"),
                    onClick = { showDatePicker = true },
                    enabled = !isSaving,
                ) {
                    Text(
                        form.firstDueDate?.let { "أول قسط: ${installmentDateFormatter.format(it)}" }
                            ?: "اختر تاريخ أول قسط",
                    )
                }
                if (form.currentPlanId != null) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = form.reason,
                        onValueChange = onReasonChange,
                        label = { Text("سبب التعديل — اختياري") },
                        enabled = !isSaving,
                        minLines = 2,
                    )
                    Text(
                        "لن تُحذف الخطة السابقة؛ تحفظ كنسخة تاريخية عند لحظة التعديل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("save-installment-plan"),
                onClick = onSave,
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("حفظ الخطة")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") }
        },
    )

    if (showDatePicker) {
        val selectedMillis = form.firstDueDate
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = pickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        onDateChange(date)
                        showDatePicker = false
                    },
                ) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatInstallmentMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val number = BigDecimal.valueOf(money.minorUnits)
        .movePointLeft(fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = true
    }
    return "${formatter.format(number)} ${money.currency.value}"
}
