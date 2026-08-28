package com.wasl.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun InstallmentsHubRoute(repository: com.wasl.app.data.WaslRepository, store: com.wasl.app.data.InstallmentPlanStore,
    onBack: () -> Unit, onOpenAccount: (DebtId) -> Unit) {
    val vm: InstallmentsHubViewModel = viewModel(key = "installments-hub", factory = InstallmentsHubViewModel.Factory(repository, store))
    val state by vm.uiState.collectAsStateWithLifecycle()
    InstallmentsHubScreen(state, onBack, vm::retryLoad, vm::openEditor, vm::dismissEditor, vm::updateCount,
        vm::updateFirstDueDate, vm::updateReason, vm::savePlan, onOpenAccount, vm::clearNotice)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsHubScreen(state: InstallmentsHubUiState, onBack: () -> Unit, onRetry: () -> Unit,
    onOpenEditor: (DebtId) -> Unit, onDismissEditor: () -> Unit, onCountChange: (String) -> Unit,
    onFirstDueDateChange: (LocalDate?) -> Unit, onReasonChange: (String) -> Unit, onSave: () -> Unit,
    onOpenAccount: (DebtId) -> Unit, onNoticeShown: () -> Unit) {
    BackHandler(onBack = onBack)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); onNoticeShown() } }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("installments-hub"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("رجوع") }
                Column(Modifier.weight(1f)) {
                    Text("الأقساط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("خطط السداد تتبع الرصيد المالي الفعلي للحساب", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } },
    ) { padding -> when {
        state.isLoading -> Column(Modifier.fillMaxSize().padding(padding), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("جارٍ قراءة خطط الأقساط…") }
        state.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(state.loadError, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("إعادة المحاولة") } }
        else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.accounts.isEmpty()) item { InfoCard("لا توجد حسابات مفتوحة تحتاج خطة أقساط.") }
            items(state.accounts, key = { it.account.ledger.header.id.value }) { item -> InstallmentAccountCard(item, onOpenEditor, onOpenAccount) }
        }
    } }
    state.editor?.let { InstallmentPlanEditorDialog(it, state.isSaving, state.saveError, onDismissEditor, onCountChange,
        onFirstDueDateChange, onReasonChange, onSave) }
}

@Composable
private fun InstallmentAccountCard(item: InstallmentHubAccount, onOpenEditor: (DebtId) -> Unit, onOpenAccount: (DebtId) -> Unit) {
    val account = item.account; val debtId = account.ledger.header.id
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(account.person.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (account.ledger.header.direction == DebtDirection.RECEIVABLE) "لي عنده" else "عليّ له", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.End) { Text("المتبقي", style = MaterialTheme.typography.labelMedium)
                    Text(formatInstallmentMoney(account.ledger.balance), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold) }
            }
            item.activePlan?.let { plan ->
                HorizontalDivider(); val total = plan.installments.size; val paid = plan.installments.count { it.isPaid }
                Text("$total أقساط", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { if (total == 0) 0f else paid.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text("$paid من $total مكتملة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("بدون خطة أقساط", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenEditor(debtId) }, Modifier.weight(1f)) { Text(if (item.activePlan == null) "إنشاء خطة" else "تعديل الخطة") }
                OutlinedButton(onClick = { onOpenAccount(debtId) }, Modifier.weight(1f)) { Text("فتح الحساب") }
            }
        }
    }
}

@Composable private fun InfoCard(message: String) { Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(18.dp)) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentPlanEditorDialog(form: InstallmentEditorForm, isSaving: Boolean, error: String?, onDismiss: () -> Unit,
    onCountChange: (String) -> Unit, onDateChange: (LocalDate?) -> Unit, onReasonChange: (String) -> Unit, onSave: () -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (form.currentPlanId == null) "إنشاء خطة أقساط" else "تعديل خطة الأقساط") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("يوزع وَصل الرصيد بدقة، وأي دفعة لاحقة تنعكس تلقائيًا على الخطة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(form.count, onCountChange, Modifier.fillMaxWidth().testTag("installment-count"), label = { Text("عدد الأقساط") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !isSaving)
            OutlinedButton(onClick = { showPicker = true }, Modifier.fillMaxWidth(), enabled = !isSaving) { Text(form.firstDueDate?.format(installmentDateFormatter) ?: "اختيار تاريخ أول قسط") }
            OutlinedTextField(form.reason, onReasonChange, Modifier.fillMaxWidth(), label = { Text("ملاحظة — اختياري") }, enabled = !isSaving)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "جارٍ الحفظ" else "حفظ الخطة") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") } })
    if (showPicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = form.firstDueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = { TextButton(onClick = {
            dateState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; showPicker = false
        }) { Text("اختيار") } }, dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } }) { DatePicker(dateState) }
    }
}

private fun formatInstallmentMoney(money: Money): String {
    val digits = MoneyInputParser.fractionDigits(money.currency); val major = BigDecimal.valueOf(money.minorUnits, digits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply { isGroupingUsed = true; minimumFractionDigits = digits; maximumFractionDigits = digits }
    return ltrIsolate("${formatter.format(major)} ${money.currency.value}")
}