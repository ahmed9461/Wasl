package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wasl.app.data.PersonRecord
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.GroupExpense
import com.wasl.domain.PersonId

@Composable
internal fun CreateEntryTypeDialog(
    onDismiss: () -> Unit,
    onCreateIndividual: () -> Unit,
    onCreateGroupExpense: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("create-entry-type-picker"),
        onDismissRequest = onDismiss,
        title = { Text("ماذا تريد أن تضيف؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "اختر حسابًا فرديًا، أو عملية واحدة موزعة على عدة أشخاص.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-entry-individual"),
                    onClick = onCreateIndividual,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("حساب فردي", fontWeight = FontWeight.Bold)
                        Text(
                            text = "دين أو حق مع شخص واحد",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-entry-group"),
                    onClick = onCreateGroupExpense,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("عملية جماعية", fontWeight = FontWeight.Bold)
                        Text(
                            text = "عملية واحدة بحصص موزعة على شخصين أو أكثر",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
internal fun GroupExpenseDialog(
    form: GroupExpenseForm,
    step: GroupExpenseEditorStep,
    preview: GroupExpense?,
    error: String?,
    isSaving: Boolean,
    peopleQuery: String,
    selectablePeople: List<PersonRecord>,
    isPeopleLoading: Boolean,
    peopleLoadError: String?,
    hasMorePeople: Boolean,
    onDismiss: () -> Unit,
    onPeopleQueryChange: (String) -> Unit,
    onToggleParticipant: (PersonId) -> Unit,
    onParticipantAmountChange: (PersonId, String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRetryPeople: () -> Unit,
    onReview: () -> Unit,
    onEditReview: () -> Unit,
    onConfirm: () -> Unit,
) {
    val reviewing = step == GroupExpenseEditorStep.REVIEW && preview != null
    AlertDialog(
        modifier = Modifier.testTag(
            if (reviewing) "group-expense-review" else "group-expense-editor",
        ),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (reviewing) "راجع العملية الجماعية" else "عملية جماعية جديدة")
                Text(
                    text = if (reviewing) {
                        "لن تُحفظ أي حركة قبل التأكيد النهائي."
                    } else {
                        "اختر شخصين أو أكثر وحدد حصة كل شخص."
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (reviewing) {
                    GroupExpenseReviewContent(form = form, preview = preview)
                } else {
                    GroupExpenseEditContent(
                        form = form,
                        peopleQuery = peopleQuery,
                        selectablePeople = selectablePeople,
                        isPeopleLoading = isPeopleLoading,
                        peopleLoadError = peopleLoadError,
                        hasMorePeople = hasMorePeople,
                        isSaving = isSaving,
                        onPeopleQueryChange = onPeopleQueryChange,
                        onToggleParticipant = onToggleParticipant,
                        onParticipantAmountChange = onParticipantAmountChange,
                        onCurrencyChange = onCurrencyChange,
                        onDirectionChange = onDirectionChange,
                        onDescriptionChange = onDescriptionChange,
                        onNotesChange = onNotesChange,
                        onRetryPeople = onRetryPeople,
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier.testTag("group-expense-error"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag(
                    if (reviewing) "group-expense-confirm" else "group-expense-review-action",
                ),
                onClick = if (reviewing) onConfirm else onReview,
                enabled = !isSaving,
            ) {
                Text(if (reviewing) "تأكيد وحفظ" else "مراجعة")
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(
                    if (reviewing) "group-expense-edit-review" else "group-expense-cancel",
                ),
                onClick = if (reviewing) onEditReview else onDismiss,
                enabled = !isSaving,
            ) {
                Text(if (reviewing) "تعديل" else "إلغاء")
            }
        },
    )
}

@Composable
private fun GroupExpenseEditContent(
    form: GroupExpenseForm,
    peopleQuery: String,
    selectablePeople: List<PersonRecord>,
    isPeopleLoading: Boolean,
    peopleLoadError: String?,
    hasMorePeople: Boolean,
    isSaving: Boolean,
    onPeopleQueryChange: (String) -> Unit,
    onToggleParticipant: (PersonId) -> Unit,
    onParticipantAmountChange: (PersonId, String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRetryPeople: () -> Unit,
) {
    Text("الاتجاه", fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group-direction-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupDirectionChoices(form.direction, isSaving, onDirectionChange)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group-direction-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupDirectionChoices(form.direction, isSaving, onDirectionChange)
            }
        }
    }

    Text("العملة", fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group-currency-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupCurrencyChoices(form.currency, isSaving, onCurrencyChange)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group-currency-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupCurrencyChoices(form.currency, isSaving, onCurrencyChange)
            }
        }
    }

    OutlinedTextField(
        value = form.description,
        onValueChange = onDescriptionChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group-expense-description"),
        label = { Text("وصف العملية") },
        supportingText = { Text("مثال: فاتورة مطعم أو مشتريات مشتركة") },
        enabled = !isSaving,
        maxLines = 3,
    )

    OutlinedTextField(
        value = form.notes,
        onValueChange = onNotesChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group-expense-notes"),
        label = { Text("ملاحظات — اختياري") },
        enabled = !isSaving,
        maxLines = 3,
    )

    HorizontalDivider()
    Text("المشاركون", fontWeight = FontWeight.SemiBold)
    Text(
        text = "المحددون: ${form.participants.size} — يلزم شخصان على الأقل.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = peopleQuery,
        onValueChange = onPeopleQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group-people-search"),
        label = { Text("ابحث في الأشخاص المحفوظين") },
        singleLine = true,
        enabled = !isSaving,
    )

    when {
        peopleLoadError != null -> {
            Text(peopleLoadError, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetryPeople, enabled = !isSaving) { Text("إعادة المحاولة") }
        }
        isPeopleLoading -> Text("جارٍ قراءة الأشخاص المحفوظين…")
        selectablePeople.isEmpty() -> Text(
            text = "لا توجد نتائج. أنشئ الأشخاص من الحسابات الفردية أولًا.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            selectablePeople.forEach { person ->
                val selected = form.participants.any { it.person.id == person.id }
                FilterChip(
                    selected = selected,
                    onClick = { onToggleParticipant(person.id) },
                    label = { Text(person.displayName) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group-person-${person.id.value}"),
                )
            }
            if (hasMorePeople) {
                Text(
                    text = "اكتب جزءًا من الاسم لإظهار نتائج أدق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (form.participants.isNotEmpty()) {
        HorizontalDivider()
        Text("الحصص", fontWeight = FontWeight.SemiBold)
        form.participants.forEach { participant ->
            GroupParticipantAmountRow(
                participant = participant,
                currency = form.currency,
                isSaving = isSaving,
                onAmountChange = { onParticipantAmountChange(participant.person.id, it) },
                onRemove = { onToggleParticipant(participant.person.id) },
            )
        }
    }
}

@Composable
private fun GroupDirectionChoices(
    selected: DebtDirection,
    isSaving: Boolean,
    onChange: (DebtDirection) -> Unit,
) {
    FilterChip(
        selected = selected == DebtDirection.RECEIVABLE,
        onClick = { onChange(DebtDirection.RECEIVABLE) },
        label = { Text("لي عندهم") },
        enabled = !isSaving,
        modifier = Modifier.testTag("group-direction-receivable"),
    )
    FilterChip(
        selected = selected == DebtDirection.PAYABLE,
        onClick = { onChange(DebtDirection.PAYABLE) },
        label = { Text("عليّ لهم") },
        enabled = !isSaving,
        modifier = Modifier.testTag("group-direction-payable"),
    )
}

@Composable
private fun GroupCurrencyChoices(
    selected: CurrencyCode,
    isSaving: Boolean,
    onChange: (CurrencyCode) -> Unit,
) {
    listOf(CurrencyCode.YER, CurrencyCode.SAR, CurrencyCode.USD).forEach { currency ->
        FilterChip(
            selected = selected == currency,
            onClick = { onChange(currency) },
            label = { Text(currency.value) },
            enabled = !isSaving,
            modifier = Modifier.testTag("group-currency-${currency.value}"),
        )
    }
}

@Composable
internal fun GroupParticipantAmountRow(
    participant: GroupExpenseParticipantDraft,
    currency: CurrencyCode,
    isSaving: Boolean,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val tagPrefix = "group-participant-${participant.person.id.value}"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagPrefix),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            if (shouldStackDenseRows(maxWidth)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$tagPrefix-stacked"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GroupParticipantIdentity(participant, isSaving, onRemove)
                    GroupParticipantAmountField(
                        participant = participant,
                        currency = currency,
                        isSaving = isSaving,
                        onAmountChange = onAmountChange,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$tagPrefix-inline"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GroupParticipantIdentity(participant, isSaving, onRemove)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        GroupParticipantAmountField(
                            participant = participant,
                            currency = currency,
                            isSaving = isSaving,
                            onAmountChange = onAmountChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupParticipantIdentity(
    participant: GroupExpenseParticipantDraft,
    isSaving: Boolean,
    onRemove: () -> Unit,
) {
    Text(
        text = participant.person.displayName,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    TextButton(
        onClick = onRemove,
        enabled = !isSaving,
        modifier = Modifier.testTag("group-participant-${participant.person.id.value}-remove"),
    ) {
        Text("إزالة")
    }
}

@Composable
private fun GroupParticipantAmountField(
    participant: GroupExpenseParticipantDraft,
    currency: CurrencyCode,
    isSaving: Boolean,
    onAmountChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = participant.amount,
        onValueChange = onAmountChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group-participant-${participant.person.id.value}-amount"),
        label = { Text("الحصة ${currency.value}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = !isSaving,
    )
}

@Composable
private fun GroupExpenseReviewContent(
    form: GroupExpenseForm,
    preview: GroupExpense,
) {
    val names = form.participants.associate { it.person.id to it.person.displayName }
    ReviewValue("الوصف", preview.description)
    ReviewValue(
        "الاتجاه",
        if (preview.direction == DebtDirection.RECEIVABLE) "لي عندهم" else "عليّ لهم",
    )
    ReviewValue("الإجمالي", formatMoney(preview.totalAmount))
    preview.notes?.let { ReviewValue("الملاحظات", it) }

    HorizontalDivider()
    Text("الحصص", fontWeight = FontWeight.SemiBold)
    preview.shares.forEach { share ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("group-review-share-${share.personId.value}"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = names[share.personId] ?: share.personId.value,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatMoney(share.amount),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    Text(
        text = "التأكيد ينشئ العملية الأصلية وديون الحصص في معاملة واحدة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
