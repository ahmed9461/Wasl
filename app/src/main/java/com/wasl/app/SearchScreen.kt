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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.wasl.app.data.AdvancedSearchResult
import com.wasl.app.data.AdvancedSearchResultType
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState
import java.time.format.DateTimeFormatter
import java.util.Locale

private val searchResultDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/uuuu",
    Locale.US,
)

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
        containerColor = MaterialTheme.colorScheme.background,
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
                top = 22.dp,
                end = 20.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("search-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "بحث محلي داخل بياناتك",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = "البحث",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "ابحث بالاسم أو البيان أو ملاحظة عملية أو رقم مستند أو مبلغ أو تاريخ.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item("search-input") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag("search-input"),
                        label = { Text("اسم، بيان، رقم مستند، مبلغ أو تاريخ") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
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
                            Text("يعرض حتى $SEARCH_RESULT_LIMIT نتيجة في كل قسم.")
                        },
                    )
                }
            }

            when {
                state.isQueryBlank -> item("search-start") {
                    SearchMessageCard(
                        title = "ابحث بسرعة",
                        message = "اكتب اسمًا أو وصفًا أو رقم مستند، أو أدخل مبلغًا أو تاريخًا مثل 13/08/2026.",
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

                !state.hasAnyResults -> item("search-empty") {
                    SearchMessageCard(
                        title = "لا توجد نتائج",
                        message = "لا توجد نتائج مطابقة لهذا البحث.",
                        testTag = "search-empty",
                    )
                }

                else -> {
                    if (state.results.isNotEmpty()) {
                        item("search-results-heading") {
                            SearchSectionHeading(
                                title = "الحسابات والديون",
                                subtitle = "مطابقات الاسم وبيان الدين",
                                count = state.results.size,
                            )
                        }
                        if (state.hasMoreResults) {
                            item("search-limit-notice") {
                                SearchMessageCard(
                                    title = "نتائج حسابات إضافية",
                                    message = "توجد نتائج إضافية. ضيّق عبارة البحث لعرض نتيجة أدق.",
                                    testTag = "search-limit-notice",
                                )
                            }
                        }
                        items(
                            items = state.results,
                            key = { "account-${it.ledger.header.id.value}" },
                        ) { account ->
                            SearchResultCard(
                                account = account,
                                onOpen = { onOpenAccount(account.ledger.header.id) },
                            )
                        }
                    }

                    if (state.advancedResults.isNotEmpty()) {
                        item("advanced-search-results-heading") {
                            SearchSectionHeading(
                                title = "العمليات والمستندات",
                                subtitle = "مطابقات المبالغ والتواريخ والملاحظات وأرقام المستندات",
                                count = state.advancedResults.size,
                            )
                        }
                        if (state.hasMoreAdvancedResults) {
                            item("advanced-search-limit-notice") {
                                SearchMessageCard(
                                    title = "نتائج إضافية",
                                    message = "توجد عمليات أو مستندات إضافية. استخدم مبلغًا أو تاريخًا أو رقمًا أدق.",
                                    testTag = "advanced-search-limit-notice",
                                )
                            }
                        }
                        items(
                            items = state.advancedResults,
                            key = { "advanced-${it.type}-${it.id}" },
                        ) { result ->
                            AdvancedSearchResultCard(
                                result = result,
                                onOpen = { onOpenAccount(result.debtId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeading(
    title: String,
    subtitle: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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
                PersonAvatar(name = account.person.displayName)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = account.person.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    ledger.header.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                DirectionPill(direction = ledger.header.direction)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "المتبقي",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMoney(ledger.balance),
                        modifier = Modifier.testTag("search-balance-${ledger.header.id.value}"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Start,
                    )
                }
                AccountStatePill(state = ledger.state)
            }

            Text(
                text = "فتح الحساب",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AdvancedSearchResultCard(
    result: AdvancedSearchResult,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                "search-advanced-${result.type.name.lowercase(Locale.ROOT)}-${result.id}",
            ),
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
                PersonAvatar(name = result.personName)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = result.personName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = advancedSearchTitle(result),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    result.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                SearchTypePill(result.type)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "المبلغ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMoney(result.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "التاريخ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = result.date.format(searchResultDateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (result.type == AdvancedSearchResultType.DOCUMENT) {
                Text(
                    text = "${documentTypeLabel(requireNotNull(result.documentType))} • ${documentStatusLabel(requireNotNull(result.documentStatus))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "فتح الحساب المرتبط",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun advancedSearchTitle(result: AdvancedSearchResult): String = when (result.type) {
    AdvancedSearchResultType.DEBT -> result.description ?: "دين مطابق"
    AdvancedSearchResultType.PAYMENT -> "دفعة"
    AdvancedSearchResultType.PAYMENT_REVERSAL -> "عكس دفعة"
    AdvancedSearchResultType.DOCUMENT -> requireNotNull(result.documentNumber)
}

@Composable
private fun SearchTypePill(type: AdvancedSearchResultType) {
    val text = when (type) {
        AdvancedSearchResultType.DEBT -> "دين"
        AdvancedSearchResultType.PAYMENT -> "دفعة"
        AdvancedSearchResultType.PAYMENT_REVERSAL -> "عكس"
        AdvancedSearchResultType.DOCUMENT -> "مستند"
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun documentTypeLabel(type: DocumentType): String = when (type) {
    DocumentType.DEBT_RECEIPT -> "إيصال دين"
    DocumentType.PAYMENT_RECEIPT -> "إيصال سداد"
    DocumentType.ACCOUNT_STATEMENT -> "كشف حساب"
}

private fun documentStatusLabel(status: DocumentStatus): String = when (status) {
    DocumentStatus.PENDING_PDF -> "قيد التجهيز"
    DocumentStatus.READY -> "جاهز"
    DocumentStatus.FAILED -> "تعذر التجهيز"
}

@Composable
private fun PersonAvatar(name: String) {
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
private fun DirectionPill(direction: DebtDirection) {
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
private fun AccountStatePill(state: DebtState) {
    val text = when (state) {
        DebtState.OPEN -> "مفتوح"
        DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
        DebtState.SETTLED -> "مسدد"
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (state == DebtState.SETTLED) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SearchMessageCard(
    title: String,
    message: String,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("search-retry"),
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}
