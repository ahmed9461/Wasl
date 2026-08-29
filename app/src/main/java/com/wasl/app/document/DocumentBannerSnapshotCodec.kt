package com.wasl.app.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stable JSON codec for the immutable banner reference embedded in issued-document snapshots.
 *
 * Decoding always recreates [DocumentBannerAsset], so path and digest validation cannot be
 * bypassed by backup/restore or by reading an older persisted snapshot.
 */
internal object DocumentBannerSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(asset: DocumentBannerAsset): String =
        json.encodeToString(Payload.from(asset))

    fun decode(value: String): DocumentBannerAsset =
        json.decodeFromString<Payload>(value).toAsset()

    @Serializable
    private data class Payload(
        val relativePath: String,
        val sha256: String,
    ) {
        fun toAsset(): DocumentBannerAsset = DocumentBannerAsset(
            relativePath = relativePath,
            sha256 = sha256,
        )

        companion object {
            fun from(asset: DocumentBannerAsset): Payload = Payload(
                relativePath = asset.relativePath,
                sha256 = asset.sha256,
            )
        }
    }
}
