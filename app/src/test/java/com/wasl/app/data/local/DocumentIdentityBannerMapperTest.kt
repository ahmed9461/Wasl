package com.wasl.app.data.local

import com.wasl.app.data.local.entity.DocumentIdentityEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentIdentityBannerMapperTest {
    @Test
    fun `identity without banner returns null`() {
        assertNull(identity().bannerAssetOrNull())
    }

    @Test
    fun `complete banner metadata becomes validated immutable asset`() {
        val asset = identity(
            bannerRelativePath = "document-banners/default.png",
            bannerSha256 = VALID_SHA,
        ).bannerAssetOrNull()

        requireNotNull(asset)
        assertEquals("document-banners/default.png", asset.relativePath)
        assertEquals(VALID_SHA, asset.sha256)
    }

    @Test
    fun `half populated banner metadata fails closed`() {
        assertThrows(IllegalStateException::class.java) {
            identity(bannerRelativePath = "document-banners/default.png").bannerAssetOrNull()
        }
        assertThrows(IllegalStateException::class.java) {
            identity(bannerSha256 = VALID_SHA).bannerAssetOrNull()
        }
    }

    @Test
    fun `unsafe persisted banner metadata is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            identity(
                bannerRelativePath = "../default.png",
                bannerSha256 = VALID_SHA,
            ).bannerAssetOrNull()
        }
        assertThrows(IllegalArgumentException::class.java) {
            identity(
                bannerRelativePath = "document-banners/default.png",
                bannerSha256 = "ABC",
            ).bannerAssetOrNull()
        }
    }

    private fun identity(
        bannerRelativePath: String? = null,
        bannerSha256: String? = null,
    ) = DocumentIdentityEntity(
        id = "identity-default",
        displayName = "متجر وصل",
        activityName = null,
        phone = null,
        footerText = null,
        isDefault = true,
        createdAt = 1L,
        updatedAt = 1L,
        bannerRelativePath = bannerRelativePath,
        bannerSha256 = bannerSha256,
    )

    private companion object {
        const val VALID_SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
