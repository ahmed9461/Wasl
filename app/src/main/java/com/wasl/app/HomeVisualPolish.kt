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
internal fun HomeHeroCard(accountCount: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("home-hero")) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("home-hero-stacked").padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeBrandBlock()
                HomeHeroMetric(accountCount)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("home-hero-inline").padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeBrandBlock(modifier = Modifier.weight(1f))
                HomeHeroMetric(accountCount)
            }
        }
    }
}

@Composable
private fun HomeBrandBlock(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("وَصل", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.tertiary)
        Text("كل حساب له وصل", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeHeroMetric(accountCount: Int) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(accountCount.toString(), Modifier.testTag("home-hero-account-count"), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(if (accountCount == 1) "حساب" else "حسابات", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HomeSectionHeader(title: String, subtitle: String, tagPrefix: String, count: Int? = null, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = shouldStackDenseRows(maxWidth)
        if (count != null && stacked) {
            Column(Modifier.fillMaxWidth().testTag("$tagPrefix-heading-stacked"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeSectionCopy(title, subtitle)
                HomeSectionCount(count, tagPrefix)
            }
        } else {
            Row(Modifier.fillMaxWidth().testTag("$tagPrefix-heading-inline"), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                HomeSectionCopy(title, subtitle)
                count?.let { HomeSectionCount(it, tagPrefix) }
            }
        }
    }
}

@Composable
private fun HomeSectionCopy(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSectionCount(count: Int, tagPrefix: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(count.toString(), Modifier.testTag("$tagPrefix-count").padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun CreateEntryOption(title: String, description: String, primary: Boolean, testTag: String, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (primary) {
        Button(modifier.fillMaxWidth().testTag(testTag), onClick = onClick, shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)) { content() }
    } else {
        OutlinedButton(modifier.fillMaxWidth().testTag(testTag), onClick = onClick, shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}
