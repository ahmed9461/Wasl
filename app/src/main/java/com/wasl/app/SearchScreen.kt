package com.wasl.app

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRetryLoad: () -> Unit,
    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            WaslTopLevelNavigation(
                selected = WaslTopLevelDestination.SEARCH,
                onOpenHome = onOpenHome,
                onOpenToday = onOpenToday,
                onOpenSearch = {},
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
            item("search-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "البحث",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "ابحث باسم الشخص أو بيان الدين داخل بياناتك المحلية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item("search-input") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search-input"),
                    label = { Text("اسم الشخص أو البيان") },
                    singleLine = true,
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = onClearQuery,
                                modifier = Modifier.testTag("search-clear"),
                            ) {
                                Text("مسح")
                            }
                        }
                    } else {
                        null
                    },
                    supportingText = {
                        Text("يعرض البحث أول $SEARCH_RESULT_LIMIT نتيجة كحد أقصى.")
                    },
                )
            }

            when {
                state.isQueryBlank -> item("search-start") {
                    SearchMessageCard(
                        message = "ابدأ بكتابة اسم شخص أو كلمة من بيان الدين.",
                        testTag = "search-start",
                    )
                }

                state.isLoading -> item("search-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("search-loading"))
                    }
                }

                state.loadError != null -> item("search-error") {
                    SearchErrorCard(
                        message = state.loadError,
                        onRetry = onRetryLoad,
                    )
                }

                state.results.isEmpty() -> item("search-empty") {
                    SearchMessageCard(
                        message = "لا توجد نتائج مطابقة لهذا البحث.",
                        testTag = "search-empty",
                    )
                }

                else -> {
                    item("search-results-heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "النتائج",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = state.results.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.hasMoreResults) {
                        item("search-limit-notice") {
                            SearchMessageCard(
                                message = "توجد نتائج إضافية. ضيّق عبارة البحث لعرض نتيجة أدق.",
                                testTag = "search-limit-notice",
                            )
                        }
                    }
                    items(
                        items = state.results,
                        key = { it.ledger.header.id.value },
                    ) { account ->
                        SearchResultCard(
                            account = account,
                            onOpen = { onOpenAccount(account.ledger.header.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    account: AccountOverview,
    onOpen: () -> Unit,
) {
    val ledger = account.ledger
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search-result-${ledger.header.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = account.person.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (ledger.header.direction) {
                        DebtDirection.RECEIVABLE -> "لي عنده"
                        DebtDirection.PAYABLE -> "عليّ له"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ledger.header.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (ledger.state) {
                        DebtState.OPEN -> "مفتوح"
                        DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
                        DebtState.SETTLED -> "مسدد"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = formatMoney(ledger.balance),
                    modifier = Modifier.testTag("search-balance-${ledger.header.id.value}"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End,
                )
            }
            Text(
                text = "فتح الحساب",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SearchMessageCard(
    message: String,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SearchErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
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
            Text(message)
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("search-retry"),
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}
