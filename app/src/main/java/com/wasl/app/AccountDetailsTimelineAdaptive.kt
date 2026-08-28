package com.wasl.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.wasl.domain.Money

@Composable
internal fun AdaptiveDetailMoneyRow(
    label: String,
    money: Money,
    valueModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-detail-money-stacked"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label)
                Text(
                    text = formatMoney(money),
                    modifier = valueModifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-detail-money-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f))
                Text(
                    text = formatMoney(money),
                    modifier = valueModifier,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun AdaptiveMetadataRow(label: String, value: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-metadata-row-stacked"),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-metadata-row-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
internal fun PaymentTimelineStatusHeader(isReversed: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val badge: @Composable () -> Unit = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isReversed) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Text(
                    text = if (isReversed) "معكوسة" else "فعّالة",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = if (isReversed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment-timeline-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("دفعة مسجلة", fontWeight = FontWeight.Bold)
                badge()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment-timeline-header-inline"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("دفعة مسجلة", fontWeight = FontWeight.Bold)
                badge()
            }
        }
    }
}

@Composable
internal fun AccountTimelineDualActions(
    firstLabel: String,
    secondLabel: String,
    firstTag: String,
    secondTag: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-timeline-actions-stacked"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = onFirst,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(firstTag),
                ) {
                    Text(firstLabel)
                }
                TextButton(
                    onClick = onSecond,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(secondTag),
                ) {
                    Text(secondLabel)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-timeline-actions-inline"),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onFirst,
                    modifier = Modifier.testTag(firstTag),
                ) {
                    Text(firstLabel)
                }
                TextButton(
                    onClick = onSecond,
                    modifier = Modifier.testTag(secondTag),
                ) {
                    Text(secondLabel)
                }
            }
        }
    }
}
