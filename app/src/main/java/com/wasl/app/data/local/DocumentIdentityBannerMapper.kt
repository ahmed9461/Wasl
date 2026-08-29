package com.wasl.app.data.local

import com.wasl.app.data.local.entity.DocumentIdentityEntity
import com.wasl.app.document.DocumentBannerAsset

/**
 * Resolves the persisted banner integrity metadata for a document identity.
 *
 * Room v12 stores the path and digest in separate nullable columns for migration compatibility.
 * Treating a half-populated pair as corruption prevents a document from silently falling back to
 * an unverified banner or from losing the integrity binding that will be copied into its snapshot.
 */
internal fun DocumentIdentityEntity.bannerAssetOrNull(): DocumentBannerAsset? {
    if (bannerRelativePath == null && bannerSha256 == null) return null
    check(bannerRelativePath != null && bannerSha256 != null) {
        "Document identity banner metadata is incomplete."
    }
    return DocumentBannerAsset(
        relativePath = bannerRelativePath,
        sha256 = bannerSha256,
    )
}
