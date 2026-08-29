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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PaymentPromisesSection(
    promises: List<PaymentPromiseRecord>,
    loadError: String?,
    canAddPromise: Boolean,
    onAddPromise: () -> Unit,
    onResolvePromise: (String, PaymentPromiseStatus) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "وعود السداد",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "الوعد مستقل عن تاريخ الاستحقاق، وتبقى كل الوعود السابقة محفوظة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = promises.size.toString(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (canAddPromise) {
            OutlinedButton(
                onClick = onAddPromise,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-payment-promise"),
            ) {
                Text("إضافة وعد بالسداد", fontWeight = FontWeight.Bold)
            }
        }

        loadError?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (promises.isEmpty() && loadError == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "لا توجد وعود مسجلة لهذا الحساب بعد.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            promises.forEach { promise ->
                PaymentPromiseCard(
                    promise = promise,
                    onResolve = { status -> onResolvePromise(promise.id, status) },
                )
            }
        }
    }
}

@Composable
private fun PaymentPromiseCard(
    promise: PaymentPromiseRecord,
    onResolve: (PaymentPromiseStatus) -> Unit,
) {
    val today = LocalDate.now()
    val statusLabel = when (promise.status) {
        PaymentPromiseStatus.PENDING -> if (promise.isOverdue(today)) "متأخر — بانتظار الحسم" else "قادم"
        PaymentPromiseStatus.KEPT -> "تم الوفاء"
        PaymentPromiseStatus.MISSED -> "لم يُنفذ"
        PaymentPromiseStatus.CANCELLED -> "ملغي"
    }
    val container = when (promise.status) {
        PaymentPromiseStatus.PENDING -> if (promise.isOverdue(today)) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
        PaymentPromiseStatus.KEPT -> MaterialTheme.colorScheme.primaryContainer
        PaymentPromiseStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
        PaymentPromiseStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (promise.status) {
        PaymentPromiseStatus.PENDING -> if (promise.isOverdue(today)) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }
        PaymentPromiseStatus.KEPT -> MaterialTheme.colorScheme.onPrimaryContainer
        PaymentPromiseStatus.MISSED -> MaterialTheme.colorScheme.onErrorContainer
        PaymentPromiseStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment-promise-card-${promise.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("وعد بالسداد", fontWeight = FontWeight.Bold)
                    Text(
                        text = promiseDateText(promise.promisedDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = container,
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                }
            }

            promise.note?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "سُجل: ${promiseInstantText(promise.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (promise.status != PaymentPromiseStatus.PENDING) {
                promise.resolvedAt?.let { resolvedAt ->
                    Text(
                        text = "حُسم: ${promiseInstantText(resolvedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                promise.resolutionNote?.let { note ->
                    Text(
                        text = "ملاحظة الحسم: $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (promise.status == PaymentPromiseStatus.PENDING) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("payment-promise-actions-${promise.id}"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onResolve(PaymentPromiseStatus.KEPT) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-kept-${promise.id}"),
                    ) { Text("تم الوفاء", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onResolve(PaymentPromiseStatus.MISSED) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-missed-${promise.id}"),
                    ) { Text("لم يُنفذ", maxLines = 1) }
                    TextButton(
                        onClick = { onResolve(PaymentPromiseStatus.CANCELLED) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-cancelled-${promise.id}"),
                    ) { Text("إلغاء", maxLines = 1) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentPromiseDialog(
    form: PaymentPromiseForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("وعد بالسداد")
                Text(
                    text = "سجّل ما تم الاتفاق عليه دون تغيير تاريخ الدين الأصلي.",
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
                Text("تاريخ الوعد", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment-promise-date"),
                ) {
                    Text(form.promisedDate?.let(::promiseDateText) ?: "اختيار تاريخ")
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment-promise-note"),
                    label = { Text("ملاحظة — اختياري") },
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
                modifier = Modifier.testTag("save-payment-promise"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else if (error != null) "إعادة المحاولة" else "حفظ الوعد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("إلغاء")
            }
        },
    )

    if (showDatePicker) {
        val initialSelection = form.promisedDate
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
                            onDateChange(
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
internal fun PaymentPromiseResolutionDialog(
    promise: PaymentPromiseRecord,
    form: PaymentPromiseResolutionForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (form.status) {
        PaymentPromiseStatus.KEPT -> "تأكيد الوفاء بالوعد"
        PaymentPromiseStatus.MISSED -> "تسجيل عدم تنفيذ الوعد"
        PaymentPromiseStatus.CANCELLED -> "إلغاء الوعد"
        PaymentPromiseStatus.PENDING -> "حسم الوعد"
    }
    val description = when (form.status) {
        PaymentPromiseStatus.KEPT -> "سيبقى الوعد في التاريخ مع تسجيل أنه تم الوفاء به."
        PaymentPromiseStatus.MISSED -> "سيبقى الوعد في التاريخ مع تسجيل أنه لم يُنفذ."
        PaymentPromiseStatus.CANCELLED -> "سيبقى الوعد في التاريخ بحالة ملغي، ولن يتم حذفه."
        PaymentPromiseStatus.PENDING -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                        Text("وعد ${promiseDateText(promise.promisedDate)}", fontWeight = FontWeight.Bold)
                        promise.note?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment-promise-resolution-note"),
                    label = { Text("ملاحظة الحسم — اختياري") },
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
                modifier = Modifier.testTag("confirm-payment-promise-resolution"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else if (error != null) "إعادة المحاولة" else "تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("رجوع")
            }
        },
    )
}

private fun promiseDateText(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)
    return "\u2066${formatter.format(date)}\u2069"
}

private fun promiseInstantText(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.US)
    return "\u2066${formatter.format(instant.atZone(ZoneId.systemDefault()))}\u2069"
}
