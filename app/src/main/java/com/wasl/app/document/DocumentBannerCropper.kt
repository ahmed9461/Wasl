package com.wasl.app.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object DocumentBannerCropper {
    const val HEADER_ASPECT_RATIO = 499f / 82f
    private const val MAX_DECODE_DIMENSION = 4096
    private const val MAX_OUTPUT_WIDTH = 1800

    fun readCandidate(content: InputStream): ByteArray {
        val bytes = content.readBounded(AndroidDocumentBannerAssetStore.MAX_BANNER_BYTES)
        require(bytes.isNotEmpty()) { "صورة رأس المستند فارغة." }
        val bounds = decodeBounds(bytes)
        require(bounds.first > 0 && bounds.second > 0) { "الملف المختار ليس صورة صالحة." }
        return bytes
    }

    fun cropToHeader(
        sourceBytes: ByteArray,
        focusX: Float,
        focusY: Float,
    ): ByteArray {
        require(sourceBytes.isNotEmpty()) { "صورة رأس المستند فارغة." }
        require(sourceBytes.size <= AndroidDocumentBannerAssetStore.MAX_BANNER_BYTES) {
            "صورة رأس المستند أكبر من الحد المسموح."
        }
        val (sourceWidth, sourceHeight) = decodeBounds(sourceBytes)
        require(sourceWidth > 0 && sourceHeight > 0) { "الملف المختار ليس صورة صالحة." }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(sourceWidth, sourceHeight)
        }
        val source = requireNotNull(
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, options),
        ) { "تعذر قراءة صورة رأس المستند." }

        val normalizedFocusX = focusX.coerceIn(0f, 1f)
        val normalizedFocusY = focusY.coerceIn(0f, 1f)
        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (sourceRatio > HEADER_ASPECT_RATIO) {
            cropHeight = source.height
            cropWidth = (cropHeight * HEADER_ASPECT_RATIO).roundToInt().coerceAtMost(source.width)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / HEADER_ASPECT_RATIO).roundToInt().coerceAtMost(source.height)
        }

        val maxLeft = (source.width - cropWidth).coerceAtLeast(0)
        val maxTop = (source.height - cropHeight).coerceAtLeast(0)
        val left = (maxLeft * normalizedFocusX).roundToInt().coerceIn(0, maxLeft)
        val top = (maxTop * normalizedFocusY).roundToInt().coerceIn(0, maxTop)
        val cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)

        val outputWidth = cropWidth.coerceAtMost(MAX_OUTPUT_WIDTH).coerceAtLeast(1)
        val outputHeight = (outputWidth / HEADER_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
        val outputBitmap = if (cropped.width == outputWidth && cropped.height == outputHeight) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, outputWidth, outputHeight, true)
        }

        return try {
            ByteArrayOutputStream().use { output ->
                check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "تعذر تجهيز صورة رأس المستند."
                }
                output.toByteArray().also { bytes ->
                    require(bytes.isNotEmpty()) { "تعذر تجهيز صورة رأس المستند." }
                    require(bytes.size <= AndroidDocumentBannerAssetStore.MAX_BANNER_BYTES) {
                        "صورة رأس المستند بعد القص أكبر من الحد المسموح."
                    }
                }
            }
        } finally {
            if (outputBitmap !== cropped) outputBitmap.recycle()
            if (cropped !== source) cropped.recycle()
            source.recycle()
        }
    }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_DECODE_DIMENSION) sample *= 2
        return sample
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "صورة رأس المستند أكبر من الحد المسموح." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
