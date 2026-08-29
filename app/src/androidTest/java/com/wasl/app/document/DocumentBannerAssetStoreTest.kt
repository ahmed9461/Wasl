package com.wasl.app.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DocumentBannerAssetStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun importThenRead_roundTripsVerifiedImage() {
        val store = AndroidDocumentBannerAssetStore(context)
        val bytes = validPng()
        val asset = store.importImage(ByteArrayInputStream(bytes))

        assertEquals(DocumentBannerAsset.sha256(bytes), asset.sha256)
        assertArrayEquals(bytes, store.readVerified(asset))
    }

    @Test
    fun import_rejectsNonImageBytes() {
        val store = AndroidDocumentBannerAssetStore(context)
        try {
            store.importImage(ByteArrayInputStream("not an image".toByteArray()))
            fail("Expected invalid image bytes to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun validPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(8, 127, 114))
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
