package com.wasl.app.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentBannerSnapshotCodecTest {
    @Test
    fun roundTripPreservesImmutableBannerReference() {
        val asset = DocumentBannerAsset.fromBytes(
            relativePath = "document-banners/identity-1/banner.png",
            bytes = "stable-banner".encodeToByteArray(),
        )

        val restored = DocumentBannerSnapshotCodec.decode(
            DocumentBannerSnapshotCodec.encode(asset),
        )

        assertEquals(asset, restored)
    }

    @Test
    fun decodeRejectsTraversalPath() {
        val payload = """{"relativePath":"../banner.png","sha256":"${"0".repeat(64)}"}"""

        assertFailsWith<IllegalArgumentException> {
            DocumentBannerSnapshotCodec.decode(payload)
        }
    }

    @Test
    fun decodeRejectsMalformedDigest() {
        val payload = """{"relativePath":"document-banners/banner.png","sha256":"ABC"}"""

        assertFailsWith<IllegalArgumentException> {
            DocumentBannerSnapshotCodec.decode(payload)
        }
    }

    @Test
    fun decodeRejectsUnknownSnapshotFields() {
        val payload = """{"relativePath":"document-banners/banner.png","sha256":"${"a".repeat(64)}","absolutePath":"/data/banner.png"}"""

        assertFailsWith<Exception> {
            DocumentBannerSnapshotCodec.decode(payload)
        }
    }
}
