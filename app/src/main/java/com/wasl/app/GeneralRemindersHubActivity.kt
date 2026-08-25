package com.wasl.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.privacy.PrivacyPreferences
import com.wasl.app.reminder.GeneralReminderService
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.DebtDirection
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch

class GeneralRemindersHubActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as WaslApplication
        applySecureScreen(application.privacyPreferences)
        enableEdgeToEdge()
        setContent {
            WaslTheme {
                GeneralRemindersHubRoute(
                    repository = application.repository,
                    store = application.generalReminderStore,
                    service = application.generalReminderService,
                    onBack = ::finish,
                )
            }
        }
    }

    private fun applySecureScreen(preferences: PrivacyPreferences) {
        if (preferences.secureScreen || preferences.appLockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
internal fun GeneralRemindersHubRoute(
    repository: WaslRepository,
    store: GeneralReminderStore,
    service: GeneralReminderService,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accounts by remember { mutableStateOf<List<AccountOverview>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var notificationsAvailable by remember {
        mutableStateOf(canPostGeneralReminderNotifications(context))
    }
    var permissionRequestAttempted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAvailable = canPostGeneralReminderNotifications(context)
        if (granted && notificationsAvailable) service.requestRecovery()
    }

    fun requestNotificationAccess() {
        when {
            canPostGeneralReminderNotifications(context) -> {
                notificationsAvailable = true
                service.requestRecovery()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED &&
                !permissionRequestAttempted -> {
                permissionRequestAttempted = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> openGeneralReminderNotificationSettings(context)
        }
    }

    LaunchedEffect(repository) {
        repository.observeAccounts()
            .catch { error ->
                if (error is CancellationException) throw error
                loadError = "تعذر قراءة الحسابات المحفوظة."
                accounts = emptyList()
            }
            .collect { loaded ->
                accounts = loaded
                loadError = null
            }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasAvailable = notificationsAvailable
                notificationsAvailable = canPostGeneralReminderNotifications(context)
                if (!wasAvailable && notificationsAvailable) {
                    service.requestRecovery()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.testTag("general-reminders-back"),
                        onClick = onBack,
                    ) {
                        Text("رجوع")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "التذكيرات",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "متابعة مستقلة عن تاريخ الاستحقاق لكل حساب.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                accounts == null -> item("loading") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("جارٍ قراءة الحسابات…")
                        }
                    }
                }

                loadError != null -> item("error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = requireNotNull(loadError),
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                accounts.isNullOrEmpty() -> item("empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            text = "لا توجد حسابات محفوظة لإضافة تذكير متابعة لها.",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> items(
                    items = requireNotNull(accounts),
                    key = { it.ledger.header.id.value },
                ) { account ->
                    GeneralReminderAccountCard(
                        account = account,
                        store = store,
                        service = service,
                        notificationsAvailable = notificationsAvailable,
                        onRequestNotificationAccess = ::requestNotificationAccess,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralReminderAccountCard(
    account: AccountOverview,
    store: GeneralReminderStore,
    service: GeneralReminderService,
    notificationsAvailable: Boolean,
    onRequestNotificationAccess: () -> Unit,
) {
    val debtId = account.ledger.header.id
    val reminderViewModel: GeneralReminderViewModel = viewModel(
        key = "general-reminder-hub:${debtId.value}",
        factory = GeneralReminderViewModel.Factory(
            store = store,
            service = service,
            debtId = debtId,
        ),
    )
    val reminderState by reminderViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("general-reminder-account-${debtId.value}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = account.person.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildString {
                    append(
                        if (account.ledger.header.direction == DebtDirection.RECEIVABLE) {
                            "لي عنده"
                        } else {
                            "عليّ له"
                        },
                    )
                    append(" • ")
                    append(formatGeneralReminderBalance(account))
                    account.ledger.header.description
                        ?.takeIf { it.isNotBlank() }
                        ?.let { append(" • ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GeneralReminderSection(
            state = reminderState,
            canConfigure = !account.ledger.balance.isZero,
            notificationsAvailable = notificationsAvailable,
            onRetryLoad = reminderViewModel::retryLoad,
            onOpen = reminderViewModel::openDialog,
            onDismiss = reminderViewModel::dismissDialog,
            onDateChange = reminderViewModel::updateDate,
            onTimeChange = reminderViewModel::updateTime,
            onFrequencyChange = reminderViewModel::updateFrequency,
            onSave = reminderViewModel::save,
            onCancel = reminderViewModel::cancel,
            onRequestNotificationAccess = onRequestNotificationAccess,
        )
    }
}

private fun formatGeneralReminderBalance(account: AccountOverview): String {
    val money = account.ledger.balance
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val value = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = true
    }
    return "${formatter.format(value)} ${money.currency.value}"
}

private fun canPostGeneralReminderNotifications(context: Context): Boolean {
    val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    return runtimePermissionGranted &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openGeneralReminderNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
