package com.wasl.app

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AddAttachmentCommand
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import com.wasl.app.data.AttachmentStore
import com.wasl.app.data.DocumentType
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.UnavailableAttachmentStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.document.AttachmentFileAccess
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal val LocalOpenAccountDocuments = staticCompositionLocalOf<(DebtId) -> Unit> { { } }

@Composable
internal fun DocumentsHubRoute(
    repository: WaslRepository,
    documentService: PaymentReceiptService,
    onBack: () -> Unit,
    initialDebtId: DebtId? = null,
    attachmentStore: AttachmentStore = UnavailableAttachmentStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var accounts by remember { mutableStateOf<List<AccountOverview>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var issuerName by remember { mutableStateOf("") }
    var activityName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var identityId by remember { mutableStateOf<String?>(null) }
    var identityLoaded by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var readyDocument by remember { mutableStateOf<IssuedDocumentRecord?>(null) }
    var attachments by remember { mutableStateOf<List<AttachmentRecord>>(emptyList()) }
    var attachmentBusy by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val debtId = initialDebtId
        if (uri == null || debtId == null || attachmentBusy) return@rememberLauncherForActivityResult
        attachmentBusy = true
        scope.launch {
            try {
                val resolver = context.contentResolver
                val metadata = queryAttachmentMetadata(context, uri)
                val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                resolver.openInputStream(uri)?.use { input ->
                    attachmentStore.importAttachment(
                        AddAttachmentCommand(
                            id = UUID.randomUUID().toString(),
                            debtId = debtId,
                            displayName = metadata.displayName,
                            mimeType = mime,
                            createdAt = Instant.now(),
                        ),
                        input,
                    )
                } ?: error("تعذر قراءة الملف المختار.")
                showMessage("تم حفظ المرفق داخل خزنة وَصل.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(error.message?.takeIf { it.isNotBlank() } ?: "تعذر حفظ المرفق.")
            } finally {
                attachmentBusy = false
            }
        }
    }

    LaunchedEffect(repository) {
        repository.observeAccounts()
            .catch { error -> loadError = error.message ?: "تعذر قراءة الحسابات." }
            .collect { value ->
                accounts = value
                loadError = null
            }
    }
    LaunchedEffect(documentService) {
        runCatching { documentService.getDefaultIdentity() }
            .onSuccess { identity ->
                identity?.let {
                    identityId = it.id
                    issuerName = it.displayName
                    activityName = it.activityName.orEmpty()
                    phone = it.phone.orEmpty()
                    footer = it.footerText.orEmpty()
                }
            }
        identityLoaded = true
    }
    LaunchedEffect(attachmentStore, initialDebtId) {
        val debtId = initialDebtId ?: return@LaunchedEffect
        attachmentStore.observeForDebt(debtId)
            .catch { error -> showMessage(error.message ?: "تعذر قراءة المرفقات.") }
            .collect { attachments = it }
    }

    fun issue(account: AccountOverview, type: DocumentType) {
        if (busyKey != null) return
        val normalizedName = issuerName.trim()
        if (normalizedName.isEmpty()) {
            showMessage("اكتب اسم مُصدر المستند أولًا.")
            return
        }
        val key = "${type.name}:${account.ledger.header.id.value}"
        busyKey = key
        readyDocument = null
        scope.launch {
            try {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val id = identityId ?: UUID.randomUUID().toString().also { identityId = it }
                val document = when (type) {
                    DocumentType.DEBT_RECEIPT -> documentService.issueDebtReceipt(
                        PrepareDebtReceiptCommand(
                            commandId = UUID.randomUUID().toString(),
                            documentId = UUID.randomUUID().toString(),
                            identityId = id,
                            debtId = account.ledger.header.id,
                            issuerDisplayName = normalizedName,
                            issuerActivityName = activityName.trim().ifEmpty { null },
                            issuerPhone = phone.trim().ifEmpty { null },
                            footerText = footer.trim().ifEmpty { null },
                            issuedAt = now,
                            issueZoneId = zone,
                        ),
                    )
                    DocumentType.ACCOUNT_STATEMENT -> documentService.issueAccountStatement(
                        PrepareAccountStatementCommand(
                            commandId = UUID.randomUUID().toString(),
                            documentId = UUID.randomUUID().toString(),
                            identityId = id,
                            debtId = account.ledger.header.id,
                            issuerDisplayName = normalizedName,
                            issuerActivityName = activityName.trim().ifEmpty { null },
                            issuerPhone = phone.trim().ifEmpty { null },
                            footerText = footer.trim().ifEmpty { null },
                            issuedAt = now,
                            issueZoneId = zone,
                        ),
                    )
                    DocumentType.PAYMENT_RECEIPT -> error("Payment receipts are issued from payments.")
                }
                readyDocument = document
                showMessage("تم تجهيز ${type.arabicLabel()} ${document.documentNumber}.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(error.message?.takeIf { it.isNotBlank() } ?: "تعذر إصدار المستند.")
            } finally {
                busyKey = null
            }
        }
    }

    val visibleAccounts = remember(accounts, initialDebtId) {
        if (initialDebtId == null) accounts
        else accounts.filter { it.ledger.header.id == initialDebtId }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
item("header") {
    DocumentsHubHeader(
        isAccountScoped = initialDebtId != null,
        onBack = onBack,
    )
}

            if (initialDebtId != null) {
                item("attachments") {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("account-attachments"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("المرفقات وخزنة الإثباتات", fontWeight = FontWeight.Bold)
                            Text(
                                "أضف صورًا أو ملفات مرتبطة بهذا الحساب لتجدها معه لاحقًا.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                modifier = Modifier.fillMaxWidth().testTag("add-attachment"),
                                enabled = !attachmentBusy,
                                onClick = {
                                    runCatching {
                                        attachmentPicker.launch(arrayOf("image/*", "application/pdf", "text/*", "application/octet-stream"))
                                    }.onFailure {
                                        showMessage("تعذر فتح منتقي الملفات على هذا الجهاز.")
                                    }
                                },
                            ) {
                                if (attachmentBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("إضافة مرفق")
                            }
                            if (attachments.isEmpty()) {
                                Text("لا توجد مرفقات لهذا الحساب بعد.")
                            } else {
                                attachments.forEach { attachment ->
                                    AttachmentRow(
                                        attachment = attachment,
                                        onOpen = {
                                            runCatching { AttachmentFileAccess.open(context, attachment) }
                                                .onFailure { showMessage("تعذر فتح المرفق أو أن فحص سلامته لم ينجح.") }
                                        },
                                        onShare = {
                                            runCatching { AttachmentFileAccess.share(context, attachment) }
                                                .onFailure { showMessage("تعذرت مشاركة المرفق أو أن فحص سلامته لم ينجح.") }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item("identity") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("هوية مُصدر المستند", fontWeight = FontWeight.Bold)
                        if (!identityLoaded) CircularProgressIndicator()
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth().testTag("documents-issuer-name"),
                            value = issuerName,
                            onValueChange = { issuerName = it },
                            label = { Text("الاسم *") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = activityName,
                            onValueChange = { activityName = it },
                            label = { Text("النشاط — اختياري") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("الهاتف — اختياري") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = footer,
                            onValueChange = { footer = it },
                            label = { Text("عبارة أسفل المستند — اختياري") },
                        )
                    }
                }
            }

            readyDocument?.let { document ->
                item("ready-document") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${document.type.arabicLabel()} جاهز", fontWeight = FontWeight.Bold)
                            Text(ltrIsolate(document.documentNumber))
DocumentDualActionButtons(
    testTagPrefix = "ready-document",
    primaryLabel = "فتح PDF",
    secondaryLabel = "مشاركة",
    primaryFilled = true,
    onPrimary = {
        runCatching { ReceiptFileAccess.open(context, document) }
            .onFailure { showMessage("تعذر فتح ملف PDF.") }
    },
    onSecondary = {
        runCatching { ReceiptFileAccess.share(context, document) }
            .onFailure { showMessage("تعذرت مشاركة ملف PDF.") }
    },
)
                        }
                    }
                }
            }

            loadError?.let { error ->
                item("load-error") { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            if (visibleAccounts.isEmpty() && loadError == null) {
                item("empty") {
                    Text(
                        if (initialDebtId == null) "لا توجد حسابات لإصدار مستندات لها بعد."
                        else "تعذر العثور على الحساب المحدد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(visibleAccounts, key = { it.ledger.header.id.value }) { account ->
                val debtKey = "${DocumentType.DEBT_RECEIPT.name}:${account.ledger.header.id.value}"
                val statementKey = "${DocumentType.ACCOUNT_STATEMENT.name}:${account.ledger.header.id.value}"
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(account.person.displayName, fontWeight = FontWeight.Bold)
                        Text(
                            "المتبقي ${formatDocumentsMoney(account.ledger.balance)} من ${formatDocumentsMoney(account.ledger.header.originalAmount)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("issue-debt-receipt-${account.ledger.header.id.value}"),
                            enabled = busyKey == null && identityLoaded,
                            onClick = { issue(account, DocumentType.DEBT_RECEIPT) },
                        ) {
                            if (busyKey == debtKey) CircularProgressIndicator() else Text("إصدار إيصال دين")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().testTag("issue-account-statement-${account.ledger.header.id.value}"),
                            enabled = busyKey == null && identityLoaded,
                            onClick = { issue(account, DocumentType.ACCOUNT_STATEMENT) },
                        ) {
                            if (busyKey == statementKey) CircularProgressIndicator() else Text("تصدير كل المعاملات PDF")
                        }
                        Text(
                            "كشف الحساب يحفظ Snapshot ثابتًا للحساب ويشمل أصل الدين، صافي المسدد، المتبقي، وكل دفعة أو عملية عكس مسجلة حتى وقت التصدير.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DocumentsHubHeader(
    isAccountScoped: Boolean,
    onBack: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("documents-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text("رجوع") }
                DocumentsHubHeaderText(isAccountScoped = isAccountScoped)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("documents-header-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text("رجوع") }
                DocumentsHubHeaderText(isAccountScoped = isAccountScoped)
            }
        }
    }
}

@Composable
private fun DocumentsHubHeaderText(isAccountScoped: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            if (isAccountScoped) "مستندات وإثباتات الحساب" else "تصدير وتقارير PDF",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (isAccountScoped) {
                "مستندات PDF ومرفقات هذا الحساب فقط."
            } else {
                "اختر حسابًا ثم صدّر مستنداته."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DocumentDualActionButtons(
    testTagPrefix: String,
    primaryLabel: String,
    secondaryLabel: String,
    enabled: Boolean = true,
    primaryFilled: Boolean = false,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTagPrefix-actions-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DocumentPrimaryAction(
                    modifier = Modifier.fillMaxWidth(),
                    label = primaryLabel,
                    enabled = enabled,
                    filled = primaryFilled,
                    testTag = "$testTagPrefix-primary",
                    onClick = onPrimary,
                )
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$testTagPrefix-secondary"),
                    enabled = enabled,
                    onClick = onSecondary,
                ) { Text(secondaryLabel) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTagPrefix-actions-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DocumentPrimaryAction(
                    modifier = Modifier.weight(1f),
                    label = primaryLabel,
                    enabled = enabled,
                    filled = primaryFilled,
                    testTag = "$testTagPrefix-primary",
                    onClick = onPrimary,
                )
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("$testTagPrefix-secondary"),
                    enabled = enabled,
                    onClick = onSecondary,
                ) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun DocumentPrimaryAction(
    modifier: Modifier,
    label: String,
    enabled: Boolean,
    filled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    if (filled) {
        Button(
            modifier = modifier.testTag(testTag),
            enabled = enabled,
            onClick = onClick,
        ) { Text(label) }
    } else {
        OutlinedButton(
            modifier = modifier.testTag(testTag),
            enabled = enabled,
            onClick = onClick,
        ) { Text(label) }
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .testTag("attachment-${attachment.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(attachment.displayName, fontWeight = FontWeight.SemiBold)
        Text(
            "${ltrIsolate(formatFileSize(attachment.sizeBytes))} • ${ltrIsolate(attachment.mimeType)}",
            style = MaterialTheme.typography.bodySmall,
        )
        val integrityText = when (attachment.integrity) {
            AttachmentIntegrity.OK -> "سلامة الملف: سليمة"
            AttachmentIntegrity.MISSING -> "سلامة الملف: الملف مفقود"
            AttachmentIntegrity.HASH_MISMATCH -> "سلامة الملف: البصمة لا تطابق المحتوى"
        }
        Text(
            integrityText,
            color = if (attachment.integrity == AttachmentIntegrity.OK) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodySmall,
        )
DocumentDualActionButtons(
    testTagPrefix = "attachment-${attachment.id}",
    primaryLabel = "فتح",
    secondaryLabel = "مشاركة",
    enabled = attachment.integrity == AttachmentIntegrity.OK,
    onPrimary = onOpen,
    onSecondary = onShare,
)
    }
}

private data class PickedAttachmentMetadata(val displayName: String)

private fun queryAttachmentMetadata(context: android.content.Context, uri: Uri): PickedAttachmentMetadata {
    var name: String? = null
    val cursor: Cursor? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index)
        }
    }
    return PickedAttachmentMetadata(
        displayName = name?.trim()?.takeIf(String::isNotBlank) ?: "مرفق",
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun DocumentType.arabicLabel(): String = when (this) {
    DocumentType.DEBT_RECEIPT -> "إيصال الدين"
    DocumentType.PAYMENT_RECEIPT -> "إيصال السداد"
    DocumentType.ACCOUNT_STATEMENT -> "كشف الحساب الكامل"
}

private fun formatDocumentsMoney(money: Money): String = formatMoney(money)