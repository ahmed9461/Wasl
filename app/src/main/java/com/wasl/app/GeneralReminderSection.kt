package com.wasl.app

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun GeneralReminderSection(
    state: GeneralReminderUiState,
    canConfigure: Boolean,
    notificationsAvailable: Boolean,
    onRetryLoad: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onFrequencyChange: (GeneralReminderUiFrequency) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val activeReminder = state.reminder?.takeIf { it.status != ReminderStatus.CANCELLED }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("general-reminder-section"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "تذكير متابعة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "مستقل عن تاريخ الاستحقاق ولا يغيّر الرصيد أو سجل الدفعات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("جارٍ قراءة التذكير المحفوظ…")
                    }
                }

                state.loadError != null -> {
                    Text(
                        text = state.loadError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRetryLoad,
                    ) {
                        Text("إعادة المحاولة")
                    }
                }

                activeReminder != null -> {
                    GeneralReminderSummary(activeReminder)
                    when (activeReminder.status) {
                        ReminderStatus.BLOCKED_PERMISSION -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("general-reminder-permission-warning"),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "التذكير محفوظ لكنه ينتظر السماح بإشعارات وَصل.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("general-reminder-request-notifications"),
                                        onClick = onRequestNotificationAccess,
                                    ) {
                                        Text("السماح بالإشعارات")
                                    }
                                }
                            }
                        }

                        ReminderStatus.FAILED -> Text(
                            "تعذرت مزامنة آخر محاولة؛ سيحاول وَصل الاسترداد تلقائيًا.",
                            color = MaterialTheme.colorScheme.error,
                        )

                        ReminderStatus.DELIVERED -> Text(
                            "تم عرض هذا التذكير. يمكنك جدولة موعد جديد بنفس السجل.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        ReminderStatus.SCHEDULED -> Unit
                        ReminderStatus.CANCELLED -> Unit
                    }
                }

                else -> Text(
                    if (canConfigure) {
                        "لا يوجد تذكير متابعة عام لهذا الحساب."
                    } else {
                        "الحساب مسدد بالكامل؛ لا يمكن إضافة تذكير متابعة جديد."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.platformSyncPending) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("general-reminder-sync-pending"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "تم حفظ التغيير، ومزامنة الجدولة مع Android قيد الاسترداد.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            if (canConfigure || activeReminder != null) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit-general-reminder"),
                    onClick = onOpen,
                    enabled = !state.isSaving,
                ) {
                    Text(
                        if (activeReminder == null) {
                            "إضافة تذكير متابعة"
                        } else {
                            "تعديل تذكير المتابعة"
                        },
                    )
                }
            }
        }
    }

    if (state.isDialogOpen) {
        GeneralReminderDialog(
            state = state,
            notificationsAvailable = notificationsAvailable,
            canCancel = activeReminder != null,
            onDismiss = onDismiss,
            onDateChange = onDateChange,
            onTimeChange = onTimeChange,
            onFrequencyChange = onFrequencyChange,
            onSave = onSave,
            onCancel = onCancel,
            onRequestNotificationAccess = onRequestNotificationAccess,
        )
    }
}

@Composable
private fun GeneralReminderSummary(reminder: GeneralReminderRecord) {
    val local = reminder.triggerAt.atZone(reminder.zoneId)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GeneralReminderMetadataRow(
                label = "الموعد القادم",
                value = "${DATE_FORMATTER.format(local)} — ${TIME_FORMATTER.format(local)}",
            )
            GeneralReminderMetadataRow(
                label = "التكرار",
                value = reminder.repeatRule?.frequency.toDisplayText(),
            )
            GeneralReminderMetadataRow(
                label = "الحالة",
                value = reminder.status.toGeneralReminderStatusText(),
            )
        }
    }
}

@Composable
private fun GeneralReminderMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralReminderDialog(
    state: GeneralReminderUiState,
    notificationsAvailable: Boolean,
    canCancel: Boolean,
    onDismiss: () -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onFrequencyChange: (GeneralReminderUiFrequency) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val form = state.form

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تذكير متابعة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "هذا الموعد مستقل عن تاريخ الاستحقاق. يمكنك استخدامه للمتابعة مرة واحدة أو بشكل متكرر.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("التاريخ", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("general-reminder-date"),
                    enabled = !state.isSaving,
                    onClick = { showDatePicker = true },
                ) {
                    Text(form.date?.let(DATE_ONLY_FORMATTER::format) ?: "اختيار تاريخ")
                }

                Text("الوقت", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("general-reminder-time"),
                    enabled = !state.isSaving,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onTimeChange(LocalTime.of(hour, minute)) },
                            form.time.hour,
                            form.time.minute,
                            true,
                        ).show()
                    },
                ) {
                    Text(TIME_ONLY_FORMATTER.format(form.time))
                }

                Text("التكرار", fontWeight = FontWeight.SemiBold)
                GeneralReminderUiFrequency.entries.forEach { option ->
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("general-reminder-frequency-${option.name.lowercase(Locale.US)}"),
                        enabled = !state.isSaving,
                        onClick = { onFrequencyChange(option) },
                    ) {
                        Text(
                            if (form.frequency == option) {
                                "✓ ${option.toDisplayText()}"
                            } else {
                                option.toDisplayText()
                            },
                        )
                    }
                }

                if (!notificationsAvailable) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "يمكن حفظ التذكير الآن، لكنه لن يظهر حتى تسمح بإشعارات وَصل.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onRequestNotificationAccess,
                            ) {
                                Text("إعداد الإشعارات")
                            }
                        }
                    }
                }

                state.mutationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("general-reminder-error"),
                    )
                }

                if (canCancel) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel-general-reminder"),
                        enabled = !state.isSaving,
                        onClick = onCancel,
                    ) {
                        Text("إلغاء التذكير")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("save-general-reminder"),
                enabled = !state.isSaving,
                onClick = onSave,
            ) {
                Text(if (state.isSaving) "جارٍ الحفظ…" else "حفظ التذكير")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.isSaving,
                onClick = onDismiss,
            ) {
                Text("إغلاق")
            }
        },
    )

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = form.date
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                            ?.let(Instant::ofEpochMilli)
                            ?.atZone(ZoneOffset.UTC)
                            ?.toLocalDate()
                        onDateChange(selected)
                        showDatePicker = false
                    },
                ) {
                    Text("اختيار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("رجوع")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun GeneralReminderFrequency?.toDisplayText(): String = when (this) {
    null -> "مرة واحدة"
    GeneralReminderFrequency.DAILY -> "يومي"
    GeneralReminderFrequency.WEEKLY -> "أسبوعي"
    GeneralReminderFrequency.MONTHLY -> "شهري"
}

private fun GeneralReminderUiFrequency.toDisplayText(): String = when (this) {
    GeneralReminderUiFrequency.ONCE -> "مرة واحدة"
    GeneralReminderUiFrequency.DAILY -> "يومي"
    GeneralReminderUiFrequency.WEEKLY -> "أسبوعي"
    GeneralReminderUiFrequency.MONTHLY -> "شهري"
}

private fun ReminderStatus.toGeneralReminderStatusText(): String = when (this) {
    ReminderStatus.SCHEDULED -> "مجدول"
    ReminderStatus.DELIVERED -> "تم التذكير"
    ReminderStatus.BLOCKED_PERMISSION -> "بانتظار إذن الإشعارات"
    ReminderStatus.FAILED -> "قيد الاسترداد"
    ReminderStatus.CANCELLED -> "ملغى"
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)
private val TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
