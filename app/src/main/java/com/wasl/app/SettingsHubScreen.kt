package com.wasl.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wasl.app.backup.BackupCreated
import com.wasl.app.backup.BackupService
import com.wasl.app.privacy.AppAppearance
import com.wasl.app.privacy.PrivacyPreferences
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsHubRoute(
    backupService: BackupService,
    privacyPreferences: PrivacyPreferences,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRestored: () -> Unit,
    onSecureScreenChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var hideSensitiveNotifications by remember { mutableStateOf(privacyPreferences.hideSensitiveNotifications) }
    var secureScreen by remember { mutableStateOf(privacyPreferences.secureScreen) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var showCreatePasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBackup by remember { mutableStateOf<BackupCreated?>(null) }
    fun showMessage(message: String) { scope.launch { snackbarHostState.showSnackbar(message) } }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri: Uri? ->
        val backup = pendingBackup; pendingBackup = null
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "جارٍ حفظ النسخة المشفّرة…"
            val result = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(backup.bytes); it.flush() }
                    ?: error("تعذر فتح الملف المحدد للكتابة.")
            } }
            busyMessage = null
            result.fold(
                onSuccess = { showMessage("تم حفظ النسخة: ${backup.rowCount} سجل و${backup.documentCount} مستند.") },
                onFailure = { showMessage(it.userFacingMessage("تعذر حفظ النسخة الاحتياطية.")) },
            )
        }
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "جارٍ فحص ملف النسخة…"
            val result = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(MAX_BACKUP_IMPORT_BYTES) }
                    ?: error("تعذر فتح ملف النسخة المحدد.")
            } }
            busyMessage = null
            result.fold(onSuccess = { restoreBytes = it; showRestorePasswordDialog = true },
                onFailure = { showMessage(it.userFacingMessage("تعذر قراءة ملف النسخة.")) })
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings-hub"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 18.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("رجوع") }
                Column(Modifier.weight(1f)) {
                    Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("المظهر، الأمان، التذكيرات والنسخ الاحتياطي",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SettingsSectionCard("المظهر", "اختر الشكل المريح لك؛ التلقائي يتبع إعداد الجهاز.") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppAppearance.entries.forEach { option ->
                        FilterChip(
                            selected = appearance == option,
                            onClick = { onAppearanceChange(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.weight(1f).testTag("appearance-${option.storedValue}"),
                        )
                    }
                }
            }

            SettingsSectionCard("الأمان والخصوصية", "كل خيارات الحماية في مكان واضح بدون أزرار عائمة.") {
                SettingsActionRow("قفل التطبيق", "البصمة أو قفل الجهاز وإعداد مهلة القفل", onOpenSecurity, "open-security")
                HorizontalDivider()
                PrivacySwitchRow("إخفاء تفاصيل الإشعارات", "إشعار عام بدون اسم الشخص أو المبلغ.",
                    hideSensitiveNotifications, "privacy-hide-notification-details") {
                    hideSensitiveNotifications = it; privacyPreferences.hideSensitiveNotifications = it
                }
                HorizontalDivider()
                PrivacySwitchRow("حماية الشاشة", "يمنع لقطات الشاشة ويخفي المعاينة من التطبيقات الأخيرة.",
                    secureScreen, "privacy-secure-screen") {
                    secureScreen = it; privacyPreferences.secureScreen = it; onSecureScreenChanged()
                }
            }

            SettingsSectionCard("التذكيرات", "إدارة التذكيرات العامة ومواعيد المتابعة.") {
                SettingsActionRow("مركز التذكيرات", "إضافة ومراجعة التذكيرات المحفوظة", onOpenReminders, "open-reminders")
            }

            SettingsSectionCard("الأدوات والتقارير", "وصول منظم إلى المستندات والإحصاءات.") {
                SettingsActionRow("المستندات", "إيصالات الدين وكشوف الحساب", onOpenDocuments, "open-documents-hub")
                HorizontalDivider()
                SettingsActionRow("الإحصاءات", "مؤشرات موضوعية بدون خلط العملات", onOpenStatistics, "open-objective-statistics")
            }

            busyMessage?.let { message ->
                Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(message) }
                }
            }

            SettingsSectionCard("النسخ الاحتياطي المشفّر", "نسخة محلية قابلة للنقل ولا تعتمد على سحابة.") {
                Text("تُشفّر البيانات والمستندات بكلمة مرور تختارها أنت.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(Modifier.fillMaxWidth().testTag("create-encrypted-backup"), enabled = busyMessage == null,
                    onClick = { showCreatePasswordDialog = true }) { Text("إنشاء نسخة احتياطية") }
                OutlinedButton(Modifier.fillMaxWidth().testTag("restore-encrypted-backup"), enabled = busyMessage == null,
                    onClick = { openDocumentLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "*/*")) }) {
                    Text("استعادة نسخة احتياطية")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCreatePasswordDialog) BackupPasswordDialog(
        "حماية النسخة بكلمة مرور", "استخدم 8 أحرف على الأقل واحتفظ بالكلمة في مكان آمن.", "إنشاء النسخة", true,
        { showCreatePasswordDialog = false },
    ) { password ->
        showCreatePasswordDialog = false; scope.launch {
            busyMessage = "جارٍ إنشاء نسخة مشفّرة والتحقق منها…"; val chars = password.toCharArray()
            val result = try { runCatching { backupService.create(chars) } } finally { chars.fill('\u0000') }
            busyMessage = null
            result.fold(onSuccess = { pendingBackup = it; createDocumentLauncher.launch(defaultBackupFileName()) },
                onFailure = { showMessage(it.userFacingMessage("تعذر إنشاء النسخة الاحتياطية.")) })
        }
    }
    if (showRestorePasswordDialog) BackupPasswordDialog(
        "استعادة النسخة", "سيتم استبدال بيانات وَصل الحالية بعد التحقق الكامل.", "استعادة البيانات", false,
        { showRestorePasswordDialog = false; restoreBytes = null },
    ) { password ->
        val bytes = restoreBytes; showRestorePasswordDialog = false
        if (bytes != null) scope.launch {
            busyMessage = "جارٍ فك التشفير والتحقق والاستعادة…"; val chars = password.toCharArray()
            val result = try { runCatching { backupService.restore(bytes, chars) } } finally { chars.fill('\u0000'); restoreBytes = null }
            busyMessage = null
            result.fold(onSuccess = { onRestored(); showMessage("تمت الاستعادة بنجاح: ${it.rowCount} سجل و${it.documentCount} مستند.") },
                onFailure = { showMessage(it.userFacingMessage("تعذرت استعادة النسخة.")) })
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, description: String, onClick: () -> Unit, testTag: String) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("‹", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun PrivacySwitchRow(title: String, description: String, checked: Boolean, testTag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun BackupPasswordDialog(title: String, description: String, confirmLabel: String, requireConfirmation: Boolean,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    val longEnough = password.length >= MIN_PASSWORD_LENGTH; val matches = !requireConfirmation || password == confirmation
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(description)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("backup-password"),
                label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (requireConfirmation) OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth().testTag("backup-password-confirmation"),
                label = { Text("تأكيد كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        }
    }, confirmButton = { Button(onClick = { onConfirm(password) }, enabled = longEnough && matches,
        modifier = Modifier.testTag("backup-password-confirm")) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

private fun defaultBackupFileName(): String = "Wasl-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US))}.wasl"
private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0
    while (true) { val count = read(buffer); if (count < 0) break; total += count
        require(total <= limit) { "ملف النسخة أكبر من الحد المسموح." }; output.write(buffer, 0, count) }
    return output.toByteArray()
}
private fun Throwable.userFacingMessage(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback
private const val BACKUP_MIME_TYPE = "application/vnd.wasl.backup"
private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_BACKUP_IMPORT_BYTES = 256 * 1024 * 1024