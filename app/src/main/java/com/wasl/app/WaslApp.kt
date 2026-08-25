package com.wasl.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.UnavailablePaymentPromiseStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.reminder.ExactAlarmAccess
import com.wasl.app.reminder.NoOpReminderScheduler
import com.wasl.app.reminder.ReminderNotificationPublisher
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.document.UnavailablePaymentReceiptService
import com.wasl.app.document.ReceiptFileAccess
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import com.wasl.domain.PersonId
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data object TodayRoute : NavKey

@Serializable
private data object SearchRoute : NavKey

@Serializable
private data class AccountDetailsRoute(
    val debtId: String,
) : NavKey

private val supportedCurrencies = listOf(
    CurrencyCode.YER,
    CurrencyCode.SAR,
    CurrencyCode.USD,
)

private val dueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/uuuu",
    Locale.US,
)

@Composable
fun WaslApp(
    repository: WaslRepository,
    instanceKey: String = "production",
    reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    paymentReceiptService: PaymentReceiptService = UnavailablePaymentReceiptService,
    paymentPromiseStore: PaymentPromiseStore = UnavailablePaymentPromiseStore,
    todayClock: Clock = Clock.systemUTC(),
    todayZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    exactAlarmAccessOverride: Boolean? = null,
    requestedDebtId: String? = null,
    onRequestedDebtHandled: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(HomeRoute)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAvailable by remember {
        mutableStateOf(canPostNotifications(context))
    }
    var exactAlarmsAvailable by remember(exactAlarmAccessOverride) {
        mutableStateOf(exactAlarmAccessOverride ?: ExactAlarmAccess.canSchedule(context))
    }
    var permissionRequestAttempted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAvailable = canPostNotifications(context)
        if (granted && notificationsAvailable) reminderScheduler.requestRecovery()
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAvailable = canPostNotifications(context)
                val exactWasAvailable = exactAlarmsAvailable
                exactAlarmsAvailable = exactAlarmAccessOverride
                    ?: ExactAlarmAccess.canSchedule(context)
                if (!exactWasAvailable && exactAlarmsAvailable) {
                    reminderScheduler.requestRecovery()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun requestNotificationAccess() {
        when {
            canPostNotifications(context) -> reminderScheduler.requestRecovery()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationRuntimePermission(context) &&
                !permissionRequestAttempted -> {
                permissionRequestAttempted = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> openNotificationSettings(context)
        }
    }
    fun requestExactAlarmAccess() {
        if (exactAlarmAccessOverride != null) return
        ExactAlarmAccess.requestIntent(context)?.let(context::startActivity)
    }
    LaunchedEffect(requestedDebtId) {
        val debtId = requestedDebtId ?: return@LaunchedEffect
        if (backStack.lastOrNull() != AccountDetailsRoute(debtId)) {
            backStack.add(AccountDetailsRoute(debtId))
        }
        onRequestedDebtHandled()
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WaslTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    entryProvider = entryProvider {
                        entry<HomeRoute> {
                            val homeViewModel: HomeViewModel = viewModel(
                                key = "home:$instanceKey",
                                factory = HomeViewModel.Factory(repository, reminderScheduler),
                            )
                            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                            WaslHomeScreen(
                                state = state,
                                onOpenHome = {},
                                onOpenToday = {
                                    if (backStack.lastOrNull() != TodayRoute) {
                                        backStack.add(TodayRoute)
                                    }
                                },
                                onOpenSearch = {
                                    if (backStack.lastOrNull() != SearchRoute) {
                                        backStack.add(SearchRoute)
                                    }
                                },
                                onOpenCreate = homeViewModel::openCreateDialog,
                                onOpenAccount = { debtId ->
                                    backStack.add(AccountDetailsRoute(debtId.value))
                                },
                                onDismissCreate = homeViewModel::dismissCreateDialog,
                                onPersonModeChange = homeViewModel::updatePersonMode,
                                onPersonNameChange = homeViewModel::updatePersonName,
                                onPeopleQueryChange = homeViewModel::updatePeopleQuery,
                                onSelectPerson = homeViewModel::selectExistingPerson,
                                onRetryPeople = homeViewModel::retryPeople,
                                onAmountChange = homeViewModel::updateAmount,
                                onCurrencyChange = homeViewModel::updateCurrency,
                                onDirectionChange = homeViewModel::updateDirection,
                                onDescriptionChange = homeViewModel::updateDescription,
                                onDueDateChange = homeViewModel::updateDueDate,
                                onReminderChange = { enabled ->
                                    homeViewModel.updateRemindOnDueDate(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                },
                                onStrongAlarmChange = { enabled ->
                                    homeViewModel.updateStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                notificationPermissionGranted = notificationsAvailable,
                                exactAlarmAccessGranted = exactAlarmsAvailable,
                                onSave = homeViewModel::createDebt,
                                onSuccessShown = homeViewModel::clearSuccessMessage,
                            )
                        }
                        entry<TodayRoute> {
                            val todayViewModel: TodayViewModel = viewModel(
                                key = "today:$instanceKey",
                                factory = TodayViewModel.Factory(
                                    repository = repository,
                                    reminderScheduler = reminderScheduler,
                                    clock = todayClock,
                                    zoneIdProvider = todayZoneIdProvider,
                                    paymentPromiseStore = paymentPromiseStore,
                                ),
                            )
                            val state by todayViewModel.uiState.collectAsStateWithLifecycle()
                            TodayScreen(
                                state = state,
                                notificationsAvailable = notificationsAvailable,
                                onOpenHome = {
                                    while (backStack.size > 1) backStack.removeLastOrNull()
                                },
                                onOpenSearch = {
                                    if (backStack.lastOrNull() != SearchRoute) {
                                        backStack.add(SearchRoute)
                                    }
                                },
                                onOpenAccount = { debtId ->
                                    backStack.add(AccountDetailsRoute(debtId.value))
                                },
                                onRefreshDate = todayViewModel::refreshForCurrentDate,
                                onRetryLoad = todayViewModel::retryLoad,
                                onResolveNotificationPermission = ::requestNotificationAccess,
                                onRetryReminders = todayViewModel::retryReminderRecovery,
                                onNoticeShown = todayViewModel::clearNotice,
                            )
                        }
                        entry<SearchRoute> {
                            val searchViewModel: SearchViewModel = viewModel(
                                key = "search:$instanceKey",
                                factory = SearchViewModel.Factory(repository),
                            )
                            val state by searchViewModel.uiState.collectAsStateWithLifecycle()
                            SearchScreen(
                                state = state,
                                onQueryChange = searchViewModel::updateQuery,
                                onClearQuery = searchViewModel::clearQuery,
                                onRetryLoad = searchViewModel::retryLoad,
                                onOpenAccount = { debtId ->
                                    backStack.add(AccountDetailsRoute(debtId.value))
                                },
                                onOpenHome = {
                                    while (backStack.size > 1) backStack.removeLastOrNull()
                                },
                                onOpenToday = {
                                    if (backStack.lastOrNull() != TodayRoute) {
                                        backStack.add(TodayRoute)
                                    }
                                },
                            )
                        }
                        entry<AccountDetailsRoute> { route ->
                            val debtId = com.wasl.domain.DebtId(route.debtId)
                            val detailsViewModel: AccountDetailsViewModel = viewModel(
                                key = "account:$instanceKey:${route.debtId}",
                                factory = AccountDetailsViewModel.Factory(
                                    repository = repository,
                                    debtId = debtId,
                                    reminderScheduler = reminderScheduler,
                                    paymentReceiptService = paymentReceiptService,
                                    paymentPromiseStore = paymentPromiseStore,
                                ),
                            )
                            val state by detailsViewModel.uiState.collectAsStateWithLifecycle()
                            AccountDetailsScreen(
                                state = state,
                                onBack = { backStack.removeLastOrNull() },
                                onRetry = detailsViewModel::retryLoad,
                                onOpenPayment = detailsViewModel::openPaymentDialog,
                                onDismissPayment = detailsViewModel::dismissPaymentDialog,
                                onPaymentAmountChange = detailsViewModel::updatePaymentAmount,
                                onPaymentNoteChange = detailsViewModel::updatePaymentNote,
                                onReviewPayment = detailsViewModel::reviewPayment,
                                onEditPayment = detailsViewModel::editPayment,
                                onConfirmPayment = detailsViewModel::confirmPayment,
                                onOpenReversal = detailsViewModel::openReversalDialog,
                                onDismissReversal = detailsViewModel::dismissReversalDialog,
                                onReversalReasonChange = detailsViewModel::updateReversalReason,
                                onConfirmReversal = detailsViewModel::confirmReversal,
                                onOpenReceiptDialog = detailsViewModel::openReceiptDialog,
                                onDismissReceiptDialog = detailsViewModel::dismissReceiptDialog,
                                onReceiptIssuerNameChange = detailsViewModel::updateReceiptIssuerName,
                                onReceiptActivityNameChange = detailsViewModel::updateReceiptActivityName,
                                onReceiptPhoneChange = detailsViewModel::updateReceiptPhone,
                                onReceiptFooterChange = detailsViewModel::updateReceiptFooter,
                                onConfirmPaymentReceipt = detailsViewModel::confirmPaymentReceipt,
                                onRetryPaymentReceipt = detailsViewModel::retryPaymentReceipt,
                                onOpenReceipt = { document ->
                                    runCatching { ReceiptFileAccess.open(context, document) }
                                        .onFailure {
                                            android.widget.Toast.makeText(
                                                context,
                                                "لا يوجد تطبيق متاح لفتح ملف PDF.",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                },
                                onShareReceipt = { document ->
                                    runCatching { ReceiptFileAccess.share(context, document) }
                                        .onFailure {
                                            android.widget.Toast.makeText(
                                                context,
                                                "تعذرت مشاركة ملف الإيصال.",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                },
                                onReceiptReadyHandled = detailsViewModel::receiptReadyHandled,
                                onOpenDueSchedule = detailsViewModel::openDueScheduleDialog,
                                onDismissDueSchedule = detailsViewModel::dismissDueScheduleDialog,
                                onDueScheduleDateChange = detailsViewModel::updateDueScheduleDate,
                                onDueScheduleReminderChange = { enabled ->
                                    detailsViewModel.updateDueScheduleReminder(enabled)
                                    if (enabled && !notificationsAvailable) {
                                        requestNotificationAccess()
                                    }
                                },
                                onDueScheduleStrongAlarmChange = { enabled ->
                                    detailsViewModel.updateDueScheduleStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                onConfirmDueSchedule = detailsViewModel::confirmDueSchedule,
                                onOpenPaymentPromise = detailsViewModel::openPaymentPromiseDialog,
                                onDismissPaymentPromise = detailsViewModel::dismissPaymentPromiseDialog,
                                onPaymentPromiseDateChange = detailsViewModel::updatePaymentPromiseDate,
                                onPaymentPromiseNoteChange = detailsViewModel::updatePaymentPromiseNote,
                                onConfirmPaymentPromise = detailsViewModel::confirmPaymentPromise,
                                onOpenPaymentPromiseResolution = detailsViewModel::openPaymentPromiseResolution,
                                onDismissPaymentPromiseResolution = detailsViewModel::dismissPaymentPromiseResolution,
                                onPaymentPromiseResolutionNoteChange = detailsViewModel::updatePaymentPromiseResolutionNote,
                                onConfirmPaymentPromiseResolution = detailsViewModel::confirmPaymentPromiseResolution,
                                notificationPermissionGranted = notificationsAvailable,
                                exactAlarmAccessGranted = exactAlarmsAvailable,
                                onNoticeShown = detailsViewModel::clearNotice,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WaslHomeScreen(
    state: HomeUiState,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,
    onDismissCreate: () -> Unit,
    onPersonModeChange: (DebtPersonMode) -> Unit,
    onPersonNameChange: (String) -> Unit,
    onPeopleQueryChange: (String) -> Unit,
    onSelectPerson: (PersonId) -> Unit,
    onRetryPeople: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
    onSuccessShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage) {
        val message = state.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSuccessShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WaslTopLevelNavigation(
                selected = WaslTopLevelDestination.HOME,
                onOpenHome = onOpenHome,
                onOpenToday = onOpenToday,
                onOpenSearch = onOpenSearch,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("إضافة حساب", fontWeight = FontWeight.Bold)
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 22.dp,
                end = 20.dp,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("home-header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "دفترك المالي الشخصي",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = "وَصل",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "كل حساب له وصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isLoading) {
                item("home-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item("home-overview-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "ملخصك المالي",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "نظرة سريعة على الحقوق والالتزامات حسب العملة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("home-receivable-summary") {
                    SummaryCard(
                        title = "لي عند الناس",
                        subtitle = "حقوقك المفتوحة",
                        values = summaryRows(state.balanceSummary.receivableByCurrency),
                        receivable = true,
                    )
                }
                item("home-payable-summary") {
                    SummaryCard(
                        title = "عليّ للناس",
                        subtitle = "التزاماتك المفتوحة",
                        values = summaryRows(state.balanceSummary.payableByCurrency),
                        receivable = false,
                    )
                }

                state.loadError?.let { error ->
                    item("home-error") { StatusCard(message = error, isError = true) }
                }

                if (state.accounts.isEmpty() && state.loadError == null) {
                    item("home-empty") {
                        StatusCard(
                            message = "لا توجد حسابات بعد. أضف شخصًا ودينًا، وسيبقى محفوظًا بعد إغلاق التطبيق.",
                            isError = false,
                        )
                    }
                } else {
                    item("home-accounts-heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "الحسابات",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${state.accounts.size} حساب محفوظ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = state.accounts.size.toString(),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    items(
                        items = state.accounts,
                        key = { it.ledger.header.id.value },
                    ) { account ->
                        AccountCard(
                            account = account,
                            onClick = { onOpenAccount(account.ledger.header.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.isCreateDialogOpen) {
        CreateDebtDialog(
            form = state.createForm,
            isSaving = state.isSaving,
            error = state.formError,
            peopleQuery = state.peopleQuery,
            selectablePeople = state.selectablePeople,
            isPeopleLoading = state.isPeopleLoading,
            peopleLoadError = state.peopleLoadError,
            hasMorePeople = state.hasMorePeople,
            onDismiss = onDismissCreate,
            onPersonModeChange = onPersonModeChange,
            onPersonNameChange = onPersonNameChange,
            onPeopleQueryChange = onPeopleQueryChange,
            onSelectPerson = onSelectPerson,
            onRetryPeople = onRetryPeople,
            onAmountChange = onAmountChange,
            onCurrencyChange = onCurrencyChange,
            onDirectionChange = onDirectionChange,
            onDescriptionChange = onDescriptionChange,
            onDueDateChange = onDueDateChange,
            onReminderChange = onReminderChange,
            onStrongAlarmChange = onStrongAlarmChange,
            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onSave = onSave,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreateDebtDialog(
    form: CreateDebtForm,
    isSaving: Boolean,
    error: String?,
    peopleQuery: String,
    selectablePeople: List<PersonRecord>,
    isPeopleLoading: Boolean,
    peopleLoadError: String?,
    hasMorePeople: Boolean,
    onDismiss: () -> Unit,
    onPersonModeChange: (DebtPersonMode) -> Unit,
    onPersonNameChange: (String) -> Unit,
    onPeopleQueryChange: (String) -> Unit,
    onSelectPerson: (PersonId) -> Unit,
    onRetryPeople: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("دين جديد")
                Text(
                    text = "سجّل حقًا لك أو التزامًا عليك بدون تعقيد.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("الشخص", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.personMode == DebtPersonMode.NEW,
                        onClick = { onPersonModeChange(DebtPersonMode.NEW) },
                        label = { Text("شخص جديد") },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("create-person-mode-new"),
                    )
                    FilterChip(
                        selected = form.personMode == DebtPersonMode.EXISTING,
                        onClick = { onPersonModeChange(DebtPersonMode.EXISTING) },
                        label = { Text("شخص موجود") },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("create-person-mode-existing"),
                    )
                }

                if (form.personMode == DebtPersonMode.NEW) {
                    OutlinedTextField(
                        value = form.personName,
                        onValueChange = onPersonNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create-person-name"),
                        label = { Text("اسم الشخص") },
                        singleLine = true,
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                    )
                } else {
                    form.selectedPerson?.let { selected ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selected-existing-person"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Text(
                                text = "الشخص المحدد: ${selected.displayName}",
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = peopleQuery,
                        onValueChange = onPeopleQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("existing-person-query"),
                        label = { Text("ابحث باسم الشخص") },
                        singleLine = true,
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                    )
                    when {
                        isPeopleLoading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }

                        peopleLoadError != null -> Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                peopleLoadError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(
                                onClick = onRetryPeople,
                                enabled = !isSaving,
                                modifier = Modifier.testTag("retry-existing-people"),
                            ) {
                                Text("إعادة المحاولة")
                            }
                        }

                        selectablePeople.isEmpty() -> Text(
                            text = if (peopleQuery.isBlank()) {
                                "لا يوجد شخص محفوظ بعد. اختر «شخص جديد» أولًا."
                            } else {
                                "لا يوجد شخص مطابق لهذا الاسم."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> {
                            selectablePeople.forEach { person ->
                                OutlinedButton(
                                    onClick = { onSelectPerson(person.id) },
                                    enabled = !isSaving,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("existing-person-${person.id.value}"),
                                ) {
                                    Text(person.displayName)
                                }
                            }
                            if (hasMorePeople) {
                                Text(
                                    "توجد نتائج إضافية؛ اكتب جزءًا أدق من الاسم.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Text("تاريخ الاستحقاق", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create-due-date"),
                    ) {
                        Text(
                            form.dueDate?.format(dueDateFormatter)
                                ?: "اختيار تاريخ — اختياري",
                        )
                    }
                    if (form.dueDate != null) {
                        TextButton(
                            onClick = { onDueDateChange(null) },
                            enabled = !isSaving,
                        ) {
                            Text("إزالة")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("متابعة ذكية للاستحقاق", fontWeight = FontWeight.SemiBold)
                        Text(
                            "قبل الموعد بيوم، يوم الموعد، بعد يومين، ثم أسبوعيًا حتى السداد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.remindOnDueDate,
                        onCheckedChange = onReminderChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("create-due-reminder"),
                    )
                }
                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "ستُحفظ المتابعة، لكن لن يظهر الإشعار حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("منبه قوي إضافي", fontWeight = FontWeight.SemiBold)
                        Text(
                            "منبه دقيق قرابة 09:00 يوم الاستحقاق مع إبقاء المتابعة الذكية كبديل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.strongAlarmEnabled,
                        onCheckedChange = onStrongAlarmChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("create-strong-alarm"),
                    )
                }
                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Text(
                        "Android يحتاج إذن «المنبهات والتذكيرات» لتشغيل المنبه القوي بدقة. المتابعة الذكية ستبقى فعالة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("create-exact-alarm-permission-warning"),
                    )
                }

                Text("اتجاه الدين", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.direction == DebtDirection.RECEIVABLE,
                        onClick = { onDirectionChange(DebtDirection.RECEIVABLE) },
                        label = { Text("لي عنده") },
                        enabled = !isSaving,
                    )
                    FilterChip(
                        selected = form.direction == DebtDirection.PAYABLE,
                        onClick = { onDirectionChange(DebtDirection.PAYABLE) },
                        label = { Text("عليّ له") },
                        enabled = !isSaving,
                    )
                }

                OutlinedTextField(
                    value = form.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-debt-amount"),
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                )

                Text("العملة", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    supportedCurrencies.forEach { currency ->
                        FilterChip(
                            selected = form.currency == currency,
                            onClick = { onCurrencyChange(currency) },
                            label = { Text(currency.value) },
                            enabled = !isSaving,
                        )
                    }
                }

                OutlinedTextField(
                    value = form.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البيان — اختياري") },
                    minLines = 2,
                    maxLines = 3,
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                )

                error?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.testTag("create-debt-save"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else "حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("إلغاء")
            }
        },
    )

    if (showDatePicker) {
        val initialSelection = form.dueDate
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialSelection,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            onDueDateChange(
                                Instant.ofEpochMilli(selected)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text("اختيار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountOverview,
    onClick: () -> Unit,
) {
    val header = account.ledger.header
    val receivable = header.direction == DebtDirection.RECEIVABLE
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account-${header.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = MaterialTheme.shapes.large,
                    color = if (receivable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = account.person.displayName.trim().firstOrNull()?.toString() ?: "و",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (receivable) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = account.person.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    header.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (receivable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Text(
                        text = if (receivable) "لي عنده" else "عليّ له",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (receivable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "المتبقي",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMoney(account.ledger.balance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Start,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = when (account.ledger.state) {
                        DebtState.SETTLED -> MaterialTheme.colorScheme.primaryContainer
                        DebtState.PARTIALLY_PAID -> MaterialTheme.colorScheme.tertiaryContainer
                        DebtState.OPEN -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        text = when (account.ledger.state) {
                            DebtState.OPEN -> "مفتوح"
                            DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
                            DebtState.SETTLED -> "مسدد"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "الأصل",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMoney(header.originalAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, money: Money) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(
            text = formatMoney(money),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    values: List<String>,
    receivable: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (receivable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (receivable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (receivable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            HorizontalDivider(
                color = if (receivable) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.18f)
                },
            )
            values.forEachIndexed { index, value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = supportedCurrencies[index].value,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (receivable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                    Text(
                        text = value,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (receivable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = if (isError) "تعذر تحميل البيانات" else "ابدأ أول حساب",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun summaryRows(values: Map<CurrencyCode, Money>): List<String> =
    supportedCurrencies.map { currency ->
        formatMoney(values[currency] ?: Money.zero(currency))
    }

private fun hasNotificationRuntimePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun canPostNotifications(context: Context): Boolean {
    if (!hasNotificationRuntimePermission(context) ||
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    ) {
        return false
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val channel = context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(ReminderNotificationPublisher.DUE_ACCOUNTS_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
    )
}

internal fun formatMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return "\u2066${formatter.format(major)} ${money.currency.value}\u2069"
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun WaslHomeScreenPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WaslTheme {
            WaslHomeScreen(
                state = HomeUiState(isLoading = false),
                onOpenHome = {},
                onOpenToday = {},
                onOpenSearch = {},
                onOpenCreate = {},
                onOpenAccount = {},
                onDismissCreate = {},
                onPersonModeChange = {},
                onPersonNameChange = {},
                onPeopleQueryChange = {},
                onSelectPerson = {},
                onRetryPeople = {},
                onAmountChange = {},
                onCurrencyChange = {},
                onDirectionChange = {},
                onDescriptionChange = {},
                onDueDateChange = {},
                onReminderChange = {},
                onStrongAlarmChange = {},
                notificationPermissionGranted = true,
                exactAlarmAccessGranted = true,
                onSave = {},
                onSuccessShown = {},
            )
        }
    }
}
