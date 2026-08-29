from pathlib import Path
import re

p = Path('app/src/main/java/com/wasl/app/AccountDetailsScreen.kt')
s = p.read_text()

s = s.replace('import androidx.compose.material3.HorizontalDivider\n', 'import androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.LinearProgressIndicator\n')
s = s.replace('import androidx.compose.material3.ExtendedFloatingActionButton\n', '')

old_top = '''                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = account?.person?.displayName ?: "تفاصيل الحساب",
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                        )
                        if (account != null) {
                            Text(
                                text = "تفاصيل الحساب وسجل العمليات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("رجوع")
                    }
                },
'''
new_top = '''                title = {
                    Text(
                        text = account?.person?.displayName ?: "تفاصيل الحساب",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
                    }
                },
'''
assert old_top in s
s = s.replace(old_top, new_top, 1)

s, n = re.subn(r'''\n        floatingActionButton = \{[\s\S]*?\n        \},\n    \) \{ scaffoldPadding ->''', '\n    ) { scaffoldPadding ->', s, count=1)
assert n == 1, n

old_call = '''                    onOpenDueSchedule = onOpenDueSchedule,
                    paymentPromises = state.paymentPromises,
'''
new_call = '''                    onOpenDueSchedule = onOpenDueSchedule,
                    onOpenPayment = onOpenPayment,
                    paymentPromises = state.paymentPromises,
'''
assert old_call in s
s = s.replace(old_call, new_call, 1)

old_sig = '''    onOpenDueSchedule: () -> Unit,
    paymentPromises: List<PaymentPromiseRecord>,
'''
new_sig = '''    onOpenDueSchedule: () -> Unit,
    onOpenPayment: () -> Unit,
    paymentPromises: List<PaymentPromiseRecord>,
'''
assert old_sig in s
s = s.replace(old_sig, new_sig, 1)

s = s.replace('bottom = 112.dp,', 'bottom = 32.dp,', 1)
s = s.replace('AccountSummaryCard(account, onOpenDueSchedule)', 'AccountSummaryCard(account, onOpenPayment, onOpenDueSchedule, onOpenPaymentPromise)', 1)

new_summary = r'''@Composable
private fun AccountSummaryCard(
    account: AccountOverview,
    onOpenPayment: () -> Unit,
    onOpenDueSchedule: () -> Unit,
    onOpenPaymentPromise: () -> Unit,
) {
    val openPersonTimeline = LocalOpenPersonTimeline.current
    val ledger = account.ledger
    val receivable = ledger.header.direction == DebtDirection.RECEIVABLE
    val originalMinor = ledger.header.originalAmount.minorUnits.coerceAtLeast(0L)
    val paidMinor = ledger.paidAmount.minorUnits.coerceAtLeast(0L)
    val progress = if (originalMinor == 0L) 0f else (paidMinor.toDouble() / originalMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccountHeroBadges(receivable = receivable, state = ledger.state)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "المتبقي",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = formatMoney(ledger.balance),
                        modifier = Modifier.testTag("account-remaining"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ledger.header.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            AccountFinancialMetrics(
                originalAmount = ledger.header.originalAmount,
                paidAmount = ledger.paidAmount,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("تقدم السداد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            AccountPrimaryActions(
                canRecordPayment = !ledger.balance.isZero,
                onOpenPayment = onOpenPayment,
                onOpenDueSchedule = onOpenDueSchedule,
                onOpenPaymentPromise = onOpenPaymentPromise,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataRow("العملة", ledger.header.originalAmount.currency.value)
                    MetadataRow("تاريخ الإنشاء", formatInstant(ledger.header.openedAt))
                    MetadataRow("تاريخ الاستحقاق", ledger.header.dueDate?.let(::formatDate) ?: "غير محدد")
                    account.dueReminder?.let { reminder ->
                        MetadataRow("موعد المتابعة الأساسي", formatInstant(reminder.triggerAt))
                        MetadataRow(
                            "حالة التذكير",
                            when (reminder.status) {
                                ReminderStatus.SCHEDULED -> "مجدول"
                                ReminderStatus.DELIVERED -> "تم إظهاره"
                                ReminderStatus.BLOCKED_PERMISSION -> "بانتظار إذن الإشعارات"
                                ReminderStatus.FAILED -> "ستُعاد المحاولة"
                                ReminderStatus.CANCELLED -> "ملغى"
                            },
                        )
                    }
                    account.strongAlarm
                        ?.takeIf { it.status != ReminderStatus.CANCELLED }
                        ?.let { MetadataRow("المنبه الدقيق", formatInstant(it.triggerAt)) }
                    account.closedAt?.let { MetadataRow("تاريخ الإغلاق", formatInstant(it)) }
                }
            }

            TextButton(
                onClick = { openPersonTimeline(account.person.id) },
                modifier = Modifier.fillMaxWidth().testTag("open-person-timeline"),
            ) {
                Text("سجل الشخص الكامل")
            }
        }
    }
}

@Composable
private fun AccountPrimaryActions(
    canRecordPayment: Boolean,
    onOpenPayment: () -> Unit,
    onOpenDueSchedule: () -> Unit,
    onOpenPaymentPromise: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("account-primary-actions-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenPayment,
                    enabled = canRecordPayment,
                    modifier = Modifier.fillMaxWidth().testTag("record-payment"),
                ) { Text("تسجيل دفعة") }
                OutlinedButton(
                    onClick = onOpenDueSchedule,
                    enabled = canRecordPayment,
                    modifier = Modifier.fillMaxWidth().testTag("edit-due-schedule"),
                ) { Text("الموعد") }
                OutlinedButton(
                    onClick = onOpenPaymentPromise,
                    enabled = canRecordPayment,
                    modifier = Modifier.fillMaxWidth().testTag("account-add-payment-promise"),
                ) { Text("وعد سداد") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("account-primary-actions-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenPayment,
                    enabled = canRecordPayment,
                    modifier = Modifier.weight(1f).testTag("record-payment"),
                ) { Text("دفعة") }
                OutlinedButton(
                    onClick = onOpenDueSchedule,
                    enabled = canRecordPayment,
                    modifier = Modifier.weight(1f).testTag("edit-due-schedule"),
                ) { Text("الموعد") }
                OutlinedButton(
                    onClick = onOpenPaymentPromise,
                    enabled = canRecordPayment,
                    modifier = Modifier.weight(1f).testTag("account-add-payment-promise"),
                ) { Text("وعد") }
            }
        }
    }
}

'''

s, n = re.subn(
    r'@Composable\nprivate fun AccountSummaryCard\([\s\S]*?(?=@Composable\ninternal fun AccountTimelineHeading)',
    new_summary,
    s,
    count=1,
)
assert n == 1, n

# Tighten timeline copy while keeping invariant meaning.
s = s.replace('text = "الأصل محفوظ، وكل دفعة أو عكس يظهر كسجل مستقل.",', 'text = "كل دفعة أو تصحيح محفوظ كسجل مستقل.",', 1)
s = s.replace('text = "سجل موثق",', 'text = "موثق",', 1)

p.write_text(s)
