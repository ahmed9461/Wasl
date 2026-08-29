package com.wasl.app.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentBannerAssetTest {
    @Test
    fun computesStableSha256AndVerifiesOriginalBytes() {
        val bytes = "hello".encodeToByteArray()
        val asset = DocumentBannerAsset.fromBytes("document-banners/banner.png", bytes)

        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            asset.sha256,
        )
        assertTrue(asset.matches(bytes))
        assertFalse(asset.matches("tampered".encodeToByteArray()))
    }

    @Test
    fun rejectsAbsoluteOrTraversalPaths() {
        listOf(
            "/data/user/0/com.wasl.app/files/banner.png",
            "\\data\\banner.png",
            "C:/banner.png",
            "C:\\banner.png",
            "../banner.png",
            "document-banners/../banner.png",
            "document-banners//banner.png",
            "./banner.png",
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) {
                DocumentBannerAsset(path, "0".repeat(64))
            }
        }
    }

    @Test
    fun rejectsMalformedDigest() {
        listOf(
            "",
            "0".repeat(63),
            "0".repeat(65),
            "A".repeat(64),
            "z".repeat(64),
        ).forEach { digest ->
            assertFailsWith<IllegalArgumentException>(digest) {
                DocumentBannerAsset("document-banners/banner.png", digest)
            }
        }
    }

    @Test
    fun acceptsNestedSafeRelativePath() {
        val asset = DocumentBannerAsset(
            relativePath = "document-banners/identity-1/banner.png",
            sha256 = "a".repeat(64),
        )

        assertEquals("document-banners/identity-1/banner.png", asset.relativePath)
    }
}
