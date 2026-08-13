package com.wasl.app

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.WaslRepository
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtState
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val supportedCurrencies = listOf(
    CurrencyCode.YER,
    CurrencyCode.SAR,
    CurrencyCode.USD,
)

@Composable
fun WaslApp(repository: WaslRepository) {
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(repository),
    )
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WaslTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                WaslHomeScreen(
                    state = state,
                    onOpenCreate = homeViewModel::openCreateDialog,
                    onDismissCreate = homeViewModel::dismissCreateDialog,
                    onPersonNameChange = homeViewModel::updatePersonName,
                    onAmountChange = homeViewModel::updateAmount,
                    onCurrencyChange = homeViewModel::updateCurrency,
                    onDirectionChange = homeViewModel::updateDirection,
                    onDescriptionChange = homeViewModel::updateDescription,
                    onSave = homeViewModel::createPersonWithDebt,
                    onSuccessShown = homeViewModel::clearSuccessMessage,
                )
            }
        }
    }
}

@Composable
private fun WaslHomeScreen(
    state: HomeUiState,
    onOpenCreate: () -> Unit,
    onDismissCreate: () -> Unit,
    onPersonNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
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
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCreate,
            ) {
                Text("إضافة حساب")
            }
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
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "وَصل",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "كل حساب له وصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isLoading) {
                item {
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
                item {
                    SummaryCard(
                        title = "لي عند الناس",
                        values = summaryRows(state.balanceSummary.receivableByCurrency),
                    )
                }
                item {
                    SummaryCard(
                        title = "عليّ للناس",
                        values = summaryRows(state.balanceSummary.payableByCurrency),
                    )
                }

                state.loadError?.let { error ->
                    item { StatusCard(message = error, isError = true) }
                }

                if (state.accounts.isEmpty() && state.loadError == null) {
                    item {
                        StatusCard(
                            message = "لا توجد حسابات بعد. أضف شخصًا ودينًا، وسيبقى محفوظًا بعد إغلاق التطبيق.",
                            isError = false,
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "الحسابات",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(
                        items = state.accounts,
                        key = { it.ledger.header.id.value },
                    ) { account ->
                        AccountCard(account)
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
            onDismiss = onDismissCreate,
            onPersonNameChange = onPersonNameChange,
            onAmountChange = onAmountChange,
            onCurrencyChange = onCurrencyChange,
            onDirectionChange = onDirectionChange,
            onDescriptionChange = onDescriptionChange,
            onSave = onSave,
        )
    }
}

@Composable
private fun CreateDebtDialog(
    form: CreateDebtForm,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPersonNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حساب جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.personName,
                    onValueChange = onPersonNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم الشخص") },
                    singleLine = true,
                    enabled = !isSaving,
                )

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
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSaving,
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
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
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
}

@Composable
private fun AccountCard(account: AccountOverview) {
    val header = account.ledger.header
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (header.direction) {
                        DebtDirection.RECEIVABLE -> "لي عنده"
                        DebtDirection.PAYABLE -> "عليّ له"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            header.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            MoneyRow("الأصل", header.originalAmount)
            MoneyRow("المتبقي", account.ledger.balance)
            Text(
                text = when (account.ledger.state) {
                    DebtState.OPEN -> "مفتوح"
                    DebtState.PARTIALLY_PAID -> "مسدد جزئيًا"
                    DebtState.SETTLED -> "مسدد"
                },
                style = MaterialTheme.typography.labelMedium,
            )
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
private fun SummaryCard(title: String, values: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            values.forEachIndexed { index, value ->
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (index != values.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
        )
    }
}

private fun summaryRows(values: Map<CurrencyCode, Money>): List<String> =
    supportedCurrencies.map { currency ->
        formatMoney(values[currency] ?: Money.zero(currency))
    }

private fun formatMoney(money: Money): String {
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
                onOpenCreate = {},
                onDismissCreate = {},
                onPersonNameChange = {},
                onAmountChange = {},
                onCurrencyChange = {},
                onDirectionChange = {},
                onDescriptionChange = {},
                onSave = {},
                onSuccessShown = {},
            )
        }
    }
}
