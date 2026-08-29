from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'anchor not found: {label}')
    return text.replace(old, new, 1)

def regex_once(text, pattern, repl, label):
    new, n = re.subn(pattern, repl, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'regex {label} matched {n}')
    return new

# 1) Standard phones should use compact horizontal layouts; stack only when truly narrow or large-font.
p = 'app/src/main/java/com/wasl/app/AdaptiveLayout.kt'
t = read(p)
t = replace_once(t, 'availableWidth < 420.dp || fontScale >= 1.3f', 'availableWidth < 340.dp || fontScale >= 1.3f', 'adaptive threshold')
write(p, t)

# 2) Home: hide zero-only currencies and compact account cards / remove oversized initial avatar.
p = 'app/src/main/java/com/wasl/app/WaslApp.kt'
t = read(p)
new_currency = '''@Composable
private fun HomeCurrencyOverview(state: HomeUiState) {
    val visibleCurrencies = supportedCurrencies.filter { currency ->
        val receivable = state.balanceSummary.receivableByCurrency[currency] ?: Money.zero(currency)
        val payable = state.balanceSummary.payableByCurrency[currency] ?: Money.zero(currency)
        !receivable.isZero || !payable.isZero
    }

    if (visibleCurrencies.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-empty"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = "لا توجد أرصدة مفتوحة حاليًا.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = shouldStackDenseRows(maxWidth) || visibleCurrencies.size > 2 && maxWidth < 390.dp
        if (stack) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleCurrencies.forEach { currency ->
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
                visibleCurrencies.forEach { currency ->
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
'''
t = regex_once(t, r'@Composable\nprivate fun HomeCurrencyOverview\(state: HomeUiState\) \{.*?\n\}\n\n@Composable\nprivate fun CurrencyBalanceTile', new_currency + '\n@Composable\nprivate fun CurrencyBalanceTile', 'HomeCurrencyOverview')

new_account = '''@Composable
internal fun AccountCard(account: AccountOverview, onClick: () -> Unit) {
    val header = account.ledger.header
    val receivable = header.direction == DebtDirection.RECEIVABLE
    val tagPrefix = "account-${header.id.value}"
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tagPrefix),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-header-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountIdentityRow(account, receivable, Modifier.weight(1f))
                AccountDirectionBadge(receivable)
            }
            Row(
                modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-balance-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountRemainingBalance(account)
                AccountStateBadge(account.ledger.state)
            }
            val dueText = header.dueDate?.let { "الاستحقاق ${it.format(dueDateFormatter)}" } ?: "بدون استحقاق"
            Row(
                modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-original-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "الأصل ${formatMoney(header.originalAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = account.person.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        account.ledger.header.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AccountDirectionBadge'''
t = regex_once(t, r'@Composable\ninternal fun AccountCard\(account: AccountOverview, onClick: \(\) -> Unit\) \{.*?\n@Composable\nprivate fun AccountDirectionBadge', new_account, 'AccountCard compact')

t = replace_once(
    t,
    '.align(Alignment.BottomStart)\n                                        .padding(start = 20.dp, bottom = 24.dp)',
    '.align(Alignment.TopStart)\n                                        .padding(start = 18.dp, top = 82.dp)',
    'PDF floating position',
)
write(p, t)

# 3) Group expense: compact normal phones, chips wrap naturally, smaller text fields.
p = 'app/src/main/java/com/wasl/app/GroupExpenseDialogs.kt'
t = read(p)
if 'import androidx.compose.foundation.layout.FlowRow' not in t:
    t = t.replace('import androidx.compose.foundation.layout.Column\n', 'import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.ExperimentalLayoutApi\nimport androidx.compose.foundation.layout.FlowRow\n')
t = replace_once(t, '@Composable\nprivate fun GroupExpenseEditContent(', '@OptIn(ExperimentalLayoutApi::class)\n@Composable\nprivate fun GroupExpenseEditContent(', 'GroupExpense opt-in')
t = t.replace('        maxLines = 3,\n', '        maxLines = 2,\n', 2)
old_people = '''        else -> {
            selectablePeople.forEach { person ->
                val selected = form.participants.any { it.person.id == person.id }
                FilterChip(
                    selected = selected,
                    onClick = { onToggleParticipant(person.id) },
                    label = { Text(person.displayName) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group-person-${person.id.value}"),
                )
            }
            if (hasMorePeople) {
                Text(
                    text = "اكتب جزءًا من الاسم لإظهار نتائج أدق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
'''
new_people = '''        else -> {
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("group-people-chips"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                selectablePeople.forEach { person ->
                    val selected = form.participants.any { it.person.id == person.id }
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleParticipant(person.id) },
                        label = { Text(person.displayName, maxLines = 1) },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("group-person-${person.id.value}"),
                    )
                }
            }
            if (hasMorePeople) {
                Text(
                    text = "اكتب جزءًا من الاسم لإظهار نتائج أدق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
'''
t = replace_once(t, old_people, new_people, 'group people chips')
write(p, t)

# 4) Promise actions: one coherent action bar instead of scattered controls.
p = 'app/src/main/java/com/wasl/app/PaymentPromiseUi.kt'
t = read(p)
old_actions = '''                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { onResolve(PaymentPromiseStatus.KEPT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resolve-promise-kept-${promise.id}"),
                        ) {
                            Text("تم الوفاء")
                        }
                        TextButton(
                            onClick = { onResolve(PaymentPromiseStatus.MISSED) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resolve-promise-missed-${promise.id}"),
                        ) {
                            Text("لم يُنفذ")
                        }
                    }
                    TextButton(
                        onClick = { onResolve(PaymentPromiseStatus.CANCELLED) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("resolve-promise-cancelled-${promise.id}"),
                    ) {
                        Text("إلغاء الوعد")
                    }
                }
'''
new_actions = '''                Row(
                    modifier = Modifier.fillMaxWidth().testTag("payment-promise-actions-${promise.id}"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onResolve(PaymentPromiseStatus.KEPT) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-kept-${promise.id}"),
                    ) { Text("تم الوفاء", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onResolve(PaymentPromiseStatus.MISSED) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-missed-${promise.id}"),
                    ) { Text("لم يُنفذ", maxLines = 1) }
                    TextButton(
                        onClick = { onResolve(PaymentPromiseStatus.CANCELLED) },
                        modifier = Modifier.weight(1f).testTag("resolve-promise-cancelled-${promise.id}"),
                    ) { Text("إلغاء", maxLines = 1) }
                }
'''
t = replace_once(t, old_actions, new_actions, 'promise action bar')
write(p, t)

# 5) Attachments: use the real store, catch picker-launch failures, remove implementation details from user copy.
p = 'app/src/main/java/com/wasl/app/MainActivity.kt'
t = read(p)
old_route = '''                            DocumentsHubRoute(
                                repository = waslApplication.repository,
                                documentService = waslApplication.paymentReceiptService,
                                initialDebtId = documentsDebtId,
                                onBack = {'''
new_route = '''                            DocumentsHubRoute(
                                repository = waslApplication.repository,
                                documentService = waslApplication.paymentReceiptService,
                                attachmentStore = waslApplication.attachmentStore,
                                initialDebtId = documentsDebtId,
                                onBack = {'''
t = replace_once(t, old_route, new_route, 'real attachment store')
write(p, t)

p = 'app/src/main/java/com/wasl/app/DocumentsHubScreen.kt'
t = read(p)
t = replace_once(
    t,
    '"الملفات تُنسخ إلى التخزين الخاص بالتطبيق وتُفحص ببصمة SHA-256. لا يحتاج وَصل إلى صلاحية الوصول الشامل للملفات."',
    '"أضف صورًا أو ملفات مرتبطة بهذا الحساب لتجدها معه لاحقًا."',
    'attachment user copy',
)
old_click = 'onClick = { attachmentPicker.launch(arrayOf("image/*", "application/pdf", "text/*", "application/octet-stream")) },'
new_click = '''onClick = {
                                    runCatching {
                                        attachmentPicker.launch(arrayOf("image/*", "application/pdf", "text/*", "application/octet-stream"))
                                    }.onFailure {
                                        showMessage("تعذر فتح منتقي الملفات على هذا الجهاز.")
                                    }
                                },'''
t = replace_once(t, old_click, new_click, 'attachment picker guard')
t = replace_once(t, 'if (attachmentBusy) CircularProgressIndicator() else Text("إضافة صورة أو PDF أو ملف")', 'if (attachmentBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("إضافة مرفق")', 'attachment button compact')
if 'import androidx.compose.foundation.layout.size' not in t:
    t = t.replace('import androidx.compose.foundation.layout.safeDrawing\n', 'import androidx.compose.foundation.layout.safeDrawing\nimport androidx.compose.foundation.layout.size\n')
write(p, t)

print('v0.4 batch1 patch applied')
