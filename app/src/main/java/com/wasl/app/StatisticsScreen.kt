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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        when {
            state.isLoading && state.statistics == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.statistics == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        state.errorMessage ?: "تعذر عرض الإحصاءات.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) { Text("إعادة المحاولة") }
                }
            }
            else -> {
                val stats = state.statistics
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
                            .testTag("objective-statistics-screen"),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item("header") {
                            StatisticsHeader(onBack = onBack)
                        }

                        item("debt-summary") {
                            StatisticsCard("الحسابات") {
                                StatisticRow("إجمالي الحسابات", stats.totalAccounts.toString())
                                StatisticRow("المسددة", stats.settledAccounts.toString())
                                StatisticRow("المفتوحة", stats.openAccounts.toString())
                                StatisticRow(
                                    "متوسط مدة السداد",
                                    stats.averageSettlementDays.formatDaysOrUnavailable(),
                                )
                            }
                        }

                        item("delay-summary") {
                            StatisticsCard("الاستحقاق والتأخير") {
                                StatisticRow(
                                    "حسابات مسددة لها استحقاق",
                                    stats.settledAccountsWithDueDate.toString(),
                                )
                                StatisticRow("المسددة بعد الاستحقاق", stats.lateSettledAccounts.toString())
                                StatisticRow(
                                    "متوسط التأخير للحالات المتأخرة",
                                    stats.averageLateDays.formatDaysOrUnavailable(),
                                )
                            }
                        }

                        item("promise-summary") {
                            StatisticsCard("وعود السداد") {
                                StatisticRow("تم الوفاء بها", stats.keptPromises.toString())
                                StatisticRow("لم يتم الوفاء بها", stats.missedPromises.toString())
                                StatisticRow("قيد الانتظار", stats.pendingPromises.toString())
                                StatisticRow("ملغاة", stats.cancelledPromises.toString())
                            }
                        }

                        item("method-note") {
                            Text(
                                "لا تُجمع مبالغ العملات المختلفة في هذه الصفحة. مدة السداد تُحسب من فتح الحساب حتى إغلاقه، ومتوسط التأخير يشمل فقط الحسابات المسددة بعد تاريخ الاستحقاق.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("statistics-method-note"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsHeader(onBack: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistics-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text("رجوع") }
                StatisticsHeaderText()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistics-header-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text("رجوع") }
                StatisticsHeaderText()
            }
        }
    }
}

@Composable
private fun StatisticsHeaderText() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "الإحصاءات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            "أرقام موضوعية من سجل وَصل فقط، بدون تقييم الأشخاص.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatisticsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatisticRow(label: String, value: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistic-row-stacked-$label"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label)
                Text(value, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistic-row-inline-$label"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f))
                Text(value, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val dayFormat = DecimalFormat("0.#")

private fun Double?.formatDaysOrUnavailable(): String =
    this?.let { "${dayFormat.format(it)} يوم" } ?: "لا توجد بيانات كافية"
