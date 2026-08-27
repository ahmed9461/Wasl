package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonTimelineScreen(
    state: PersonTimelineUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenAccount: (DebtId) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.person?.displayName ?: "صفحة الشخص",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (state.person != null) {
                            Text(
                                "الحسابات والسجل الموحد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.person == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.person == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        state.loadError ?: "تعذر العثور على الشخص.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) { Text("إعادة المحاولة") }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = WaslMaxContentWidth)
                            .fillMaxWidth()
                            .testTag("person-timeline-screen"),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item("person-contact") {
                            PersonContactCard(state)
                        }
                        item("balance-title") {
                            Text("الملخص حسب العملة والاتجاه", fontWeight = FontWeight.ExtraBold)
                        }
                        items(
                            state.balanceGroups,
                            key = { "${it.currency.value}:${it.direction.name}" },
                        ) { group ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        "${directionLabel(group.direction)} • ${group.currency.value}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text("${group.accountCount} حساب")
                                    Text("الأصل: ${formatPersonMoney(group.originalAmount)}")
                                    Text("المسدد: ${formatPersonMoney(group.paidAmount)}")
                                    Text(
                                        "المتبقي: ${formatPersonMoney(group.balance)}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        item("accounts-title") {
                            Text("الحسابات", fontWeight = FontWeight.ExtraBold)
                        }
                        items(state.accounts, key = { it.ledger.header.id.value }) { account ->
                            PersonAccountCard(account, onOpenAccount)
                        }
                        item("timeline-title") {
                            Text("السجل الزمني", fontWeight = FontWeight.ExtraBold)
                        }
                        if (state.timeline.isEmpty()) {
                            item("timeline-empty") {
                                Text(
                                    "لا توجد أحداث إضافية بعد.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.timeline, key = { it.id }) { event ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("person-timeline-${event.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(15.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        PersonTimelineEventHeader(event)
                                        Text(
                                            "${directionLabel(event.direction)} • ${event.currency.value}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        event.detail?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        OutlinedButton(onClick = { onOpenAccount(event.debtId) }) {
                                            Text("فتح الحساب المرتبط")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonTimelineEventHeader(event: PersonTimelineEvent) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("person-timeline-header-stacked-${event.id}"),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Text(
                    formatPersonTimelineInstant(event.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("person-timeline-header-inline-${event.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Text(
                    formatPersonTimelineInstant(event.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonContactCard(state: PersonTimelineUiState) {
    val person = requireNotNull(state.person)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("person-profile-header"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(person.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("${state.accounts.size} حساب مرتبط")
            person.phone?.takeIf { it.isNotBlank() }?.let { Text("الهاتف: $it") }
            person.email?.takeIf { it.isNotBlank() }?.let { Text("البريد: $it") }
            person.notes?.takeIf { it.isNotBlank() }?.let { Text("ملاحظات: $it") }
            if (state.balanceGroups.map { it.currency }.distinct().size > 1) {
                Text(
                    "تُعرض كل عملة منفصلة ولا يتم جمع العملات المختلفة في إجمالي واحد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("person-multi-currency-note"),
                )
            }
        }
    }
}

@Composable
private fun PersonAccountCard(
    account: AccountOverview,
    onOpenAccount: (DebtId) -> Unit,
) {
    val header = account.ledger.header
    Card(modifier = Modifier.fillMaxWidth().testTag("person-account-${header.id.value}")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${directionLabel(header.direction)} • ${header.originalAmount.currency.value}",
                fontWeight = FontWeight.Bold,
            )
            Text("الأصل: ${formatPersonMoney(header.originalAmount)}")
            Text("المتبقي: ${formatPersonMoney(account.ledger.balance)}")
            Text("فتح في ${header.openedAt.atZone(ZoneId.systemDefault()).toLocalDate()}")
            header.dueDate?.let { Text("الاستحقاق: $it") }
            header.description?.let { Text(it) }
            Text(
                "الحالة: ${account.lifecycleState.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onOpenAccount(header.id) }) { Text("فتح الحساب") }
        }
    }
}

private fun directionLabel(direction: DebtDirection): String = when (direction) {
    DebtDirection.RECEIVABLE -> "لي عنده"
    DebtDirection.PAYABLE -> "عليّ له"
}

private fun formatPersonMoney(money: Money): String {
    val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
    val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return "${formatter.format(major)} ${money.currency.value}"
}

private val personTimelineFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.US)

private fun formatPersonTimelineInstant(instant: java.time.Instant): String =
    personTimelineFormatter.format(instant.atZone(ZoneId.systemDefault()))
