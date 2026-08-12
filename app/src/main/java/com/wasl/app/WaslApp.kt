package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasl.app.ui.theme.WaslTheme

@Composable
fun WaslApp() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WaslTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                FoundationHomeScreen()
            }
        }
    }
}

@Composable
private fun FoundationHomeScreen() {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
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

            item {
                SummaryCard(
                    title = "لي عند الناس",
                    values = listOf(
                        ltrMoney("0", "YER"),
                        ltrMoney("0", "SAR"),
                        ltrMoney("0", "USD"),
                    ),
                )
            }

            item {
                SummaryCard(
                    title = "عليّ للناس",
                    values = listOf(
                        ltrMoney("0", "YER"),
                        ltrMoney("0", "SAR"),
                        ltrMoney("0", "USD"),
                    ),
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "المرحلة التأسيسية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "تم تأسيس السجل المالي واختبارات الأرصدة. ستُفعّل إجراءات إضافة الحسابات بعد ربط قاعدة البيانات حتى لا تظهر وظائف تحفظ بيانات مؤقتة.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    values: List<String>,
) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (index != values.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun ltrMoney(amount: String, currency: String): String =
    "\u2066" + amount + " " + currency + "\u2069"

@Preview(showBackground = true, locale = "ar")
@Composable
private fun FoundationHomeScreenPreview() {
    WaslApp()
}
