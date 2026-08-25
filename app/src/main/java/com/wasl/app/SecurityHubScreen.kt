package com.wasl.app

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.privacy.AppLockTimeout

@Composable
internal fun SecuritySettingsEntryButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            modifier = Modifier.testTag("open-security-hub"),
            onClick = onClick,
        ) {
            Text("الأمان")
        }
        OutlinedButton(
            modifier = Modifier.testTag("open-general-reminders-hub"),
            onClick = {
                context.startActivity(Intent(context, GeneralRemindersHubActivity::class.java))
            },
        ) {
            Text("التذكيرات")
        }
    }
}

@Composable
internal fun SecurityHubRoute(
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    authenticationAvailable: Boolean,
    statusMessage: String?,
    onBack: () -> Unit,
    onAppLockEnabledChange: (Boolean) -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onLockNow: () -> Unit,
) {
    var selectedTimeout by remember(appLockTimeout) {
        mutableStateOf(appLockTimeout)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            modifier = Modifier.semantics { heading() },
                            text = "الأمان وقفل وَصل",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "حماية محلية تعتمد على مصادقة Android النظامية.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "قفل التطبيق",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "يفتح وَصل بالبصمة أو الوجه المدعوم أو رمز/نمط/كلمة مرور قفل الجهاز. لا يخزن وَصل PIN خاصًا به.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = if (appLockEnabled) "قفل وَصل مفعّل" else "تفعيل قفل وَصل",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (authenticationAvailable) {
                                        "المصادقة النظامية متاحة على هذا الجهاز."
                                    } else {
                                        "لا توجد وسيلة مصادقة نظامية متاحة حاليًا."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                modifier = Modifier.testTag("app-lock-enabled"),
                                checked = appLockEnabled,
                                enabled = appLockEnabled || authenticationAvailable,
                                onCheckedChange = onAppLockEnabledChange,
                            )
                        }

                        if (!authenticationAvailable) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    modifier = Modifier.padding(14.dp),
                                    text = "فعّل بصمة أو قفل شاشة آمن من إعدادات Android ثم عد إلى وَصل. إذا كان القفل مفعّلًا ثم أصبحت المصادقة غير متاحة، يمكن تعطيله من شاشة الاسترداد بدون حذف أي بيانات.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                        statusMessage?.let { message ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app-lock-status"),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    modifier = Modifier.padding(14.dp),
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        if (appLockEnabled) {
                            HorizontalDivider()
                            Text(
                                text = "إعادة القفل بعد مغادرة التطبيق",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "تسمح المهلة القصيرة بالعودة من منتقي الملفات أو إعدادات النظام بدون مطالبة متكررة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppLockTimeout.entries.forEach { timeout ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("app-lock-timeout-${timeout.storedValue}")
                                        .clickable {
                                            selectedTimeout = timeout
                                            onAppLockTimeoutChange(timeout)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RadioButton(
                                        selected = selectedTimeout == timeout,
                                        onClick = null,
                                    )
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = timeout.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }

                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app-lock-lock-now"),
                                onClick = onLockNow,
                            ) {
                                Text("قفل وَصل الآن")
                            }
                        }
                    }
                }

                Text(
                    text = "قفل وَصل طبقة خصوصية إضافية فوق قفل الجهاز، ولا يشفر قاعدة البيانات بمفرده. النسخ الاحتياطية القابلة للنقل تبقى مشفرة بكلمة المرور التي يختارها المستخدم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun AppLockScreen(
    authenticationAvailable: Boolean,
    message: String?,
    onUnlock: () -> Unit,
    onDisableUnavailableLock: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app-lock-screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = "وَصل مقفل",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (authenticationAvailable) {
                        "استخدم البصمة أو قفل الجهاز للمتابعة."
                    } else {
                        "تعذر استخدام المصادقة النظامية على هذا الجهاز الآن."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                message?.let {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app-lock-message"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            modifier = Modifier.padding(14.dp),
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                if (authenticationAvailable) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app-lock-unlock"),
                        onClick = onUnlock,
                    ) {
                        Text("فتح وَصل")
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app-lock-recovery-disable"),
                        onClick = onDisableUnavailableLock,
                    ) {
                        Text("تعطيل القفل على هذا الجهاز")
                    }
                    Text(
                        text = "هذا الإجراء لا يحذف البيانات. استخدمه فقط إذا أزيل قفل الجهاز أو أصبحت المصادقة غير متاحة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
