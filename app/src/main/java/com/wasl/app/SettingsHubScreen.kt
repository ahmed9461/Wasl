package com.wasl.app

import android.net.Uri
import androidx.activity.compose.BackHandler
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
    appearance: AppAppearance = privacyPreferences.appearance,
    onAppearanceChange: (AppAppearance) -> Unit = { privacyPreferences.appearance = it },
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenDocuments: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRestored: () -> Unit,
    onSecureScreenChanged: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var hideSensitiveNotifications by remember {
        mutableStateOf(privacyPreferences.hideSensitiveNotifications)
    }
    var secureScreen by remember {
        mutableStateOf(privacyPreferences.secureScreen)
    }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var createPasswordDialog by remember { mutableStateOf(false) }
    var restorePasswordDialog by remember { mutableStateOf(false) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBackup by remember { mutableStateOf<BackupCreated?>(null) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val saveBackup = rememberLauncherForActivityResult(
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
                    message("تم حفظ النسخة: ${backup.rowCount} سجل و${backup.documentCount} مستند.")
                },
                onFailure = { message(it.userFacingMessage("تعذر حفظ النسخة الاحتياطية.")) },
            )
        }
    }

    val openBackup = rememberLauncherForActivityResult(
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
                onSuccess = {
                    restoreBytes = it
                    restorePasswordDialog = true
                },
                onFailure = { message(it.userFacingMessage("تعذر قراءة ملف النسخة.")) },
            )
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings-hub"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 18.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader(onBack)

            SettingsCard(
                title = "المظهر",
                subtitle = "اختر المظهر المريح لك؛ التلقائي يتبع إعداد الجهاز.",
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (shouldStackDenseRows(maxWidth)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppAppearance.entries.forEach { option ->
                                FilterChip(
                                    selected = appearance == option,
                                    onClick = { onAppearanceChange(option) },
                                    label = { Text(option.label) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("appearance-${option.storedValue}"),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppAppearance.entries.forEach { option ->
                                FilterChip(
                                    selected = appearance == option,
                                    onClick = { onAppearanceChange(option) },
                                    label = { Text(option.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("appearance-${option.storedValue}"),
                                )
                            }
                        }
                    }
                }
            }

            SettingsCard(
                title = "الأمان والخصوصية",
                subtitle = "خيارات الحماية في مكان واحد بدون أزرار عائمة.",
            ) {
                SettingsActionRow(
                    title = "قفل التطبيق",
                    description = "البصمة أو قفل الجهاز وإعداد مهلة القفل",
                    testTag = "open-security",
                    onClick = onOpenSecurity,
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    title = "إخفاء تفاصيل الإشعارات",
                    description = "يعرض إشعارًا عامًا بدون اسم الشخص أو المبلغ.",
                    checked = hideSensitiveNotifications,
                    testTag = "privacy-hide-notification-details",
                    onCheckedChange = {
                        hideSensitiveNotifications = it
                        privacyPreferences.hideSensitiveNotifications = it
                    },
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    title = "حماية الشاشة",
                    description = "يمنع لقطات الشاشة ويخفي المعاينة من التطبيقات الأخيرة.",
                    checked = secureScreen,
                    testTag = "privacy-secure-screen",
                    onCheckedChange = {
                        secureScreen = it
                        privacyPreferences.secureScreen = it
                        onSecureScreenChanged()
                    },
                )
            }

            SettingsCard(
                title = "التذكيرات",
                subtitle = "إدارة التذكيرات العامة ومواعيد المتابعة.",
            ) {
                SettingsActionRow(
                    title = "مركز التذكيرات",
                    description = "إضافة ومراجعة التذكيرات المحفوظة",
                    testTag = "open-reminders",
                    onClick = onOpenReminders,
                )
            }

            SettingsCard(
                title = "الأدوات والتقارير",
                subtitle = "وصول منظم إلى مستنداتك وإحصاءاتك.",
            ) {
                SettingsActionRow(
                    title = "المستندات",
                    description = "إيصالات الدين وكشوف الحساب",
                    testTag = "open-documents-hub",
                    onClick = onOpenDocuments,
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = "الإحصاءات",
                    description = "مؤشرات موضوعية بدون خلط العملات",
                    testTag = "open-objective-statistics",
                    onClick = onOpenStatistics,
                )
            }

            busyMessage?.let { current ->
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
                        Text(current)
                    }
                }
            }

            SettingsCard(
                title = "النسخ الاحتياطي المشفّر",
                subtitle = "نسخة محلية قابلة للنقل ولا تعتمد على سحابة.",
            ) {
                Text(
                    text = "تُشفّر البيانات والمستندات بكلمة مرور تختارها أنت.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-encrypted-backup"),
                    enabled = busyMessage == null,
                    onClick = { createPasswordDialog = true },
                ) {
                    Text("إنشاء نسخة احتياطية")
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore-encrypted-backup"),
                    enabled = busyMessage == null,
                    onClick = {
                        openBackup.launch(
                            arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "*/*"),
                        )
                    },
                ) {
                    Text("استعادة نسخة احتياطية")
                }
            }
        }
    }

    if (createPasswordDialog) {
        BackupPasswordDialog(
            title = "حماية النسخة بكلمة مرور",
            description = "استخدم 8 أحرف على الأقل واحتفظ بالكلمة في مكان آمن.",
            confirmLabel = "إنشاء النسخة",
            requireConfirmation = true,
            onDismiss = { createPasswordDialog = false },
            onConfirm = { password ->
                createPasswordDialog = false
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
                        onSuccess = {
                            pendingBackup = it
                            saveBackup.launch(defaultBackupFileName())
                        },
                        onFailure = {
                            message(it.userFacingMessage("تعذر إنشاء النسخة الاحتياطية."))
                        },
                    )
                }
            },
        )
    }

    if (restorePasswordDialog) {
        BackupPasswordDialog(
            title = "استعادة النسخة",
            description = "سيتم استبدال بيانات وَصل الحالية بعد التحقق الكامل.",
            confirmLabel = "استعادة البيانات",
            requireConfirmation = false,
            onDismiss = {
                restorePasswordDialog = false
                restoreBytes = null
            },
            onConfirm = { password ->
                val bytes = restoreBytes
                restorePasswordDialog = false
                if (bytes != null) {
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
                            onSuccess = {
                                onRestored()
                                message("تمت الاستعادة بنجاح: ${it.rowCount} سجل و${it.documentCount} مستند.")
                            },
                            onFailure = {
                                message(it.userFacingMessage("تعذرت استعادة النسخة."))
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("رجوع") }
                SettingsHeaderText()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-header-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text("رجوع") }
                SettingsHeaderText(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsHeaderText(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "الإعدادات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "المظهر، الأمان، التذكيرات والنسخ الاحتياطي",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTag-row-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsSwitchText(title, description)
                Switch(
                    modifier = Modifier.testTag(testTag),
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTag-row-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSwitchText(title, description, Modifier.weight(1f))
                Switch(
                    modifier = Modifier.testTag(testTag),
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchText(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = title, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup-password-confirmation"),
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
    val timestamp = LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US),
    )
    return "Wasl-$timestamp.wasl"
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
