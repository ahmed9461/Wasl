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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.data.InstallmentPlanRecord
import com.wasl.app.data.InstallmentRecord
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
    val viewModel: InstallmentsHubViewModel = viewModel(key = "installments-hub", factory = InstallmentsHubViewModel.Factory(repository, store))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InstallmentsHubScreen(state, onBack, viewModel::retryLoad, viewModel::openEditor, viewModel::dismissEditor,
        viewModel::updateCount, viewModel::updateFirstDueDate, viewModel::updateReason, viewModel::savePlan, onOpenAccount, viewModel::clearNotice)
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
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text("الأقساط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("خطط السداد مرتبطة دائمًا بالرصيد الحقيقي في Ledger",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Column(Modifier.fillMaxSize().padding(padding), Arrangement.Center, Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("جارٍ قراءة خطط الأقساط…")
            }
            state.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(state.loadError, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("إعادة المحاولة") }
            }
            else -> InstallmentsList(Modifier.padding(padding), state.accounts, onOpenEditor, onOpenAccount)
        }
    }
    state.editor?.let { InstallmentPlanEditorDialog(it, state.isSaving, state.saveError, onDismissEditor, onCountChange,
        onFirstDueDateChange, onReasonChange, onSave) }
}

@Composable
private fun InstallmentsList(modifier: Modifier, accounts: List<InstallmentHubAccount>, onOpenEditor: (DebtId) -> Unit, onOpenAccount: (DebtId) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (accounts.isEmpty()) item { InfoCard("لا توجد حسابات مفتوحة تحتاج خطة أقساط.") }
        items(accounts, key = { it.debtId.value }) { account -> InstallmentAccountCard(account, onOpenEditor, onOpenAccount) }
    }
}

@Composable
private fun InstallmentAccountCard(account: InstallmentHubAccount, onOpenEditor: (DebtId) -> Unit, onOpenAccount: (DebtId) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(account.personName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (account.direction == DebtDirection.RECEIVABLE) "لي عنده" else "عليّ له", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.End) { Text("المتبقي", style = MaterialTheme.typography.labelMedium)
                    Text(formatInstallmentMoney(account.balance), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold) }
            }
            account.plan?.let { plan ->
                HorizontalDivider(); Text("${plan.installments.size} أقساط", fontWeight = FontWeight.SemiBold)
                val paid = plan.installments.count { it.paidMinorUnits >= it.amountMinorUnits }
                LinearProgressIndicator(progress = { if (plan.installments.isEmpty()) 0f else paid.toFloat() / plan.installments.size }, Modifier.fillMaxWidth())
                Text("$paid من ${plan.installments.size} مكتملة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("بدون خطة أقساط", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenEditor(account.debtId) }, Modifier.weight(1f)) { Text(if (account.plan == null) "إنشاء خطة" else "تعديل الخطة") }
                OutlinedButton(onClick = { onOpenAccount(account.debtId) }, Modifier.weight(1f)) { Text("فتح الحساب") }
            }
        }
    }
}

@Composable private fun InfoCard(message: String) { Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(18.dp)) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentPlanEditorDialog(form: InstallmentPlanEditorState, isSaving: Boolean, error: String?, onDismiss: () -> Unit,
    onCountChange: (String) -> Unit, onDateChange: (LocalDate?) -> Unit, onReasonChange: (String) -> Unit, onSave: () -> Unit) {
    val picker = remember { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (form.existingPlan == null) "إنشاء خطة أقساط" else "تعديل خطة الأقساط") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("سيتم توزيع الرصيد الحالي بدقة على الأقساط، وأي دفعة لاحقة تنعكس تلقائيًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(form.installmentCount, onCountChange, Modifier.fillMaxWidth().testTag("installment-count"), label = { Text("عدد الأقساط") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !isSaving)
            OutlinedButton(onClick = { picker.value = true }, Modifier.fillMaxWidth(), enabled = !isSaving) {
                Text(form.firstDueDate?.format(installmentDateFormatter) ?: "اختيار تاريخ أول قسط")
            }
            OutlinedTextField(form.reason, onReasonChange, Modifier.fillMaxWidth(), label = { Text("ملاحظة — اختياري") }, enabled = !isSaving)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "جارٍ الحفظ" else "حفظ الخطة") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") } })
    if (picker.value) {
        val dateState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = form.firstDueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { picker.value = false }, confirmButton = { TextButton(onClick = {
            dateState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; picker.value = false
        }) { Text("اختيار") } }, dismissButton = { TextButton(onClick = { picker.value = false }) { Text("إلغاء") } }) { DatePicker(dateState) }
    }
}

private fun formatInstallmentMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency); val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply { isGroupingUsed = true; minimumFractionDigits = fractionDigits; maximumFractionDigits = fractionDigits }
    return ltrIsolate("${formatter.format(major)} ${money.currency.value}")
}