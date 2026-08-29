package com.wasl.app.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentBannerCropperInstrumentedTest {
    @Test
    fun cropProducesHeaderRatioAndHonorsVerticalFocus() {
        val source = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, 1200f, 300f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(0f, 300f, 1200f, 600f, paint)
        val sourceBytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        source.recycle()

        val topBytes = DocumentBannerCropper.cropToHeader(sourceBytes, focusX = 0.5f, focusY = 0f)
        val bottomBytes = DocumentBannerCropper.cropToHeader(sourceBytes, focusX = 0.5f, focusY = 1f)
        val top = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(topBytes, 0, topBytes.size))
        val bottom = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bottomBytes, 0, bottomBytes.size))

        try {
            val topRatio = top.width.toFloat() / top.height.toFloat()
            val bottomRatio = bottom.width.toFloat() / bottom.height.toFloat()
            assertTrue(abs(topRatio - DocumentBannerCropper.HEADER_ASPECT_RATIO) < 0.05f)
            assertTrue(abs(bottomRatio - DocumentBannerCropper.HEADER_ASPECT_RATIO) < 0.05f)
            assertEquals(Color.RED, top.getPixel(top.width / 2, top.height / 2))
            assertEquals(Color.BLUE, bottom.getPixel(bottom.width / 2, bottom.height / 2))
        } finally {
            top.recycle()
            bottom.recycle()
        }
    }

    @Test
    fun previewDecodeDownsamplesLargeCandidateBeforeComposeUsesIt() {
        val source = Bitmap.createBitmap(3200, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val sourceBytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        source.recycle()

        val preview = requireNotNull(DocumentBannerCropper.decodePreview(sourceBytes))
        try {
            assertTrue(maxOf(preview.width, preview.height) <= 1600)
            assertTrue(preview.width > 0 && preview.height > 0)
        } finally {
            preview.recycle()
        }
    }

    @Test
    fun tinyImageStillProducesAReadableBannerInsteadOfZeroSizedCrop() {
        val source = Bitmap.createBitmap(1, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val sourceBytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        source.recycle()

        val croppedBytes = DocumentBannerCropper.cropToHeader(sourceBytes, focusX = 0.5f, focusY = 0.5f)
        val cropped = requireNotNull(
            android.graphics.BitmapFactory.decodeByteArray(croppedBytes, 0, croppedBytes.size),
        )
        try {
            assertTrue(cropped.width >= 1)
            assertTrue(cropped.height >= 1)
        } finally {
            cropped.recycle()
        }
    }

    @Test
    fun candidateReadRejectsOversizedInputBeforeDecode() {
        val oversized = ByteArray(AndroidDocumentBannerAssetStore.MAX_BANNER_BYTES + 1)
        assertFailsWith<IllegalArgumentException> {
            DocumentBannerCropper.readCandidate(ByteArrayInputStream(oversized))
        }
    }
}
