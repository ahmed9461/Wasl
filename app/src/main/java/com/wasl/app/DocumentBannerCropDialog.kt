package com.wasl.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wasl.app.document.DocumentBannerCropper

@Composable
internal fun DocumentBannerCropDialog(
    sourceBytes: ByteArray,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (focusX: Float, focusY: Float) -> Unit,
) {
    val preview = remember(sourceBytes) {
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)?.asImageBitmap()
    }
    var focusX by remember(sourceBytes) { mutableFloatStateOf(0.5f) }
    var focusY by remember(sourceBytes) { mutableFloatStateOf(0.5f) }
    val alignment = BiasAlignment(
        horizontalBias = (focusX * 2f) - 1f,
        verticalBias = (focusY * 2f) - 1f,
    )

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ضبط صورة رأس المستند") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "اضبط موضع الصورة داخل المساحة العريضة التي ستظهر أعلى ملف PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(DocumentBannerCropper.HEADER_ASPECT_RATIO)
                        .testTag("documents-banner-crop-preview"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "معاينة قص صورة رأس المستند",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(DocumentBannerCropper.HEADER_ASPECT_RATIO)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                            alignment = alignment,
                        )
                    } else {
                        Text(
                            "تعذر قراءة الصورة المختارة.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Text("الموضع الأفقي", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = focusX,
                    onValueChange = { focusX = it },
                    enabled = !busy && preview != null,
                    modifier = Modifier.testTag("documents-banner-focus-x"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("يسار", style = MaterialTheme.typography.bodySmall)
                    Text("وسط", style = MaterialTheme.typography.bodySmall)
                    Text("يمين", style = MaterialTheme.typography.bodySmall)
                }

                Text("الموضع الرأسي", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = focusY,
                    onValueChange = { focusY = it },
                    enabled = !busy && preview != null,
                    modifier = Modifier.testTag("documents-banner-focus-y"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("أعلى", style = MaterialTheme.typography.bodySmall)
                    Text("وسط", style = MaterialTheme.typography.bodySmall)
                    Text("أسفل", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(focusX, focusY) },
                enabled = !busy && preview != null,
                modifier = Modifier.testTag("documents-banner-crop-confirm"),
            ) {
                Text(if (busy) "جارٍ التجهيز…" else "استخدام الصورة")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("documents-banner-crop-cancel"),
            ) {
                Text("إلغاء")
            }
        },
    )
}
