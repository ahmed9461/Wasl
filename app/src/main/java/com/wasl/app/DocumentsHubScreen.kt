package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DocumentType
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.WaslRepository
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
internal fun DocumentsHubRoute(
    repository: WaslRepository,
    documentService: PaymentReceiptService,
    onBack: () -> Unit,
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

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onBack) { Text("رجوع") }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "مركز المستندات",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "إيصال الدين وكشف الحساب من Snapshot ثابت وقت الإصدار.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            Text(
                                "${document.type.arabicLabel()} جاهز",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(document.documentNumber)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    runCatching { ReceiptFileAccess.open(context, document) }
                                        .onFailure { showMessage("تعذر فتح ملف PDF.") }
                                }) { Text("فتح PDF") }
                                OutlinedButton(onClick = {
                                    runCatching { ReceiptFileAccess.share(context, document) }
                                        .onFailure { showMessage("تعذرت مشاركة ملف PDF.") }
                                }) { Text("مشاركة") }
                            }
                        }
                    }
                }
            }

            loadError?.let { error ->
                item("load-error") {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }

            if (accounts.isEmpty() && loadError == null) {
                item("empty") {
                    Text(
                        "لا توجد حسابات لإصدار مستندات لها بعد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(accounts, key = { it.ledger.header.id.value }) { account ->
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
                            if (busyKey == debtKey) CircularProgressIndicator()
                            else Text("إصدار إيصال دين")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().testTag("issue-account-statement-${account.ledger.header.id.value}"),
                            enabled = busyKey == null && identityLoaded,
                            onClick = { issue(account, DocumentType.ACCOUNT_STATEMENT) },
                        ) {
                            if (busyKey == statementKey) CircularProgressIndicator()
                            else Text("إصدار كشف حساب")
                        }
                    }
                }
            }
        }
    }
}

private fun DocumentType.arabicLabel(): String = when (this) {
    DocumentType.DEBT_RECEIPT -> "إيصال الدين"
    DocumentType.PAYMENT_RECEIPT -> "إيصال السداد"
    DocumentType.ACCOUNT_STATEMENT -> "كشف الحساب"
}

private fun formatDocumentsMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return "${formatter.format(major)} ${money.currency.value}"
}
