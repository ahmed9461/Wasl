package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtDirection
import com.wasl.domain.DueState
import java.time.format.DateTimeFormatter
import java.util.Locale

private val todayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)

@Composable
internal fun TodayScreen(
    state: TodayUiState,
    notificationsAvailable: Boolean,
    onOpenHome: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,
    onRefreshDate: () -> Unit,
    onRetryLoad: () -> Unit,
    onResolveNotificationPermission: () -> Unit,
    onRetryReminders: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnRefreshDate = rememberUpdatedState(onRefreshDate)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnRefreshDate.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        currentOnRefreshDate.value()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (notice) {
                TodayNotice.REMINDER_RECOVERY_REQUESTED ->
                    "تم طلب إعادة تفعيل التذكيرات المحفوظة."

                TodayNotice.REMINDER_RECOVERY_FAILED ->
                    "تعذر بدء إعادة المحاولة الآن. حاول مرة أخرى."
            },
        )
        onNoticeShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WaslTopLevelNavigation(
                selected = WaslTopLevelDestination.TODAY,
                onOpenHome = onOpenHome,
                onOpenToday = {},
                onOpenSearch = onOpenSearch,
            )
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
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("today-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "متابعة الاستحقاقات والوعود والأقساط والمطالبات",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = "اليوم",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "حسب تاريخ الجهاز: \u2066${state.today.format(todayDateFormatter)}\u2069",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item("today-summary") {
                TodaySummaryCard(state)
            }

            when {
                state.isLoading && state.totalAttentionItems == 0 -> item("today-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.loadError != null -> item("today-error") {
                    TodayErrorCard(message = state.loadError, onRetry = onRetryLoad)
                }

                state.totalAttentionItems == 0 -> item("today-empty") {
                    TodayEmptyCard()
                }

                else -> {
                    if (state.overdueItems.isNotEmpty()) {
                        item("overdue-heading") {
                            TodaySectionHeading(
                                title = "متأخرة",
                                subtitle = "حسابات تجاوزت تاريخ الاستحقاق",
                                count = state.overdueItems.size,
                                overdue = true,
                            )
                        }
                        items(
                            items = state.overdueItems,
                            key = { "overdue:${it.account.ledger.header.id.value}" },
                        ) { item ->
                            TodayAccountCard(
                                item = item,
                                notificationsAvailable = notificationsAvailable,
                                isReminderActionRunning = state.isRequestingReminderRecovery,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                                onResolveNotificationPermission = onResolveNotificationPermission,
                                onRetryReminders = onRetryReminders,
                            )
                        }
                    }

                    if (state.overdueInstallmentItems.isNotEmpty()) {
                        item("overdue-installments-heading") {
                            TodaySectionHeading(
                                title = "أقساط متأخرة",
                                subtitle = "أقساط لم يكتمل سدادها بعد موعدها",
                                count = state.overdueInstallmentItems.size,
                                overdue = true,
                            )
                        }
                        items(
                            items = state.overdueInstallmentItems,
                            key = { "overdue-installment:${it.installment.id}" },
                        ) { item ->
                            TodayInstallmentCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.overduePromiseItems.isNotEmpty()) {
                        item("overdue-promises-heading") {
                            TodaySectionHeading(
                                title = "وعود متأخرة",
                                subtitle = "وعود مر موعدها وما زالت بانتظار الحسم",
                                count = state.overduePromiseItems.size,
                                overdue = true,
                            )
                        }
                        items(
                            items = state.overduePromiseItems,
                            key = { "overdue-promise:${it.promise.id}" },
                        ) { item ->
                            TodayPromiseCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.overdueClaimItems.isNotEmpty()) {
                        item("overdue-claims-heading") {
                            TodaySectionHeading(
                                title = "مطالبات متأخرة",
                                subtitle = "مطالبات «طالبني» حان موعد متابعتها ولم تُحسم",
                                count = state.overdueClaimItems.size,
                                overdue = true,
                            )
                        }
                        items(
                            items = state.overdueClaimItems,
                            key = { "overdue-claim:${it.claim.id}" },
                        ) { item ->
                            TodayClaimCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.dueTodayItems.isNotEmpty()) {
                        item("due-today-heading") {
                            TodaySectionHeading(
                                title = "مستحقة اليوم",
                                subtitle = "موعد الدين اليوم",
                                count = state.dueTodayItems.size,
                                overdue = false,
                            )
                        }
                        items(
                            items = state.dueTodayItems,
                            key = { "today:${it.account.ledger.header.id.value}" },
                        ) { item ->
                            TodayAccountCard(
                                item = item,
                                notificationsAvailable = notificationsAvailable,
                                isReminderActionRunning = state.isRequestingReminderRecovery,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                                onResolveNotificationPermission = onResolveNotificationPermission,
                                onRetryReminders = onRetryReminders,
                            )
                        }
                    }

                    if (state.dueTodayInstallmentItems.isNotEmpty()) {
                        item("due-today-installments-heading") {
                            TodaySectionHeading(
                                title = "أقساط اليوم",
                                subtitle = "أقساط موعدها اليوم وتحتاج متابعة",
                                count = state.dueTodayInstallmentItems.size,
                                overdue = false,
                            )
                        }
                        items(
                            items = state.dueTodayInstallmentItems,
                            key = { "today-installment:${it.installment.id}" },
                        ) { item ->
                            TodayInstallmentCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.dueTodayPromiseItems.isNotEmpty()) {
                        item("due-today-promises-heading") {
                            TodaySectionHeading(
                                title = "وعود اليوم",
                                subtitle = "وعود سداد موعدها اليوم",
                                count = state.dueTodayPromiseItems.size,
                                overdue = false,
                            )
                        }
                        items(
                            items = state.dueTodayPromiseItems,
                            key = { "today-promise:${it.promise.id}" },
                        ) { item ->
                            TodayPromiseCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.dueTodayClaimItems.isNotEmpty()) {
                        item("due-today-claims-heading") {
                            TodaySectionHeading(
                                title = "مطالبات اليوم",
                                subtitle = "مطالبات «طالبني» موعد متابعتها اليوم",
                                count = state.dueTodayClaimItems.size,
                                overdue = false,
                            )
                        }
                        items(
                            items = state.dueTodayClaimItems,
                            key = { "today-claim:${it.claim.id}" },
                        ) { item ->
                            TodayClaimCard(
                                item = item,
                                onOpenAccount = { onOpenAccount(item.account.ledger.header.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(state: TodayUiState) {
    val overdueCount = state.overdueItems.size +
        state.overduePromiseItems.size +
        state.overdueInstallmentItems.size +
        state.overdueClaimItems.size
    val todayCount = state.dueTodayItems.size +
        state.dueTodayPromiseItems.size +
        state.dueTodayInstallmentItems.size +
        state.dueTodayClaimItems.size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-summary"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "اليوم لديك ${state.totalAttentionItems} أمور",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "ركّز على المتأخر أولًا، ثم استحقاقات وأقساط ووعود ومطالبات اليوم.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today-summary-metrics-stacked"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TodayMetric(
                            modifier = Modifier.fillMaxWidth(),
                            value = overdueCount,
                            label = "متأخرة",
                            emphasized = overdueCount > 0,
                        )
                        TodayMetric(
                            modifier = Modifier.fillMaxWidth(),
                            value = todayCount,
                            label = "مستحقة اليوم",
                            emphasized = false,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today-summary-metrics-inline"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TodayMetric(
                            modifier = Modifier.weight(1f),
                            value = overdueCount,
                            label = "متأخرة",
                            emphasized = overdueCount > 0,
                        )
                        TodayMetric(
                            modifier = Modifier.weight(1f),
                            value = todayCount,
                            label = "مستحقة اليوم",
                            emphasized = false,
                        )
                    }
                }
            }
            Text(
                text = "$overdueCount متأخرة · $todayCount مستحقة اليوم",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (state.installmentItems.isNotEmpty()) {
                Text(
                    text = "منها ${state.installmentItems.size} أقساط لم يكتمل سدادها.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (state.promiseItems.isNotEmpty()) {
                Text(
                    text = "ومنها ${state.promiseItems.size} وعود سداد تحتاج متابعة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (state.claimItems.isNotEmpty()) {
                Text(
                    text = "ومنها ${state.claimItems.size} مطالبات «طالبني» تحتاج متابعة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TodayMetric(
    modifier: Modifier,
    value: Int,
    label: String,
    emphasized: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TodayEmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "لا توجد أمور تحتاج انتباهك اليوم.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ستظهر هنا الاستحقاقات والأقساط ووعود السداد ومطالبات «طالبني» التي حان موعدها أو تأخرت.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodaySectionHeading(
    title: String,
    subtitle: String,
    count: Int,
    overdue: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-section-heading-stacked-$title"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodaySectionHeadingText(title = title, subtitle = subtitle)
                TodayCountPill(count = count, overdue = overdue)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-section-heading-inline-$title"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodaySectionHeadingText(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f),
                )
                TodayCountPill(count = count, overdue = overdue)
            }
        }
    }
}

@Composable
private fun TodaySectionHeadingText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
    }
}

@Composable
private fun TodayCountPill(count: Int, overdue: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (overdue) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (overdue) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

@Composable
private fun TodayAccountCard(
    item: TodayItem,
    notificationsAvailable: Boolean,
    isReminderActionRunning: Boolean,
    onOpenAccount: () -> Unit,
    onResolveNotificationPermission: () -> Unit,
    onRetryReminders: () -> Unit,
) {
    val account = item.account
    val reminder = account.dueReminder
    val overdue = item.dueState == DueState.OVERDUE
    val debtId = account.ledger.header.id.value
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-account-$debtId"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayAccountHeader(
                personName = account.person.displayName,
                direction = account.ledger.header.direction,
                subtitle = account.ledger.header.description,
            )
            TodayAmountStatusRow(
                label = "المتبقي",
                amount = formatMoney(account.ledger.balance),
                statusText = when (item.dueState) {
                    DueState.DUE_TODAY -> "مستحق اليوم"
                    DueState.OVERDUE -> overdueLabel(item.daysOverdue)
                    else -> error("Unsupported Today due state.")
                },
                overdue = overdue,
                testTag = "today-amount-status-$debtId",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = reminderStatusText(reminder?.status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (reminder?.status == ReminderStatus.BLOCKED_PERMISSION ||
                    reminder?.status == ReminderStatus.FAILED
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            TodayReminderActions(
                debtId = debtId,
                reminderStatus = reminder?.status,
                notificationsAvailable = notificationsAvailable,
                isReminderActionRunning = isReminderActionRunning,
                onOpenAccount = onOpenAccount,
                onResolveNotificationPermission = onResolveNotificationPermission,
                onRetryReminders = onRetryReminders,
            )
        }
    }
}

@Composable
private fun TodayReminderActions(
    debtId: String,
    reminderStatus: ReminderStatus?,
    notificationsAvailable: Boolean,
    isReminderActionRunning: Boolean,
    onOpenAccount: () -> Unit,
    onResolveNotificationPermission: () -> Unit,
    onRetryReminders: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-actions-stacked-$debtId"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today-open-$debtId"),
                ) { Text("فتح الحساب") }
                TodayReminderSecondaryAction(
                    modifier = Modifier.fillMaxWidth(),
                    debtId = debtId,
                    reminderStatus = reminderStatus,
                    notificationsAvailable = notificationsAvailable,
                    isReminderActionRunning = isReminderActionRunning,
                    onResolveNotificationPermission = onResolveNotificationPermission,
                    onRetryReminders = onRetryReminders,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-actions-inline-$debtId"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today-open-$debtId"),
                ) { Text("فتح الحساب") }
                TodayReminderSecondaryAction(
                    modifier = Modifier.weight(1f),
                    debtId = debtId,
                    reminderStatus = reminderStatus,
                    notificationsAvailable = notificationsAvailable,
                    isReminderActionRunning = isReminderActionRunning,
                    onResolveNotificationPermission = onResolveNotificationPermission,
                    onRetryReminders = onRetryReminders,
                )
            }
        }
    }
}

@Composable
private fun TodayReminderSecondaryAction(
    modifier: Modifier,
    debtId: String,
    reminderStatus: ReminderStatus?,
    notificationsAvailable: Boolean,
    isReminderActionRunning: Boolean,
    onResolveNotificationPermission: () -> Unit,
    onRetryReminders: () -> Unit,
) {
    when (reminderStatus) {
        ReminderStatus.BLOCKED_PERMISSION -> OutlinedButton(
            onClick = if (notificationsAvailable) {
                onRetryReminders
            } else {
                onResolveNotificationPermission
            },
            enabled = !isReminderActionRunning,
            modifier = modifier.testTag("today-enable-notifications-$debtId"),
        ) {
            Text(if (notificationsAvailable) "إعادة التفعيل" else "السماح بالإشعارات")
        }

        ReminderStatus.FAILED -> OutlinedButton(
            onClick = onRetryReminders,
            enabled = !isReminderActionRunning,
            modifier = modifier.testTag("today-retry-reminder-$debtId"),
        ) {
            Text("إعادة المحاولة")
        }

        ReminderStatus.SCHEDULED,
        ReminderStatus.DELIVERED,
        ReminderStatus.CANCELLED,
        null -> Unit
    }
}

@Composable
private fun TodayInstallmentCard(
    item: TodayInstallmentItem,
    onOpenAccount: () -> Unit,
) {
    val account = item.account
    val installment = item.installment
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-installment-${installment.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayAccountHeader(
                personName = account.person.displayName,
                direction = account.ledger.header.direction,
                subtitle = "القسط ${installment.sequenceNumber} · الخطة ${installment.revisionNumber}",
            )
            TodayAmountStatusRow(
                label = if (installment.isPartiallyPaid) "المتبقي في القسط" else "قيمة القسط",
                amount = formatMoney(installment.remainingAmount),
                statusText = if (item.isOverdue) {
                    overdueLabel(item.daysOverdue)
                } else {
                    "قسط اليوم"
                },
                overdue = item.isOverdue,
                testTag = "today-amount-status-installment-${installment.id}",
            )
            if (installment.isPartiallyPaid) {
                Text(
                    text = "دُفع ${formatMoney(installment.paidAmount)} من ${formatMoney(installment.scheduledAmount)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "الموعد: \u2066${installment.dueDate.format(todayDateFormatter)}\u2069",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "تقدم القسط مشتق من دفعات الحساب المالية نفسها؛ لا يوجد رصيد أقساط منفصل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-open-installment-${installment.id}"),
            ) {
                Text(if (installment.isPartiallyPaid) "فتح الحساب وإكمال السداد" else "فتح الحساب وتسجيل دفعة")
            }
        }
    }
}

@Composable
private fun TodayPromiseCard(
    item: TodayPromiseItem,
    onOpenAccount: () -> Unit,
) {
    val account = item.account
    val promise = item.promise
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-promise-${promise.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayAccountHeader(
                personName = account.person.displayName,
                direction = account.ledger.header.direction,
                subtitle = "وعد بالسداد · \u2066${promise.promisedDate.format(todayDateFormatter)}\u2069",
            )
            TodayAmountStatusRow(
                label = "المتبقي في الحساب",
                amount = formatMoney(account.ledger.balance),
                statusText = if (item.isOverdue) {
                    "الوعد ${overdueLabel(item.daysOverdue)}"
                } else {
                    "وعد اليوم"
                },
                overdue = item.isOverdue,
                testTag = "today-amount-status-promise-${promise.id}",
            )
            promise.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "هذا الوعد مستقل عن تاريخ استحقاق الدين. افتح الحساب لتسجيل الوفاء أو عدم التنفيذ أو الإلغاء.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-open-promise-${promise.id}"),
            ) {
                Text("فتح الحساب وحسم الوعد")
            }
        }
    }
}

@Composable
private fun TodayClaimCard(
    item: TodayClaimItem,
    onOpenAccount: () -> Unit,
) {
    val account = item.account
    val claim = item.claim
    val followUpDate = requireNotNull(claim.followUpDate)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-claim-${claim.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayAccountHeader(
                personName = account.person.displayName,
                direction = account.ledger.header.direction,
                subtitle = "طالبني · متابعة \u2066${followUpDate.format(todayDateFormatter)}\u2069",
            )
            TodayAmountStatusRow(
                label = "المتبقي في الحساب",
                amount = formatMoney(account.ledger.balance),
                statusText = if (item.isOverdue) {
                    "المطالبة ${overdueLabel(item.daysOverdue)}"
                } else {
                    "متابعة اليوم"
                },
                overdue = item.isOverdue,
                testTag = "today-amount-status-claim-${claim.id}",
            )
            claim.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "«طالبني» سجل متابعة غير مالي؛ لا يغيّر الرصيد ولا ينشئ دفعة. افتح الحساب لحسم المطالبة أو إلغائها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-open-claim-${claim.id}"),
            ) {
                Text("فتح الحساب وحسم المطالبة")
            }
        }
    }
}

@Composable
private fun TodayAccountHeader(
    personName: String,
    direction: DebtDirection,
    subtitle: String?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-account-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TodayPersonAvatar(personName)
                    TodayAccountHeaderText(
                        personName = personName,
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f),
                    )
                }
                TodayDirectionPill(direction)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-account-header-inline"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayPersonAvatar(personName)
                TodayAccountHeaderText(
                    personName = personName,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f),
                )
                TodayDirectionPill(direction)
            }
        }
    }
}

@Composable
private fun TodayAccountHeaderText(
    personName: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = personName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayAmountStatusRow(
    label: String,
    amount: String,
    statusText: String,
    overdue: Boolean,
    testTag: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTag-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodayAmountText(label = label, amount = amount)
                TodayStatusPill(text = statusText, overdue = overdue)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTag-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                TodayAmountText(
                    label = label,
                    amount = amount,
                    modifier = Modifier.weight(1f),
                )
                TodayStatusPill(text = statusText, overdue = overdue)
            }
        }
    }
}

@Composable
private fun TodayAmountText(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun TodayStatusPill(text: String, overdue: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (overdue) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (overdue) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
        )
    }
}

@Composable
private fun TodayPersonAvatar(name: String) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().firstOrNull()?.toString() ?: "و",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TodayDirectionPill(direction: DebtDirection) {
    val receivable = direction == DebtDirection.RECEIVABLE
    Surface(
        shape = RoundedCornerShape(999.dp),
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
private fun TodayErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("today-retry-load"),
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}

private fun reminderStatusText(status: ReminderStatus?): String = when (status) {
    ReminderStatus.SCHEDULED -> "التذكير مجدول."
    ReminderStatus.DELIVERED -> "تم إظهار تذكير هذا الحساب."
    ReminderStatus.BLOCKED_PERMISSION -> "التذكير متوقف حتى تسمح بإشعارات وَصل."
    ReminderStatus.FAILED -> "تعذرت جدولة التذكير، ويمكن إعادة المحاولة."
    ReminderStatus.CANCELLED -> "التذكير ملغى، والحساب ما زال ظاهرًا حسب موعده."
    null -> "لا يوجد تذكير لهذا الحساب."
}

private fun overdueLabel(days: Long): String = when (days) {
    1L -> "متأخر يومًا واحدًا"
    2L -> "متأخر يومين"
    in 3L..10L -> "متأخر $days أيام"
    else -> "متأخر $days يومًا"
}
