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
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.app.document.DocumentBannerCropper
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var issuerBanner by remember { mutableStateOf<DocumentBannerAsset?>(null) }
    var bannerPreviewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBannerSource by remember { mutableStateOf<ByteArray?>(null) }
    var bannerBusy by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var pendingIssue by remember { mutableStateOf<PendingDocumentIssue?>(null) }
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
                val mime = runCatching { resolver.getType(uri) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "application/octet-stream"
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
                showMessage(attachmentImportFailureMessage(error))
            } finally {
                attachmentBusy = false
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null || bannerBusy) return@rememberLauncherForActivityResult
        bannerBusy = true
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        DocumentBannerCropper.readCandidate(input)
                    } ?: error("تعذر قراءة الصورة المختارة.")
                }
                pendingBannerSource = candidate
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(error.message?.takeIf { it.isNotBlank() } ?: "تعذر قراءة صورة رأس المستند.")
            } finally {
                bannerBusy = false
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
        try {
            documentService.getDefaultIdentity()?.let { identity ->
                identityId = identity.id
                issuerName = identity.displayName
                activityName = identity.activityName.orEmpty()
                phone = identity.phone.orEmpty()
                footer = identity.footerText.orEmpty()
                issuerBanner = identity.banner
                bannerPreviewBytes = identity.banner?.let { banner ->
                    runCatching { documentService.readIdentityBanner(banner) }.getOrNull()
                }
                if (identity.banner != null && bannerPreviewBytes == null) {
                    showMessage("تعذر التحقق من صورة رأس الهوية. اختر الصورة من جديد قبل التصدير.")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showMessage("تعذر تحميل هوية المستند المحفوظة.")
        } finally {
            identityLoaded = true
        }
    }
    LaunchedEffect(attachmentStore, initialDebtId) {
        val debtId = initialDebtId ?: return@LaunchedEffect
        attachmentStore.observeForDebt(debtId)
            .catch { error -> showMessage(error.message ?: "تعذر قراءة المرفقات.") }
            .collect { attachments = it }
    }

    fun requestIssue(account: AccountOverview, type: DocumentType) {
        if (busyKey != null || pendingIssue != null) return
        val normalizedName = issuerName.trim()
        if (normalizedName.isEmpty()) {
            showMessage("اكتب اسم مُصدر المستند أولًا.")
            return
        }
        if (issuerBanner != null && bannerPreviewBytes == null) {
            showMessage("تحقق من صورة رأس الهوية أو اخترها من جديد قبل الإصدار.")
            return
        }
        val id = identityId ?: UUID.randomUUID().toString().also { identityId = it }
        pendingIssue = PendingDocumentIssue(
            account = account,
            type = type,
            identityId = id,
            issuerName = normalizedName,
            activityName = activityName.trim().ifEmpty { null },
            phone = phone.trim().ifEmpty { null },
            footer = footer.trim().ifEmpty { null },
            banner = issuerBanner,
            bannerPreviewBytes = bannerPreviewBytes,
        )
    }

    fun confirmIssue(preview: PendingDocumentIssue) {
        if (busyKey != null) return
        val key = "${preview.type.name}:${preview.account.ledger.header.id.value}"
        busyKey = key
        pendingIssue = null
        readyDocument = null
        scope.launch {
            try {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val document = when (preview.type) {
                    DocumentType.DEBT_RECEIPT -> documentService.issueDebtReceipt(
                        PrepareDebtReceiptCommand(
                            commandId = UUID.randomUUID().toString(),
                            documentId = UUID.randomUUID().toString(),
                            identityId = preview.identityId,
                            debtId = preview.account.ledger.header.id,
                            issuerDisplayName = preview.issuerName,
                            issuerActivityName = preview.activityName,
                            issuerPhone = preview.phone,
                            footerText = preview.footer,
                            issuerBanner = preview.banner,
                            issuedAt = now,
                            issueZoneId = zone,
                        ),
                    )
                    DocumentType.ACCOUNT_STATEMENT -> documentService.issueAccountStatement(
                        PrepareAccountStatementCommand(
                            commandId = UUID.randomUUID().toString(),
                            documentId = UUID.randomUUID().toString(),
                            identityId = preview.identityId,
                            debtId = preview.account.ledger.header.id,
                            issuerDisplayName = preview.issuerName,
                            issuerActivityName = preview.activityName,
                            issuerPhone = preview.phone,
                            footerText = preview.footer,
                            issuerBanner = preview.banner,
                            issuedAt = now,
                            issueZoneId = zone,
                        ),
                    )
                    DocumentType.PAYMENT_RECEIPT -> error("Payment receipts are issued from payments.")
                }
                readyDocument = document
                showMessage("تم تجهيز ${preview.type.arabicLabel()} ${document.documentNumber}.")
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
                        DocumentIdentityBannerControls(
                            previewBytes = bannerPreviewBytes,
                            hasBanner = issuerBanner != null,
                            busy = bannerBusy,
                            onPick = {
                                runCatching { bannerPicker.launch(arrayOf("image/*")) }
                                    .onFailure { showMessage("تعذر فتح معرض الصور على هذا الجهاز.") }
                            },
                            onRemove = {
                                pendingBannerSource = null
                                issuerBanner = null
                                bannerPreviewBytes = null
                                showMessage("لن تظهر صورة رأس في المستندات الجديدة.")
                            },
                        )
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
                        DocumentDualActionButtons(
                            testTagPrefix = "account-documents-${account.ledger.header.id.value}",
                            primaryLabel = if (busyKey == debtKey) "جاري الإصدار…" else "إيصال دين",
                            secondaryLabel = if (busyKey == statementKey) "جاري التصدير…" else "كشف حساب PDF",
                            enabled = busyKey == null && pendingIssue == null && identityLoaded,
                            primaryFilled = true,
                            onPrimary = { requestIssue(account, DocumentType.DEBT_RECEIPT) },
                            onSecondary = { requestIssue(account, DocumentType.ACCOUNT_STATEMENT) },
                        )
                    }
                }
            }
        }
    }

    pendingBannerSource?.let { sourceBytes ->
        DocumentBannerCropDialog(
            sourceBytes = sourceBytes,
            busy = bannerBusy,
            onDismiss = { if (!bannerBusy) pendingBannerSource = null },
            onConfirm = { focusX, focusY ->
                if (!bannerBusy) {
                    bannerBusy = true
                    scope.launch {
                        try {
                            val croppedBytes = withContext(Dispatchers.Default) {
                                DocumentBannerCropper.cropToHeader(sourceBytes, focusX, focusY)
                            }
                            val asset = ByteArrayInputStream(croppedBytes).use { input ->
                                documentService.importIdentityBanner(input)
                            }
                            val verifiedBytes = documentService.readIdentityBanner(asset)
                            issuerBanner = asset
                            bannerPreviewBytes = verifiedBytes
                            pendingBannerSource = null
                            showMessage("تم حفظ وضبط صورة رأس المستند.")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            showMessage(error.message?.takeIf { it.isNotBlank() } ?: "تعذر تجهيز صورة رأس المستند.")
                        } finally {
                            bannerBusy = false
                        }
                    }
                }
            },
        )
    }

    pendingIssue?.let { preview ->
        DocumentIssuePreviewDialog(
            preview = preview,
            busy = busyKey != null,
            onDismiss = { if (busyKey == null) pendingIssue = null },
            onConfirm = { confirmIssue(preview) },
        )
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
    val cursor: Cursor? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )
    }.getOrNull()
    runCatching {
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
    }
    return PickedAttachmentMetadata(
        displayName = name?.trim()?.takeIf(String::isNotBlank) ?: "مرفق",
    )
}

internal fun attachmentImportFailureMessage(error: Throwable): String {
    val details = error.message.orEmpty()
    return when {
        error is SecurityException -> "لا يمكن قراءة هذا الملف. اختر ملفًا آخر أو امنح الإذن المطلوب."
        details.contains("25 MB", ignoreCase = true) || details.contains("larger", ignoreCase = true) ->
            "حجم المرفق أكبر من الحد المسموح (25 ميجابايت)."
        details.contains("empty", ignoreCase = true) -> "الملف المختار فارغ ولا يمكن إضافته."
        details == "تعذر قراءة الملف المختار." -> details
        else -> "تعذر حفظ المرفق. جرّب ملفًا آخر."
    }
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
