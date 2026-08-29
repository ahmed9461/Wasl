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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.wasl.app.reminder.ReminderNotificationActions
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
    requestedPaymentIntent: String? = null,
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
    LaunchedEffect(requestedDebtId, requestedPaymentIntent) {
        val debtId = requestedDebtId ?: return@LaunchedEffect
        if (backStack.lastOrNull() != AccountDetailsRoute(debtId)) {
            backStack.add(AccountDetailsRoute(debtId))
        }
        if (requestedPaymentIntent == null) {
            onRequestedDebtHandled()
        }
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
                            CompositionLocalProvider(
                                LocalStrongAlarmTimeChange provides homeViewModel::updateStrongAlarmTime,
                            ) {
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
                                    onOpenCreate = homeViewModel::openCreateTypePicker,
                                    onDismissCreateTypePicker = homeViewModel::dismissCreateTypePicker,
                                    onCreateIndividual = homeViewModel::openCreateDialog,
                                    onCreateGroupExpense = homeViewModel::openGroupExpenseDialog,
                                    onOpenAccount = { debtId ->
                                        backStack.add(AccountDetailsRoute(debtId.value))
                                    },
                                    onDismissCreate = homeViewModel::dismissCreateDialog,
                                    onDismissGroupExpense = homeViewModel::dismissGroupExpenseDialog,
                                    onToggleGroupParticipant = homeViewModel::toggleGroupParticipant,
                                    onGroupParticipantAmountChange = homeViewModel::updateGroupParticipantAmount,
                                    onGroupCurrencyChange = homeViewModel::updateGroupCurrency,
                                    onGroupDirectionChange = homeViewModel::updateGroupDirection,
                                    onGroupDescriptionChange = homeViewModel::updateGroupDescription,
                                    onGroupNotesChange = homeViewModel::updateGroupNotes,
                                    onReviewGroupExpense = homeViewModel::reviewGroupExpense,
                                    onEditGroupExpenseReview = homeViewModel::editGroupExpenseReview,
                                    onConfirmGroupExpense = homeViewModel::confirmGroupExpense,
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
                                    onStrongAlarmChange = homeViewModel::updateStrongAlarm,
                                    onRequestExactAlarmAccess = ::requestExactAlarmAccess,
                                    notificationPermissionGranted = notificationsAvailable,
                                    exactAlarmAccessGranted = exactAlarmsAvailable,
                                    onSave = homeViewModel::createDebt,
                                    onSuccessShown = homeViewModel::clearSuccessMessage,
                                )
                            }
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
                            val openAccountDocuments = LocalOpenAccountDocuments.current
                            LaunchedEffect(
                                requestedDebtId,
                                requestedPaymentIntent,
                                state.account?.ledger?.header?.id?.value,
                            ) {
                                if (requestedDebtId != route.debtId) return@LaunchedEffect
                                val account = state.account ?: return@LaunchedEffect
                                if (account.ledger.balance.isZero) {
                                    onRequestedDebtHandled()
                                    return@LaunchedEffect
                                }
                                when (requestedPaymentIntent) {
                                    ReminderNotificationActions.PAYMENT_INTENT_PARTIAL -> {
                                        detailsViewModel.openPaymentDialog()
                                        onRequestedDebtHandled()
                                    }
                                    ReminderNotificationActions.PAYMENT_INTENT_FULL -> {
                                        detailsViewModel.openPaymentDialog()
                                        detailsViewModel.updatePaymentAmount(
                                            paymentInputValue(account.ledger.balance),
                                        )
                                        onRequestedDebtHandled()
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
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
                                    onDueScheduleStrongAlarmChange = detailsViewModel::updateDueScheduleStrongAlarm,
                                    onDueScheduleStrongAlarmTimeChange = detailsViewModel::updateDueScheduleStrongAlarmTime,
                                    onRequestExactAlarmAccess = ::requestExactAlarmAccess,
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
                                OutlinedButton(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, bottom = 24.dp)
                                        .testTag("account-export-pdf"),
                                    onClick = { openAccountDocuments(debtId) },
                                ) {
                                    Text("تصدير PDF")
                                }
                            }
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
    onDismissCreateTypePicker: () -> Unit,
    onCreateIndividual: () -> Unit,
    onCreateGroupExpense: () -> Unit,
    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,
    onDismissCreate: () -> Unit,
    onDismissGroupExpense: () -> Unit,
    onToggleGroupParticipant: (PersonId) -> Unit,
    onGroupParticipantAmountChange: (PersonId, String) -> Unit,
    onGroupCurrencyChange: (CurrencyCode) -> Unit,
    onGroupDirectionChange: (DebtDirection) -> Unit,
    onGroupDescriptionChange: (String) -> Unit,
    onGroupNotesChange: (String) -> Unit,
    onReviewGroupExpense: () -> Unit,
    onEditGroupExpenseReview: () -> Unit,
    onConfirmGroupExpense: () -> Unit,
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
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
    onSuccessShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val openNaturalEntry = LocalOpenNaturalEntry.current
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
            Column {
                Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onCreateIndividual,
                            modifier = Modifier.weight(1f).testTag("home-add-entry"),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("＋ إضافة حساب", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { openNaturalEntry?.invoke() },
                            enabled = openNaturalEntry != null,
                            modifier = Modifier.weight(1f).testTag("home-natural-entry"),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("إدخال ذكي") }
                    }
                }
                WaslTopLevelNavigation(
                    selected = WaslTopLevelDestination.HOME,
                    onOpenHome = onOpenHome,
                    onOpenToday = onOpenToday,
                    onOpenSearch = onOpenSearch,
                )
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("home-header") { HomeHeroCard(accountCount = state.accounts.size) }
            if (state.isLoading) {
                item("home-loading") {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item("home-overview-heading") {
                    HomeSectionHeader(
                        title = "إجمالي الحسابات",
                        subtitle = "كل عملة مستقلة؛ لا يتم خلط الأرصدة.",
                        tagPrefix = "home-overview",
                    )
                }
                item("home-currency-overview") { HomeCurrencyOverview(state) }
                state.loadError?.let { error -> item("home-error") { StatusCard(error, true) } }
                if (state.accounts.isEmpty() && state.loadError == null) {
                    item("home-empty") { StatusCard("أضف أول حساب لتبدأ متابعة الحقوق والالتزامات.", false) }
                } else {
                    item("home-accounts-heading") {
                        HomeSectionHeader(
                            title = "الحسابات",
                            subtitle = if (state.accounts.size == 1) "حساب واحد" else "${state.accounts.size} حسابات",
                            count = state.accounts.size,
                            tagPrefix = "home-accounts",
                        )
                    }
                    items(state.accounts, key = { it.ledger.header.id.value }) { account ->
                        AccountCard(account) { onOpenAccount(account.ledger.header.id) }
                    }
                }
                item("home-group-expense") {
                    TextButton(onClick = onCreateGroupExpense, modifier = Modifier.fillMaxWidth()) { Text("عملية جماعية") }
                }
            }
        }
    }

    if (state.isGroupExpenseDialogOpen) {
        GroupExpenseDialog(
            form = state.groupExpenseForm,
            step = state.groupExpenseStep,
            preview = state.groupExpensePreview,
            error = state.groupExpenseError,
            isSaving = state.isSaving,
            peopleQuery = state.peopleQuery,
            selectablePeople = state.selectablePeople,
            isPeopleLoading = state.isPeopleLoading,
            peopleLoadError = state.peopleLoadError,
            hasMorePeople = state.hasMorePeople,
            onDismiss = onDismissGroupExpense,
            onPeopleQueryChange = onPeopleQueryChange,
            onToggleParticipant = onToggleGroupParticipant,
            onParticipantAmountChange = onGroupParticipantAmountChange,
            onCurrencyChange = onGroupCurrencyChange,
            onDirectionChange = onGroupDirectionChange,
            onDescriptionChange = onGroupDescriptionChange,
            onNotesChange = onGroupNotesChange,
            onRetryPeople = onRetryPeople,
            onReview = onReviewGroupExpense,
            onEditReview = onEditGroupExpenseReview,
            onConfirm = onConfirmGroupExpense,
        )
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
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onSave = onSave,
        )
    }
}

@Composable
private fun HomeCurrencyOverview(state: HomeUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                supportedCurrencies.forEach { currency ->
                    CurrencyBalanceTile(
                        currency,
                        state.balanceSummary.receivableByCurrency[currency] ?: Money.zero(currency),
                        state.balanceSummary.payableByCurrency[currency] ?: Money.zero(currency),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                supportedCurrencies.forEach { currency ->
                    CurrencyBalanceTile(
                        currency,
                        state.balanceSummary.receivableByCurrency[currency] ?: Money.zero(currency),
                        state.balanceSummary.payableByCurrency[currency] ?: Money.zero(currency),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyBalanceTile(currency: CurrencyCode, receivable: Money, payable: Money, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(currency.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(formatMoney(receivable), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text("لي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!payable.isZero) {
                Text("عليّ ${formatMoney(payable)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            }
        }
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
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var advancedOpen by remember(form.dueDate, form.remindOnDueDate, form.strongAlarmEnabled) {
        mutableStateOf(form.dueDate != null || form.remindOnDueDate || form.strongAlarmEnabled)
    }
    val onStrongAlarmTimeChange = LocalStrongAlarmTimeChange.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("إضافة حساب جديد", fontWeight = FontWeight.Bold)
                Text("البيانات الأساسية أولًا، وخيارات الاستحقاق عند الحاجة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.direction == DebtDirection.RECEIVABLE, { onDirectionChange(DebtDirection.RECEIVABLE) },
                        { Text("لي عنده") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                    FilterChip(form.direction == DebtDirection.PAYABLE, { onDirectionChange(DebtDirection.PAYABLE) },
                        { Text("عليّ له") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.personMode == DebtPersonMode.NEW, { onPersonModeChange(DebtPersonMode.NEW) },
                        { Text("شخص جديد") }, enabled = !isSaving,
                        modifier = Modifier.weight(1f).testTag("create-person-mode-new"))
                    FilterChip(form.personMode == DebtPersonMode.EXISTING, { onPersonModeChange(DebtPersonMode.EXISTING) },
                        { Text("شخص محفوظ") }, enabled = !isSaving,
                        modifier = Modifier.weight(1f).testTag("create-person-mode-existing"))
                }

                if (form.personMode == DebtPersonMode.NEW) {
                    OutlinedTextField(form.personName, onPersonNameChange,
                        Modifier.fillMaxWidth().testTag("create-person-name"),
                        label = { Text("الاسم") }, placeholder = { Text("مثال: أحمد") },
                        singleLine = true, enabled = !isSaving)
                } else {
                    form.selectedPerson?.let { selected ->
                        Surface(Modifier.fillMaxWidth().testTag("selected-existing-person"),
                            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(selected.displayName, Modifier.padding(11.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(peopleQuery, onPeopleQueryChange,
                        Modifier.fillMaxWidth().testTag("existing-person-query"),
                        label = { Text("ابحث عن شخص") }, singleLine = true, enabled = !isSaving)
                    when {
                        isPeopleLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        }
                        peopleLoadError != null -> Column {
                            Text(peopleLoadError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryPeople, modifier = Modifier.testTag("retry-existing-people")) { Text("إعادة المحاولة") }
                        }
                        selectablePeople.isEmpty() -> Text(
                            if (peopleQuery.isBlank()) "لا يوجد أشخاص محفوظون." else "لا توجد نتيجة مطابقة.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            selectablePeople.forEach { person ->
                                OutlinedButton(onClick = { onSelectPerson(person.id) }, enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth().testTag("existing-person-${person.id.value}")) {
                                    Text(person.displayName)
                                }
                            }
                            if (hasMorePeople) Text("اكتب اسمًا أدق لعرض نتائج أقل.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                OutlinedTextField(form.amount, onAmountChange,
                    Modifier.fillMaxWidth().testTag("create-debt-amount"),
                    label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = !isSaving)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    supportedCurrencies.forEach { currency ->
                        FilterChip(form.currency == currency, { onCurrencyChange(currency) }, { Text(currency.value) },
                            enabled = !isSaving, modifier = Modifier.weight(1f))
                    }
                }

                OutlinedTextField(form.description, onDescriptionChange, Modifier.fillMaxWidth(),
                    label = { Text("ملاحظات — اختياري") }, minLines = 1, maxLines = 2, enabled = !isSaving)

                OutlinedButton(onClick = { advancedOpen = !advancedOpen },
                    modifier = Modifier.fillMaxWidth().testTag("create-advanced-options"), enabled = !isSaving) {
                    Text(if (advancedOpen) "إخفاء خيارات الاستحقاق" else "خيارات إضافية · الاستحقاق والتذكير")
                }

                if (advancedOpen) {
                    OutlinedButton(onClick = { showDatePicker = true }, enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("create-due-date")) {
                        Text(form.dueDate?.format(dueDateFormatter) ?: "تاريخ الاستحقاق — اختياري")
                    }
                    if (form.dueDate != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("تذكير بالاستحقاق", fontWeight = FontWeight.SemiBold)
                                Text("متابعة تلقائية حتى السداد", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(form.remindOnDueDate, onReminderChange, enabled = !isSaving,
                                modifier = Modifier.testTag("create-due-reminder"))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("منبه دقيق", fontWeight = FontWeight.SemiBold)
                                Text("اختياري في يوم الاستحقاق", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(form.strongAlarmEnabled, onStrongAlarmChange, enabled = !isSaving,
                                modifier = Modifier.testTag("create-strong-alarm"))
                        }
                        if (form.strongAlarmEnabled) {
                            StrongAlarmTimeSelector(form.strongAlarmTime, !isSaving, onStrongAlarmTimeChange,
                                Modifier.fillMaxWidth(), "create-strong-alarm-time")
                        }
                        if (form.remindOnDueDate && !notificationPermissionGranted) {
                            Text("اسمح بإشعارات وَصل حتى تظهر التذكيرات.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                            TextButton(onClick = onRequestExactAlarmAccess,
                                modifier = Modifier.testTag("create-request-exact-alarm-access")) { Text("السماح بالمنبه الدقيق") }
                        }
                    }
                }

                error?.let {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(it, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.testTag("create-debt-save")) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(7.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else "حفظ الحساب")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") } },
    )

    if (showDatePicker) {
        val initialSelection = form.dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialSelection)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { selected ->
                    onDueDateChange(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate())
                }
                showDatePicker = false
            }, enabled = pickerState.selectedDateMillis != null) { Text("اختيار") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
internal fun AccountCard(account: AccountOverview, onClick: () -> Unit) {
    val header = account.ledger.header
    val receivable = header.direction == DebtDirection.RECEIVABLE
    val tagPrefix = "account-${header.id.value}"
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tagPrefix),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-header-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AccountIdentityRow(account, receivable, Modifier.fillMaxWidth())
                        AccountDirectionBadge(receivable)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-header-inline"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccountIdentityRow(account, receivable, Modifier.weight(1f))
                        AccountDirectionBadge(receivable)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-balance-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AccountRemainingBalance(account)
                        AccountStateBadge(account.ledger.state)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-balance-inline"),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        AccountRemainingBalance(account)
                        AccountStateBadge(account.ledger.state)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val dueText = header.dueDate?.let { "الاستحقاق ${it.format(dueDateFormatter)}" } ?: "بدون تاريخ استحقاق"
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-original-stacked"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الأصل ${formatMoney(header.originalAmount)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-original-inline"), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الأصل ${formatMoney(header.originalAmount)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountIdentityRow(
    account: AccountOverview,
    receivable: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
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
            account.ledger.header.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun AccountDirectionBadge(receivable: Boolean) {
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

@Composable
private fun AccountRemainingBalance(account: AccountOverview) {
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
}

@Composable
private fun AccountStateBadge(state: DebtState) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = when (state) {
            DebtState.SETTLED -> MaterialTheme.colorScheme.primaryContainer
            DebtState.PARTIALLY_PAID -> MaterialTheme.colorScheme.tertiaryContainer
            DebtState.OPEN -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = when (state) {
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

@Composable
private fun AccountOriginalAmount(money: Money) {
    Text(
        text = "الأصل",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = formatMoney(money),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
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
internal fun SummaryCard(
    title: String,
    subtitle: String,
    values: List<String>,
    receivable: Boolean,
) {
    val tagPrefix = if (receivable) "summary-receivable" else "summary-payable"
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
                val currency = supportedCurrencies[index]
                val contentColor = if (receivable) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (shouldStackDenseRows(maxWidth)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("$tagPrefix-${currency.value}-stacked"),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = currency.value,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                            )
                            Text(
                                text = value,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("$tagPrefix-${currency.value}-inline"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currency.value,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                            )
                            Text(
                                text = value,
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                        }
                    }
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

internal fun paymentInputValue(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    return BigDecimal.valueOf(money.minorUnits, fractionDigits).toPlainString()
}

internal fun formatMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return ltrIsolate("${formatter.format(major)} ${money.currency.value}")
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
                onDismissCreateTypePicker = {},
                onCreateIndividual = {},
                onCreateGroupExpense = {},
                onOpenAccount = {},
                onDismissCreate = {},
                onDismissGroupExpense = {},
                onToggleGroupParticipant = {},
                onGroupParticipantAmountChange = { _, _ -> },
                onGroupCurrencyChange = {},
                onGroupDirectionChange = {},
                onGroupDescriptionChange = {},
                onGroupNotesChange = {},
                onReviewGroupExpense = {},
                onEditGroupExpenseReview = {},
                onConfirmGroupExpense = {},
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
                onRequestExactAlarmAccess = {},
                notificationPermissionGranted = true,
                exactAlarmAccessGranted = true,
                onSave = {},
                onSuccessShown = {},
            )
        }
    }
}
