package com.wasl.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
private enum class InstallmentFilter { ALL, REMAINING, PAID }

@Composable
fun InstallmentsHubRoute(
    repository: com.wasl.app.data.WaslRepository,
    store: com.wasl.app.data.InstallmentPlanStore,
    onBack: () -> Unit,
    onOpenAccount: (DebtId) -> Unit,
) {
    val vm: InstallmentsHubViewModel = viewModel(
        key = "installments-hub",
        factory = InstallmentsHubViewModel.Factory(repository, store),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    InstallmentsHubScreen(
        state = state,
        onBack = onBack,
        onRetry = vm::retryLoad,
        onOpenEditor = vm::openEditor,
        onDismissEditor = vm::dismissEditor,
        onCountChange = vm::updateCount,
        onFirstDueDateChange = vm::updateFirstDueDate,
        onReasonChange = vm::updateReason,
        onSave = vm::savePlan,
        onOpenAccount = onOpenAccount,
        onNoticeShown = vm::clearNotice,
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
    val snackbar = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(InstallmentFilter.ALL) }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            onNoticeShown()
        }
    }

    val plans = state.accounts.mapNotNull { it.activePlan }
    val totalInstallments = plans.sumOf { it.installments.size }
    val paidInstallments = plans.sumOf { plan -> plan.installments.count { it.isPaid } }
    val remainingInstallments = totalInstallments - paidInstallments
    val visibleAccounts = state.accounts.filter { item ->
        when (filter) {
            InstallmentFilter.ALL -> true
            InstallmentFilter.REMAINING -> item.activePlan?.installments?.any { !it.isPaid } == true
            InstallmentFilter.PAID -> item.activePlan?.installments?.all { it.isPaid } == true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("installments-hub"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("الأقساط", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("جارٍ تحميل الأقساط…")
            }
            state.loadError != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.loadError, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("إعادة المحاولة") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("installments-summary") {
                    InstallmentSummary(
                        total = totalInstallments,
                        paid = paidInstallments,
                        remaining = remainingInstallments,
                    )
                }
                item("installments-filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(filter == InstallmentFilter.ALL, { filter = InstallmentFilter.ALL }, { Text("الكل") }, modifier = Modifier.weight(1f))
                        FilterChip(filter == InstallmentFilter.REMAINING, { filter = InstallmentFilter.REMAINING }, { Text("متبقية") }, modifier = Modifier.weight(1f))
                        FilterChip(filter == InstallmentFilter.PAID, { filter = InstallmentFilter.PAID }, { Text("مسددة") }, modifier = Modifier.weight(1f))
                    }
                }
                if (visibleAccounts.isEmpty()) {
                    item("installments-empty") {
                        InfoCard(
                            if (state.accounts.isEmpty()) "لا توجد خطط أقساط حتى الآن. افتح حسابًا وأنشئ له خطة أقساط عند الحاجة."
                            else "لا توجد أقساط ضمن هذا التصنيف."
                        )
                    }
                } else {
                    items(visibleAccounts, key = { it.account.ledger.header.id.value }) { item ->
                        InstallmentAccountCard(item, onOpenEditor, onOpenAccount)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    state.editor?.let {
        InstallmentPlanEditorDialog(
            form = it,
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

@Composable
private fun InstallmentSummary(total: Int, paid: Int, remaining: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InstallmentMetric(total.toString(), "عدد الأقساط", MaterialTheme.colorScheme.tertiary)
            InstallmentMetric(paid.toString(), "مسددة", MaterialTheme.colorScheme.primary)
            InstallmentMetric(remaining.toString(), "متبقية", MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InstallmentMetric(value: String, label: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InstallmentAccountCard(
    item: InstallmentHubAccount,
    onOpenEditor: (DebtId) -> Unit,
    onOpenAccount: (DebtId) -> Unit,
) {
    val account = item.account
    val debtId = account.ledger.header.id
    val plan = item.activePlan
    val title = account.ledger.header.description?.takeIf { it.isNotBlank() }
        ?: "أقساط ${account.person.displayName}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("مع ${account.person.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatInstallmentMoney(account.ledger.balance), fontWeight = FontWeight.Bold)
            }
            if (plan == null) {
                Text("لا توجد خطة أقساط لهذا الحساب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val total = plan.installments.size
                val paid = plan.installments.count { it.isPaid }
                val progress = if (total == 0) 0f else paid.toFloat() / total
                val next = plan.installments.firstOrNull { !it.isPaid }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$paid/$total مسدد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    next?.let { Text("التالي ${it.dueDate.format(installmentDateFormatter)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenEditor(debtId) }, modifier = Modifier.weight(1f)) {
                    Text(if (plan == null) "إنشاء خطة" else "تعديل")
                }
                OutlinedButton(onClick = { onOpenAccount(debtId) }, modifier = Modifier.weight(1f)) { Text("الحساب") }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(message, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.currentPlanId == null) "خطة أقساط جديدة" else "تعديل خطة الأقساط") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    form.count,
                    onCountChange,
                    Modifier.fillMaxWidth().testTag("installment-count"),
                    label = { Text("عدد الأقساط") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedButton(onClick = { showPicker = true }, Modifier.fillMaxWidth(), enabled = !isSaving) {
                    Text(form.firstDueDate?.format(installmentDateFormatter) ?: "تاريخ أول قسط")
                }
                OutlinedTextField(
                    form.reason,
                    onReasonChange,
                    Modifier.fillMaxWidth(),
                    label = { Text("ملاحظة — اختياري") },
                    enabled = !isSaving,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "جارٍ الحفظ" else "حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") } },
    )
    if (showPicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = form.firstDueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } },
        ) { DatePicker(dateState) }
    }
}

private fun formatInstallmentMoney(money: Money): String {
    val digits = MoneyInputParser.fractionDigits(money.currency)
    val major = BigDecimal.valueOf(money.minorUnits, digits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    return ltrIsolate("${formatter.format(major)} ${money.currency.value}")
}
