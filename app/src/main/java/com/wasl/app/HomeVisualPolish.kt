package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val stacked = shouldStackDenseRows(maxWidth)
            if (stacked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home-hero-stacked"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HomeBrandBlock()
                    HomeHeroMetric(accountCount)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home-hero-inline"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeBrandBlock(modifier = Modifier.weight(1f))
                    HomeHeroMetric(accountCount)
                }
            }
        }
    }
}

@Composable
private fun HomeBrandBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "وَصل",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "كل حساب له وصل",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "دفترك المالي الشخصي",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeHeroMetric(accountCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = accountCount.toString(),
                modifier = Modifier.testTag("home-hero-account-count"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "حساب",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeSectionCount(count: Int, tagPrefix: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier
                .testTag("$tagPrefix-count")
                .padding(horizontal = 12.dp, vertical = 7.dp),
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            content()
        }
    } else {
        OutlinedButton(
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTag),
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
        ) {
            content()
        }
    }
}
