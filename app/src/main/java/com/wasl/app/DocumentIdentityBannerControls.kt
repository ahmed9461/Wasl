package com.wasl.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DocumentIdentityBannerControls(
    previewBytes: ByteArray?,
    hasBanner: Boolean,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val preview = remember(previewBytes) {
        previewBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "صورة رأس المستند",
            style = MaterialTheme.typography.labelLarge,
        )
        if (preview != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("documents-banner-preview"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Image(
                    bitmap = preview,
                    contentDescription = "معاينة صورة رأس المستند",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        } else if (hasBanner) {
            Text(
                "تعذر عرض المعاينة. اختر الصورة من جديد قبل إصدار مستند.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "اختياري — تظهر الصورة أعلى المستندات الجديدة فقط.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .testTag("documents-banner-picker"),
                enabled = !busy,
                onClick = onPick,
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text(if (hasBanner) "تغيير الصورة" else "اختيار صورة")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("documents-banner-remove"),
                enabled = hasBanner && !busy,
                onClick = onRemove,
            ) {
                Text("إزالة")
            }
        }
    }
}
