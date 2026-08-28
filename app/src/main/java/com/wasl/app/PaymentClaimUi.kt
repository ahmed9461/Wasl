package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PaymentClaimsSectionHost(
    debtId: DebtId,
    canAddClaim: Boolean,
) {
    val context = LocalContext.current
    val application = context.applicationContext as WaslApplication
    val claimViewModel: PaymentClaimViewModel = viewModel(
        key = "payment-claims:${debtId.value}",
        factory = PaymentClaimViewModel.Factory(
            debtId = debtId,
            store = application.paymentClaimStore,
        ),
    )
    val state by claimViewModel.uiState.collectAsStateWithLifecycle()

    PaymentClaimsSection(
        state = state,
        canAddClaim = canAddClaim,
        onRetry = claimViewModel::retryLoad,
        onAddClaim = claimViewModel::openCreate,
        onResolve = claimViewModel::openResolution,
    )

    if (state.isCreateDialogOpen) {
        PaymentClaimDialog(
            form = state.form,
            isSaving = state.isSaving,
            error = state.saveError,
            onDismiss = claimViewModel::dismissCreate,
            onKindChange = claimViewModel::updateKind,
            onCustomDateChange = claimViewModel::updateCustomDate,
            onNoteChange = claimViewModel::updateNote,
            onConfirm = claimViewModel::confirmCreate,
        )
    }

    val resolution = state.resolution
    if (resolution != null) {
        PaymentClaimResolutionDialog(
            form = resolution,
            isSaving = state.isResolving,
            error = state.resolutionError,
            onDismiss = claimViewModel::dismissResolution,
            onNoteChange = claimViewModel::updateResolutionNote,
            onConfirm = claimViewModel::confirmResolution,
        )
    }

    LaunchedEffect(state.notice) {
        // The parent account screen owns the SnackbarHost. Keeping the notice in
        // state makes it testable now; a unified snackbar bridge can consume it later.
    }
}

@Composable
private fun PaymentClaimsSection(
    state: PaymentClaimUiState,
    canAddClaim: Boolean,
    onRetry: () -> Unit,
    onAddClaim: () -> Unit,
    onResolve: (String, PaymentClaimStatus) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "طالبني",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "سجل متى طُلب منك السداد وحدد متى تريد المتابعة. لا يغيّر هذا الرصيد أو سجل الدفعات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = state.claims.size.toString(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (canAddClaim) {
            OutlinedButton(
                onClick = onAddClaim,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-payment-claim"),
            ) {
                Text("تسجيل مطالبة بالسداد", fontWeight = FontWeight.Bold)
            }
        }

        state.loadError?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
                }
            }
        }

        if (state.claims.isEmpty() && state.loadError == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "لم تُسجل مطالبات لهذا الحساب بعد.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            state.claims.forEach { claim ->
                PaymentClaimCard(claim = claim, onResolve = onResolve)
            }
        }
    }
}

@Composable
private fun PaymentClaimCard(
    claim: PaymentClaimRecord,
    onResolve: (String, PaymentClaimStatus) -> Unit,
) {
    val statusText = when (claim.status) {
        PaymentClaimStatus.ACTIVE -> "قيد المتابعة"
        PaymentClaimStatus.RESOLVED -> "تم الحسم"
        PaymentClaimStatus.CANCELLED -> "ملغاة"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment-claim-card-${claim.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("مطالبة بالسداد", fontWeight = FontWeight.Bold)
                    Text(
                        paymentClaimFollowUpText(claim),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (claim.status == PaymentClaimStatus.ACTIVE) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                "طُلب السداد: ${paymentClaimInstantText(claim.claimedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            claim.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            claim.resolutionNote?.let {
                Text("ملاحظة الحسم: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (claim.status == PaymentClaimStatus.ACTIVE) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { onResolve(claim.id, PaymentClaimStatus.RESOLVED) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resolve-payment-claim-${claim.id}"),
                    ) {
                        Text("تم الحسم")
                    }
                    TextButton(
                        onClick = { onResolve(claim.id, PaymentClaimStatus.CANCELLED) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel-payment-claim-${claim.id}"),
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentClaimDialog(
    form: PaymentClaimForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onKindChange: (PaymentClaimFollowUpKind) -> Unit,
    onCustomDateChange: (LocalDate?) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    var showDatePicker by remember { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل «طالبني»") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "اختر متى تريد أن يعيد وَصل هذه المطالبة إلى انتباهك. التسجيل لا يُنشئ دفعة ولا يغير الاستحقاق.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("المتابعة", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    PaymentClaimFollowUpKind.entries.forEach { kind ->
                        FilterChip(
                            selected = form.followUpKind == kind,
                            onClick = { onKindChange(kind) },
                            label = { Text(paymentClaimKindLabel(kind)) },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("payment-claim-kind-${kind.name.lowercase()}")
                        )
                    }
                }
                if (form.followUpKind == PaymentClaimFollowUpKind.CUSTOM) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment-claim-custom-date"),
                    ) {
                        Text(form.customDate?.toString() ?: "اختيار التاريخ")
                    }
                }
                if (form.followUpKind == PaymentClaimFollowUpKind.SALARY) {
                    Text(
                        "لن يخمّن وَصل يوم الراتب. تبقى المطالبة موسومة «عند الراتب» حتى تحدد سياسة الراتب لاحقًا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ملاحظة — اختياري") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving,
                )
                error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("save-payment-claim"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else "حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") }
        },
    )

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = form.customDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onCustomDateChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun PaymentClaimResolutionDialog(
    form: PaymentClaimResolutionForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (form.status == PaymentClaimStatus.RESOLVED) "حسم المطالبة" else "إلغاء المطالبة")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "سيبقى السجل التاريخي محفوظًا، ولن يتغير الرصيد أو سجل الدفعات.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ملاحظة — اختياري") },
                    minLines = 2,
                    enabled = !isSaving,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm-payment-claim-resolution"),
            ) {
                Text(if (isSaving) "جارٍ الحفظ" else "تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("رجوع") }
        },
    )
}

private fun paymentClaimKindLabel(kind: PaymentClaimFollowUpKind): String = when (kind) {
    PaymentClaimFollowUpKind.TODAY -> "اليوم"
    PaymentClaimFollowUpKind.TOMORROW -> "غدًا"
    PaymentClaimFollowUpKind.SALARY -> "عند الراتب"
    PaymentClaimFollowUpKind.CUSTOM -> "تاريخ مخصص"
}

private fun paymentClaimFollowUpText(claim: PaymentClaimRecord): String = when (claim.followUpKind) {
    PaymentClaimFollowUpKind.TODAY -> "متابعة اليوم"
    PaymentClaimFollowUpKind.TOMORROW -> "متابعة غدًا"
    PaymentClaimFollowUpKind.SALARY -> "متابعة عند الراتب"
    PaymentClaimFollowUpKind.CUSTOM -> "متابعة ${claim.followUpDate ?: "بتاريخ مخصص"}"
}

private fun paymentClaimInstantText(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.US)
    return "\u2066${formatter.format(instant.atZone(ZoneId.systemDefault()))}\u2069"
}
