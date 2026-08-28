package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeHeroCard(
    accountCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-hero"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            val stacked = shouldStackDenseRows(maxWidth)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                ) {
                    Text(
                        text = "دفترك المالي الشخصي",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "وَصل",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "كل حساب له وصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "حقوقك والتزاماتك محفوظة بوضوح، من أول تسجيل إلى آخر دفعة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f))
                if (stacked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home-hero-stacked"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HomeHeroMetric(accountCount)
                        Text(
                            text = "أضف حسابًا فرديًا أو عملية جماعية من زر الإضافة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home-hero-inline"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeHeroMetric(accountCount)
                        Text(
                            text = "فردي أو جماعي — بنفس دفتر وَصل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroMetric(accountCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "الحسابات المحفوظة",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = accountCount.toString(),
            modifier = Modifier.testTag("home-hero-account-count"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
internal fun HomeSectionHeader(
    title: String,
    subtitle: String,
    tagPrefix: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = shouldStackDenseRows(maxWidth)
        if (count != null && stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$tagPrefix-heading-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSectionCopy(title, subtitle)
                HomeSectionCount(count = count, tagPrefix = tagPrefix)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$tagPrefix-heading-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSectionCopy(title, subtitle)
                if (count != null) HomeSectionCount(count = count, tagPrefix = tagPrefix)
            }
        }
    }
}

@Composable
private fun HomeSectionCopy(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeSectionCount(count: Int, tagPrefix: String) {
    Surface(
        modifier = Modifier.testTag("$tagPrefix-count"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
internal fun CreateEntryOption(
    title: String,
    description: String,
    primary: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (primary) {
        Button(
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTag),
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) { content() }
    } else {
        OutlinedButton(
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTag),
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) { content() }
    }
}
