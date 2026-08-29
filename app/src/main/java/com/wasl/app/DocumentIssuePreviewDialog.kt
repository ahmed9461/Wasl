package com.wasl.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DocumentType
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.app.document.DocumentBannerCropper

internal data class PendingDocumentIssue(
    val account: AccountOverview,
    val type: DocumentType,
    val identityId: String,
    val issuerName: String,
    val activityName: String?,
    val phone: String?,
    val footer: String?,
    val banner: DocumentBannerAsset?,
    val bannerPreviewBytes: ByteArray?,
)

@Composable
internal fun DocumentIssuePreviewDialog(
    preview: PendingDocumentIssue,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val bannerBitmap = remember(preview.bannerPreviewBytes) {
        preview.bannerPreviewBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("معاينة قبل الإصدار") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("document-issue-preview-card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (bannerBitmap != null) {
                            Image(
                                bitmap = bannerBitmap,
                                contentDescription = "معاينة صورة رأس المستند قبل الإصدار",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(DocumentBannerCropper.HEADER_ASPECT_RATIO)
                                    .clip(RoundedCornerShape(10.dp))
                                    .testTag("document-issue-banner-preview"),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                "بدون صورة رأس",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PreviewRow("المستند", documentTypePreviewLabel(preview.type))
                        PreviewRow("الطرف", preview.account.person.displayName)
                        PreviewRow("المُصدر", preview.issuerName)
                        preview.activityName?.let { PreviewRow("النشاط", it) }
                        preview.phone?.let { PreviewRow("الهاتف", it) }
                        preview.footer?.let { PreviewRow("العبارة السفلية", it) }
                    }
                }
                Text(
                    "سيُنشأ المستند بالهوية والصورة المعروضتين هنا، ثم تُحفظان داخل snapshot ثابت للمستند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.testTag("document-issue-confirm"),
            ) {
                Text(if (busy) "جارٍ الإصدار…" else "إصدار المستند")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("document-issue-cancel"),
            ) {
                Text("رجوع للتعديل")
            }
        },
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun documentTypePreviewLabel(type: DocumentType): String = when (type) {
    DocumentType.DEBT_RECEIPT -> "إيصال الدين"
    DocumentType.PAYMENT_RECEIPT -> "إيصال السداد"
    DocumentType.ACCOUNT_STATEMENT -> "كشف الحساب الكامل"
}
