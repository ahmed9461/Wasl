package com.wasl.app

import android.net.Uri
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
    onBack: () -> Unit,
    onRestored: () -> Unit,
    onSecureScreenChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hideSensitiveNotifications by remember {
        mutableStateOf(privacyPreferences.hideSensitiveNotifications)
    }
    var secureScreen by remember {
        mutableStateOf(privacyPreferences.secureScreen)
    }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var showCreatePasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBackup by remember { mutableStateOf<BackupCreated?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri: Uri? ->
        val backup = pendingBackup
        pendingBackup = null
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "جارٍ حفظ النسخة المشفّرة…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(backup.bytes)
                        output.flush()
                    } ?: error("تعذر فتح الملف المحدد للكتابة.")
                }
            }
            busyMessage = null
            result.fold(
                onSuccess = {
                    showMessage(
                        "تم حفظ النسخة: ${backup.rowCount} سجل و${backup.documentCount} مستند.",
                    )
                },
                onFailure = { showMessage(it.userFacingMessage("تعذر حفظ النسخة الاحتياطية.")) },
            )
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "جارٍ فحص ملف النسخة…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytesLimited(MAX_BACKUP_IMPORT_BYTES)
                    } ?: error("تعذر فتح ملف النسخة المحدد.")
                }
            }
            busyMessage = null
            result.fold(
                onSuccess = { bytes ->
                    restoreBytes = bytes
                    showRestorePasswordDialog = true
                },
                onFailure = { showMessage(it.userFacingMessage("تعذر قراءة ملف النسخة.")) },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 20.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("رجوع")
                }
                Column {
                    Text(
                        text = "الإعدادات والخصوصية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "حماية بيانات وَصل والنسخ الاحتياطي المحلي",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            busyMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(message, fontWeight = FontWeight.Medium)
                    }
                }
            }

            SettingsSectionCard(
                title = "الخصوصية",
                subtitle = "تحكم بما يمكن أن يظهر خارج شاشة وَصل.",
            ) {
                PrivacySwitchRow(
                    title = "إخفاء تفاصيل الإشعارات",
                    description = "يعرض تنبيهًا عامًا بدون اسم الشخص أو المبلغ.",
                    checked = hideSensitiveNotifications,
                    testTag = "privacy-hide-notification-details",
                    onCheckedChange = { value ->
                        hideSensitiveNotifications = value
                        privacyPreferences.hideSensitiveNotifications = value
                    },
                )
                HorizontalDivider()
                PrivacySwitchRow(
                    title = "حماية الشاشة",
                    description = "يمنع لقطات الشاشة ويخفي معاينة وَصل من التطبيقات الأخيرة أثناء التفعيل.",
                    checked = secureScreen,
                    testTag = "privacy-secure-screen",
                    onCheckedChange = { value ->
                        secureScreen = value
                        privacyPreferences.secureScreen = value
                        onSecureScreenChanged()
                    },
                )
            }

            SettingsSectionCard(
                title = "النسخ الاحتياطي المشفّر",
                subtitle = "نسخة يدوية قابلة للنقل لجهاز آخر، ولا تعتمد على حساب أو سحابة.",
            ) {
                Text(
                    text = "تُشفّر البيانات والمستندات بكلمة مرور تختارها أنت. لا يمكن لوَصل استرجاع كلمة المرور إذا فقدتها.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-encrypted-backup"),
                    enabled = busyMessage == null,
                    onClick = { showCreatePasswordDialog = true },
                ) {
                    Text("إنشاء نسخة احتياطية مشفّرة")
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore-encrypted-backup"),
                    enabled = busyMessage == null,
                    onClick = {
                        openDocumentLauncher.launch(
                            arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "*/*"),
                        )
                    },
                ) {
                    Text("استعادة نسخة احتياطية")
                }
                Text(
                    text = "الاستعادة تستبدل بيانات وَصل الحالية فقط بعد فك التشفير والتحقق من سلامة الملف وقاعدة البيانات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCreatePasswordDialog) {
        BackupPasswordDialog(
            title = "حماية النسخة بكلمة مرور",
            description = "استخدم 8 أحرف على الأقل. احتفظ بالكلمة في مكان آمن لأنها مطلوبة عند الاستعادة.",
            confirmLabel = "إنشاء النسخة",
            requireConfirmation = true,
            onDismiss = { showCreatePasswordDialog = false },
            onConfirm = { password ->
                showCreatePasswordDialog = false
                scope.launch {
                    busyMessage = "جارٍ إنشاء نسخة مشفّرة والتحقق منها…"
                    val chars = password.toCharArray()
                    val result = try {
                        runCatching { backupService.create(chars) }
                    } finally {
                        chars.fill('\u0000')
                    }
                    busyMessage = null
                    result.fold(
                        onSuccess = { backup ->
                            pendingBackup = backup
                            createDocumentLauncher.launch(defaultBackupFileName())
                        },
                        onFailure = {
                            showMessage(it.userFacingMessage("تعذر إنشاء النسخة الاحتياطية."))
                        },
                    )
                }
            },
        )
    }

    if (showRestorePasswordDialog) {
        BackupPasswordDialog(
            title = "استعادة النسخة",
            description = "سيتم استبدال بيانات وَصل الحالية بالنسخة المحددة بعد التحقق منها بالكامل.",
            confirmLabel = "استعادة واستبدال البيانات",
            requireConfirmation = false,
            onDismiss = {
                showRestorePasswordDialog = false
                restoreBytes = null
            },
            onConfirm = { password ->
                val bytes = restoreBytes
                showRestorePasswordDialog = false
                if (bytes == null) return@BackupPasswordDialog
                scope.launch {
                    busyMessage = "جارٍ فك التشفير والتحقق والاستعادة…"
                    val chars = password.toCharArray()
                    val result = try {
                        runCatching { backupService.restore(bytes, chars) }
                    } finally {
                        chars.fill('\u0000')
                        restoreBytes = null
                    }
                    busyMessage = null
                    result.fold(
                        onSuccess = { restored ->
                            onRestored()
                            showMessage(
                                "تمت الاستعادة بنجاح: ${restored.rowCount} سجل و${restored.documentCount} مستند.",
                            )
                        },
                        onFailure = {
                            showMessage(it.userFacingMessage("تعذرت استعادة النسخة."))
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            modifier = Modifier.testTag(testTag),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val longEnough = password.length >= MIN_PASSWORD_LENGTH
    val matches = !requireConfirmation || password == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description)
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup-password"),
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("كلمة المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        if (password.isNotEmpty() && !longEnough) {
                            Text("يجب أن تكون 8 أحرف على الأقل.")
                        }
                    },
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup-password-confirmation"),
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        label = { Text("تأكيد كلمة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            if (confirmation.isNotEmpty() && !matches) {
                                Text("كلمتا المرور غير متطابقتين.")
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("backup-password-confirm"),
                enabled = longEnough && matches,
                onClick = { onConfirm(password) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

private fun defaultBackupFileName(): String {
    val stamp = LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US),
    )
    return "Wasl-$stamp.wasl"
}

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "ملف النسخة أكبر من الحد المسموح." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun Throwable.userFacingMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

private const val BACKUP_MIME_TYPE = "application/vnd.wasl.backup"
private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_BACKUP_IMPORT_BYTES = 256 * 1024 * 1024
