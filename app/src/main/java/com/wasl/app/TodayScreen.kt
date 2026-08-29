package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
                TodayNotice.REMINDER_RECOVERY_REQUESTED -> "تمت إعادة محاولة تفعيل التذكيرات."
                TodayNotice.REMINDER_RECOVERY_FAILED -> "تعذر بدء إعادة المحاولة الآن."
            },
        )
        onNoticeShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("today-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("اليوم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${state.today.format(todayDateFormatter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "متابعة الاستحقاقات",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item("today-summary") { TodaySummaryCard(state) }

            when {
                state.isLoading && state.totalAttentionItems == 0 -> item("today-loading") {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.loadError != null -> item("today-error") { TodayErrorCard(state.loadError, onRetryLoad) }
                state.totalAttentionItems == 0 -> item("today-empty") { TodayEmptyCard() }
                else -> {
                    if (state.overdueItems.isNotEmpty()) {
                        item("overdue-heading") { TodaySectionHeading("متأخرة", "حسابات تجاوزت موعد الاستحقاق", state.overdueItems.size, true) }
                        items(state.overdueItems, key = { "overdue:${it.account.ledger.header.id.value}" }) { item ->
                            TodayAccountCard(item, notificationsAvailable, state.isRequestingReminderRecovery,
                                { onOpenAccount(item.account.ledger.header.id) }, onResolveNotificationPermission, onRetryReminders)
                        }
                    }
                    if (state.overdueInstallmentItems.isNotEmpty()) {
                        item("overdue-installments-heading") { TodaySectionHeading("أقساط متأخرة", "أقساط لم يكتمل سدادها في موعدها", state.overdueInstallmentItems.size, true) }
                        items(state.overdueInstallmentItems, key = { "overdue-installment:${it.installment.id}" }) { item ->
                            TodayInstallmentCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                    if (state.overduePromiseItems.isNotEmpty()) {
                        item("overdue-promises-heading") { TodaySectionHeading("وعود متأخرة", "وعود سداد تحتاج حسمًا", state.overduePromiseItems.size, true) }
                        items(state.overduePromiseItems, key = { "overdue-promise:${it.promise.id}" }) { item ->
                            TodayPromiseCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                    if (state.overdueClaimItems.isNotEmpty()) {
                        item("overdue-claims-heading") { TodaySectionHeading("مطالبات متأخرة", "مطالبات تحتاج متابعة", state.overdueClaimItems.size, true) }
                        items(state.overdueClaimItems, key = { "overdue-claim:${it.claim.id}" }) { item ->
                            TodayClaimCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                    if (state.dueTodayItems.isNotEmpty()) {
                        item("due-today-heading") { TodaySectionHeading("مستحقة اليوم", "موعد الدين اليوم", state.dueTodayItems.size, false) }
                        items(state.dueTodayItems, key = { "today:${it.account.ledger.header.id.value}" }) { item ->
                            TodayAccountCard(item, notificationsAvailable, state.isRequestingReminderRecovery,
                                { onOpenAccount(item.account.ledger.header.id) }, onResolveNotificationPermission, onRetryReminders)
                        }
                    }
                    if (state.dueTodayInstallmentItems.isNotEmpty()) {
                        item("due-today-installments-heading") { TodaySectionHeading("أقساط اليوم", "أقساط موعدها اليوم", state.dueTodayInstallmentItems.size, false) }
                        items(state.dueTodayInstallmentItems, key = { "today-installment:${it.installment.id}" }) { item ->
                            TodayInstallmentCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                    if (state.dueTodayPromiseItems.isNotEmpty()) {
                        item("due-today-promises-heading") { TodaySectionHeading("وعود اليوم", "وعود سداد موعدها اليوم", state.dueTodayPromiseItems.size, false) }
                        items(state.dueTodayPromiseItems, key = { "today-promise:${it.promise.id}" }) { item ->
                            TodayPromiseCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                    if (state.dueTodayClaimItems.isNotEmpty()) {
                        item("due-today-claims-heading") { TodaySectionHeading("مطالبات اليوم", "موعد المتابعة اليوم", state.dueTodayClaimItems.size, false) }
                        items(state.dueTodayClaimItems, key = { "today-claim:${it.claim.id}" }) { item ->
                            TodayClaimCard(item) { onOpenAccount(item.account.ledger.header.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(state: TodayUiState) {
    val overdueCount = state.overdueItems.size + state.overduePromiseItems.size + state.overdueInstallmentItems.size + state.overdueClaimItems.size
    val todayCount = state.dueTodayItems.size + state.dueTodayPromiseItems.size + state.dueTodayInstallmentItems.size + state.dueTodayClaimItems.size
    Card(
        modifier = Modifier.fillMaxWidth().testTag("today-summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ملخص اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(
                        Modifier.fillMaxWidth().testTag("today-summary-metrics-stacked"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TodayMetric(Modifier.fillMaxWidth(), overdueCount, "متأخرة", overdueCount > 0)
                        TodayMetric(Modifier.fillMaxWidth(), todayCount, "مستحقة اليوم", false)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().testTag("today-summary-metrics-inline"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TodayMetric(Modifier.weight(1f), overdueCount, "متأخرة", overdueCount > 0)
                        TodayMetric(Modifier.weight(1f), todayCount, "مستحقة اليوم", false)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayMetric(modifier: Modifier, value: Int, label: String, emphasized: Boolean) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayEmptyCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("لا توجد استحقاقات أو متابعات اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("أنت على موعدك.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodaySectionHeading(title: String, subtitle: String, count: Int, overdue: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                Modifier.fillMaxWidth().testTag("today-section-heading-stacked-$title"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TodaySectionHeadingText(title, subtitle)
                TodayCountPill(count, overdue)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().testTag("today-section-heading-inline-$title"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodaySectionHeadingText(title, subtitle, Modifier.weight(1f))
                TodayCountPill(count, overdue)
            }
        }
    }
}

@Composable
private fun TodaySectionHeadingText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayCountPill(count: Int, overdue: Boolean) {
    Surface(shape = CircleShape, color = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
        Text(
            count.toString(),
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
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
        modifier = Modifier.fillMaxWidth().testTag("today-account-$debtId"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TodayAccountHeader(account.person.displayName, account.ledger.header.direction, account.ledger.header.description)
            TodayAmountStatusRow(
                label = "المتبقي",
                amount = formatMoney(account.ledger.balance),
                statusText = if (overdue) overdueLabel(item.daysOverdue) else "مستحق اليوم",
                overdue = overdue,
                testTag = "today-amount-status-$debtId",
            )
            Text(
                reminderStatusText(reminder?.status),
                style = MaterialTheme.typography.bodySmall,
                color = if (reminder?.status == ReminderStatus.BLOCKED_PERMISSION || reminder?.status == ReminderStatus.FAILED)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TodayReminderActions(debtId, reminder?.status, notificationsAvailable, isReminderActionRunning,
                onOpenAccount, onResolveNotificationPermission, onRetryReminders)
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(Modifier.fillMaxWidth().testTag("today-actions-stacked-$debtId"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onOpenAccount, Modifier.fillMaxWidth().testTag("today-open-$debtId")) { Text("فتح الحساب") }
                TodayReminderSecondaryAction(Modifier.fillMaxWidth(), debtId, reminderStatus, notificationsAvailable, isReminderActionRunning,
                    onResolveNotificationPermission, onRetryReminders)
            }
        } else {
            Row(Modifier.fillMaxWidth().testTag("today-actions-inline-$debtId"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onOpenAccount, Modifier.weight(1f).testTag("today-open-$debtId")) { Text("فتح الحساب") }
                TodayReminderSecondaryAction(Modifier.weight(1f), debtId, reminderStatus, notificationsAvailable, isReminderActionRunning,
                    onResolveNotificationPermission, onRetryReminders)
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
            onClick = if (notificationsAvailable) onRetryReminders else onResolveNotificationPermission,
            enabled = !isReminderActionRunning,
            modifier = modifier.testTag("today-enable-notifications-$debtId"),
        ) { Text(if (notificationsAvailable) "إعادة التفعيل" else "السماح بالإشعارات") }
        ReminderStatus.FAILED -> OutlinedButton(
            onClick = onRetryReminders,
            enabled = !isReminderActionRunning,
            modifier = modifier.testTag("today-retry-reminder-$debtId"),
        ) { Text("إعادة المحاولة") }
        else -> Unit
    }
}

@Composable
private fun TodayInstallmentCard(item: TodayInstallmentItem, onOpenAccount: () -> Unit) {
    val installment = item.installment
    Card(
        Modifier.fillMaxWidth().testTag("today-installment-${installment.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TodayAccountHeader(item.account.person.displayName, item.account.ledger.header.direction, "القسط ${installment.sequenceNumber}")
            TodayAmountStatusRow(
                if (installment.isPartiallyPaid) "المتبقي في القسط" else "قيمة القسط",
                formatMoney(installment.remainingAmount),
                if (item.isOverdue) overdueLabel(item.daysOverdue) else "قسط اليوم",
                item.isOverdue,
                "today-amount-status-installment-${installment.id}",
            )
            Text("الموعد ${installment.dueDate.format(todayDateFormatter)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onOpenAccount, Modifier.fillMaxWidth().testTag("today-open-installment-${installment.id}")) { Text("فتح الحساب") }
        }
    }
}

@Composable
private fun TodayPromiseCard(item: TodayPromiseItem, onOpenAccount: () -> Unit) {
    val promise = item.promise
    Card(
        Modifier.fillMaxWidth().testTag("today-promise-${promise.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TodayAccountHeader(item.account.person.displayName, item.account.ledger.header.direction, "وعد سداد · ${promise.promisedDate.format(todayDateFormatter)}")
            TodayAmountStatusRow("المتبقي", formatMoney(item.account.ledger.balance),
                if (item.isOverdue) overdueLabel(item.daysOverdue) else "وعد اليوم", item.isOverdue,
                "today-amount-status-promise-${promise.id}")
            promise.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onOpenAccount, Modifier.fillMaxWidth().testTag("today-open-promise-${promise.id}")) { Text("فتح الحساب") }
        }
    }
}

@Composable
private fun TodayClaimCard(item: TodayClaimItem, onOpenAccount: () -> Unit) {
    val claim = item.claim
    val followUpDate = requireNotNull(claim.followUpDate)
    Card(
        Modifier.fillMaxWidth().testTag("today-claim-${claim.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TodayAccountHeader(item.account.person.displayName, item.account.ledger.header.direction, "مطالبة · ${followUpDate.format(todayDateFormatter)}")
            TodayAmountStatusRow("المتبقي", formatMoney(item.account.ledger.balance),
                if (item.isOverdue) overdueLabel(item.daysOverdue) else "متابعة اليوم", item.isOverdue,
                "today-amount-status-claim-${claim.id}")
            claim.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onOpenAccount, Modifier.fillMaxWidth().testTag("today-open-claim-${claim.id}")) { Text("فتح الحساب") }
        }
    }
}

@Composable
private fun TodayAccountHeader(personName: String, direction: DebtDirection, subtitle: String?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(Modifier.fillMaxWidth().testTag("today-account-header-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TodayPersonAvatar(personName)
                    TodayAccountHeaderText(personName, subtitle, Modifier.weight(1f))
                }
                TodayDirectionPill(direction)
            }
        } else {
            Row(Modifier.fillMaxWidth().testTag("today-account-header-inline"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TodayPersonAvatar(personName)
                TodayAccountHeaderText(personName, subtitle, Modifier.weight(1f))
                TodayDirectionPill(direction)
            }
        }
    }
}

@Composable
private fun TodayAccountHeaderText(personName: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TodayAmountStatusRow(label: String, amount: String, statusText: String, overdue: Boolean, testTag: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(Modifier.fillMaxWidth().testTag("$testTag-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                TodayAmountText(label, amount)
                TodayStatusPill(statusText, overdue)
            }
        } else {
            Row(Modifier.fillMaxWidth().testTag("$testTag-inline"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                TodayAmountText(label, amount, Modifier.weight(1f))
                TodayStatusPill(statusText, overdue)
            }
        }
    }
}

@Composable
private fun TodayAmountText(label: String, amount: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Start)
    }
}

@Composable
private fun TodayStatusPill(text: String, overdue: Boolean) {
    Surface(shape = MaterialTheme.shapes.medium, color = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun TodayPersonAvatar(name: String) {
    Surface(Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.toString() ?: "و", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TodayDirectionPill(direction: DebtDirection) {
    val receivable = direction == DebtDirection.RECEIVABLE
    Surface(shape = MaterialTheme.shapes.medium, color = if (receivable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(if (receivable) "لي عنده" else "عليّ له", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium,
            color = if (receivable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun TodayErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onRetry, Modifier.testTag("today-retry-load")) { Text("إعادة المحاولة") }
        }
    }
}

private fun reminderStatusText(status: ReminderStatus?): String = when (status) {
    ReminderStatus.SCHEDULED -> "التذكير مجدول."
    ReminderStatus.DELIVERED -> "تم عرض التذكير."
    ReminderStatus.BLOCKED_PERMISSION -> "التذكير متوقف حتى تسمح بإشعارات وَصل."
    ReminderStatus.FAILED -> "تعذرت جدولة التذكير، ويمكن إعادة المحاولة."
    ReminderStatus.CANCELLED -> "التذكير ملغى."
    null -> "لا يوجد تذكير لهذا الحساب."
}

private fun overdueLabel(days: Long): String = when (days) {
    1L -> "متأخر يومًا واحدًا"
    2L -> "متأخر يومين"
    in 3L..10L -> "متأخر $days أيام"
    else -> "متأخر $days يومًا"
}
