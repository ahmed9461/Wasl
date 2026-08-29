package com.wasl.app.document

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test

class DocumentBannerAssetStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun importThenRead_roundTripsVerifiedImage() {
        val store = AndroidDocumentBannerAssetStore(context)
        val bytes = onePixelPng()
        val asset = store.importImage(ByteArrayInputStream(bytes))

        assertEquals(DocumentBannerAsset.sha256(bytes), asset.sha256)
        assertArrayEquals(bytes, store.readVerified(asset))
    }

    @Test
    fun import_rejectsNonImageBytes() {
        val store = AndroidDocumentBannerAssetStore(context)
        assertFailsWith<IllegalArgumentException> {
  store.importImage(ByteArrayInputStream("not an image".toByteArray()))
        }
    }

    private fun onePixelPng(): ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9WlZf1cAAAAASUVORK5CYII=",
    )
}
