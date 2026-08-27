package com.wasl.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.wasl.app.data.PersonRecord
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PersonId
import java.math.BigDecimal
import java.util.Locale
import kotlinx.coroutines.launch

class NaturalEntryActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WaslApplication
        val service = NaturalDebtConfirmationService(
            repository = app.repository,
            paymentPromiseStore = app.paymentPromiseStore,
        )
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                WaslTheme {
                    NaturalEntryScreen(
                        parser = remember { NaturalEntryParser() },
                        confirmationService = service,
                        onBack = ::finish,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NaturalEntryScreen(
    parser: NaturalEntryParser,
    confirmationService: NaturalDebtConfirmationService,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<NaturalEntryDraft?>(null) }
    var ambiguousPeople by remember { mutableStateOf<List<PersonRecord>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun analyze(source: String = text) {
        draft = parser.parse(source)
        ambiguousPeople = emptyList()
        message = null
    }

    fun save(selectedPersonId: PersonId? = null) {
        val current = draft ?: return
        if (!current.canPreviewAsDebt || isSaving) return
        isSaving = true
        message = null
        scope.launch {
            runCatching {
                confirmationService.confirmAndSave(current, selectedPersonId)
            }.onSuccess { result ->
                when (result) {
                    is NaturalDebtConfirmationResult.Saved -> {
                        ambiguousPeople = emptyList()
                        message = buildString {
                            append("تم حفظ الحساب بعد تأكيدك.")
                            if (result.promiseCreated) append(" وتم حفظ وعد السداد.")
                            result.warning?.let { append(" ").append(it) }
                        }
                        text = ""
                        draft = null
                    }
                    is NaturalDebtConfirmationResult.InvalidDraft -> {
                        message = "المعاينة غير مكتملة. راجع الحقول المطلوبة قبل الحفظ."
                    }
                    is NaturalDebtConfirmationResult.AmbiguousPerson -> {
                        ambiguousPeople = result.matchingPeople
                        message = "وجدت أكثر من شخص باسم ${result.personName}. اختر الشخص المقصود قبل الحفظ."
                    }
                }
            }.onFailure {
                message = it.message ?: "تعذر حفظ الحساب. راجع البيانات وحاول مرة أخرى."
            }
            isSaving = false
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val recognized = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (recognized.isNotBlank()) {
            text = recognized
            analyze(recognized)
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack) { Text("رجوع") }
                    Text(
                        text = "الإدخال الذكي",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "اكتب الدين بطريقتك الطبيعية. لن يحفظ وَصل أي مبلغ قبل أن يعرض المعاينة وتضغط أنت على التأكيد.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        draft = null
                        ambiguousPeople = emptyList()
                        message = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("natural-entry-text"),
                    label = { Text("مثال: سلفت عبدالله 5000 ريال سعودي اليوم") },
                    minLines = 3,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("natural-entry-analyze"),
                        enabled = text.isNotBlank(),
                        onClick = { analyze() },
                    ) {
                        Text("تحليل ومعاينة")
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("natural-entry-voice"),
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن")
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure { message = "خدمة التعرف على الصوت غير متاحة على هذا الجهاز." }
                        },
                    ) {
                        Text("إملاء صوتي")
                    }
                }
            }

            draft?.let { current ->
                item { NaturalDraftPreview(current) }
                if (current.canPreviewAsDebt) {
                    item {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("natural-entry-confirm"),
                            enabled = !isSaving,
                            onClick = { save() },
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("تأكيد وحفظ الحساب")
                            }
                        }
                    }
                }
            }

            if (ambiguousPeople.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("اختر الشخص المقصود", fontWeight = FontWeight.Bold)
                            ambiguousPeople.forEach { person ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSaving,
                                    onClick = { save(person.id) },
                                ) {
                                    Text(personChoiceLabel(person))
                                }
                            }
                        }
                    }
                }
            }

            message?.let { value ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = value,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NaturalDraftPreview(draft: NaturalEntryDraft) {
    Card(modifier = Modifier.fillMaxWidth().testTag("natural-entry-preview")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "معاينة قبل الحفظ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()
            PreviewRow("الشخص", draft.personName ?: "غير معروف")
            PreviewRow("الاتجاه", directionLabel(draft.direction))
            PreviewRow("المبلغ", amountLabel(draft.amountMinorUnits, draft.currency))
            PreviewRow("تاريخ العملية", draft.entryDate?.toString() ?: "غير معروف")
            PreviewRow("وعد السداد", draft.promisedDate?.toString() ?: "لا يوجد")
            if (draft.missingRequiredFields.isNotEmpty()) {
                Text(
                    "حقول تحتاج مراجعة: ${draft.missingRequiredFields.joinToString { missingFieldLabel(it) }}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            draft.warnings.forEach { warning ->
                Text(warning, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "الحفظ لا يتم من التحليل وحده؛ يلزم الضغط على زر التأكيد.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun directionLabel(direction: DebtDirection?): String = when (direction) {
    DebtDirection.RECEIVABLE -> "لي عنده"
    DebtDirection.PAYABLE -> "عليّ له"
    null -> "غير معروف"
}

private fun amountLabel(minorUnits: Long?, currency: CurrencyCode?): String {
    if (minorUnits == null || currency == null) return "غير معروف"
    val digits = MoneyInputParser.fractionDigits(currency)
    val value = BigDecimal.valueOf(minorUnits, digits)
        .stripTrailingZeros()
        .toPlainString()
    return "$value ${currency.name}"
}

private fun missingFieldLabel(field: NaturalDraftField): String = when (field) {
    NaturalDraftField.PERSON -> "الشخص"
    NaturalDraftField.DIRECTION -> "اتجاه الدين"
    NaturalDraftField.AMOUNT -> "المبلغ"
    NaturalDraftField.CURRENCY -> "العملة"
}

private fun personChoiceLabel(person: PersonRecord): String = buildString {
    append(person.displayName)
    person.phone?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
    person.email?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it.lowercase(Locale.ROOT)) }
}
