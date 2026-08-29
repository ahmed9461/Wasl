package com.wasl.app.document

import java.security.MessageDigest

/**
 * Immutable integrity metadata for a document-identity banner asset.
 *
 * The path is deliberately stored as a relative application-private path so callers cannot
 * accidentally persist or later open an absolute/file URI. The digest is part of the snapshot
 * contract used to detect asset replacement or tampering before rendering a historical document.
 */
data class DocumentBannerAsset(
    val relativePath: String,
    val sha256: String,
) {
    init {
        require(isSafeRelativePath(relativePath)) { "Banner path must be a safe relative path" }
        require(SHA_256_REGEX.matches(sha256)) { "Banner SHA-256 must be lowercase hexadecimal" }
    }

    fun matches(bytes: ByteArray): Boolean = sha256 == sha256(bytes)

    companion object {
        private val SHA_256_REGEX = Regex("^[0-9a-f]{64}$")
        private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\].*")

        fun fromBytes(relativePath: String, bytes: ByteArray): DocumentBannerAsset =
            DocumentBannerAsset(
                relativePath = relativePath,
                sha256 = sha256(bytes),
            )

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }

        private fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank()) return false
            if (path.startsWith('/') || path.startsWith('\\')) return false
            if (WINDOWS_ABSOLUTE_PATH.matches(path)) return false
            if ('\\' in path) return false

            val segments = path.split('/')
            return segments.none { segment ->
                segment.isBlank() || segment == "." || segment == ".."
            }
        }
    }
}
