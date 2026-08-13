package com.wasl.app

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WaslTopLevelNavigation(
                selected = WaslTopLevelDestination.TODAY,
                onOpenHome = onOpenHome,
                onOpenToday = {},
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 24.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("today-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "اليوم",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "حسب تاريخ الجهاز: \u2066${state.today.format(todayDateFormatter)}\u2069",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item("today-summary") {
                TodaySummaryCard(state)
            }

            when {
                state.isLoading && state.items.isEmpty() -> item("today-loading") {
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
                    TodayErrorCard(
                        message = state.loadError,
                        onRetry = onRetryLoad,
                    )
                }

                state.items.isEmpty() -> item("today-empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "لا توجد مستحقات تحتاج انتباهك اليوم.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "ستظهر هنا الحسابات المستحقة اليوم والمتأخرة فقط.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                else -> {
                    if (state.overdueItems.isNotEmpty()) {
                        item("overdue-heading") {
                            TodaySectionHeading(
                                title = "متأخرة",
                                count = state.overdueItems.size,
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
                                onOpenAccount = {
                                    onOpenAccount(item.account.ledger.header.id)
                                },
                                onResolveNotificationPermission = onResolveNotificationPermission,
                                onRetryReminders = onRetryReminders,
                            )
                        }
                    }

                    if (state.dueTodayItems.isNotEmpty()) {
                        item("due-today-heading") {
                            TodaySectionHeading(
                                title = "مستحقة اليوم",
                                count = state.dueTodayItems.size,
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
                                onOpenAccount = {
                                    onOpenAccount(item.account.ledger.header.id)
                                },
                                onResolveNotificationPermission = onResolveNotificationPermission,
                                onRetryReminders = onRetryReminders,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-summary"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "اليوم لديك ${state.items.size} أمور",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${state.overdueItems.size} متأخرة · ${state.dueTodayItems.size} مستحقة اليوم",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TodaySectionHeading(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-account-${account.ledger.header.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = account.person.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when (account.ledger.header.direction) {
                        DebtDirection.RECEIVABLE -> "لي عنده"
                        DebtDirection.PAYABLE -> "عليّ له"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = formatMoney(account.ledger.balance),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Start,
            )
            Text(
                text = when (item.dueState) {
                    DueState.DUE_TODAY -> "مستحق اليوم"
                    DueState.OVERDUE -> overdueLabel(item.daysOverdue)
                    else -> error("Unsupported Today due state.")
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (item.dueState == DueState.OVERDUE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            account.ledger.header.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today-open-${account.ledger.header.id.value}"),
                ) {
                    Text("فتح الحساب")
                }
                when (reminder?.status) {
                    ReminderStatus.BLOCKED_PERMISSION -> OutlinedButton(
                        onClick = if (notificationsAvailable) {
                            onRetryReminders
                        } else {
                            onResolveNotificationPermission
                        },
                        enabled = !isReminderActionRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("today-enable-notifications-${account.ledger.header.id.value}"),
                    ) {
                        Text(
                            if (notificationsAvailable) {
                                "إعادة التفعيل"
                            } else {
                                "السماح بالإشعارات"
                            },
                        )
                    }

                    ReminderStatus.FAILED -> OutlinedButton(
                        onClick = onRetryReminders,
                        enabled = !isReminderActionRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("today-retry-reminder-${account.ledger.header.id.value}"),
                    ) {
                        Text("إعادة المحاولة")
                    }

                    ReminderStatus.SCHEDULED,
                    ReminderStatus.DELIVERED,
                    ReminderStatus.CANCELLED,
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun TodayErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message)
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
