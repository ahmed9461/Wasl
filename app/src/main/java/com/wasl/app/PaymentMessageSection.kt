package com.wasl.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.domain.DebtDirection

@Composable
internal fun PaymentMessageSection(account: AccountOverview) {
    if (account.ledger.header.direction != DebtDirection.RECEIVABLE || account.ledger.balance.isZero) return

    val context = LocalContext.current
    val drafts = remember(account) { PaymentMessageTemplates.forAccount(account) }
    var selectedTone by remember(account.ledger.header.id) {
        mutableStateOf(PaymentMessageTone.GENTLE)
    }
    val selected = drafts.first { it.tone == selectedTone }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("payment-message-section"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("رسالة مطالبة جاهزة", fontWeight = FontWeight.ExtraBold)
                Text(
                    "اختر النبرة ثم انسخ أو شارك بنفسك. وَصل لا يرسل الرسائل تلقائيًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                drafts.forEach { draft ->
                    FilterChip(
                        selected = draft.tone == selectedTone,
                        onClick = { selectedTone = draft.tone },
                        label = { Text(draft.title) },
                        modifier = Modifier.testTag("message-tone-${draft.tone.name.lowercase()}"),
                    )
                }
            }
            Text(
                selected.body,
                modifier = Modifier.fillMaxWidth().testTag("payment-message-preview"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { copyPaymentMessage(context, selected.body) },
                    modifier = Modifier.testTag("copy-payment-message"),
                ) {
                    Text("نسخ")
                }
                Button(
                    onClick = { sharePaymentMessage(context, selected.body) },
                    modifier = Modifier.testTag("share-payment-message"),
                ) {
                    Text("مشاركة")
                }
            }
        }
    }
}

private fun copyPaymentMessage(context: Context, message: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("رسالة مطالبة من وَصل", message))
}

private fun sharePaymentMessage(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة رسالة المطالبة"))
}
