package com.wasl.app.document

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

interface DocumentBannerAssetStore {
    fun importImage(content: InputStream): DocumentBannerAsset
    fun readVerified(asset: DocumentBannerAsset): ByteArray
}

object UnavailableDocumentBannerAssetStore : DocumentBannerAssetStore {
    override fun importImage(content: InputStream): DocumentBannerAsset =
        error("Document banner asset store is unavailable.")

    override fun readVerified(asset: DocumentBannerAsset): ByteArray =
        error("Document banner asset store is unavailable.")
}

class AndroidDocumentBannerAssetStore(context: Context) : DocumentBannerAssetStore {
    private val filesDir = context.applicationContext.filesDir.canonicalFile
    private val root = File(filesDir, DIRECTORY).apply { mkdirs() }.canonicalFile

    override fun importImage(content: InputStream): DocumentBannerAsset {
        val bytes = content.readBounded(MAX_BANNER_BYTES)
        require(bytes.isNotEmpty()) { "صورة رأس المستند فارغة." }
        require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
  "الملف المختار ليس صورة صالحة."
        }
        val hash = DocumentBannerAsset.sha256(bytes)
        val asset = DocumentBannerAsset(
  relativePath = "$DIRECTORY/$hash.img",
  sha256 = hash,
        )
        val target = resolve(asset.relativePath)
        if (target.exists()) {
  require(target.isFile && asset.matches(target.readBytes())) {
      "ملف صورة الرأس المحفوظ لا يجتاز فحص السلامة."
  }
  return asset
        }

        val temporary = File(root, ".$hash.${UUID.randomUUID()}.tmp")
        try {
  temporary.outputStream().buffered().use { it.write(bytes) }
  require(asset.matches(temporary.readBytes())) { "تعذر التحقق من صورة الرأس قبل الحفظ." }
  moveAtomically(temporary, target)
        } finally {
  if (temporary.exists()) temporary.delete()
        }
        return asset
    }

    override fun readVerified(asset: DocumentBannerAsset): ByteArray {
        val target = resolve(asset.relativePath)
        require(target.isFile) { "صورة رأس المستند غير موجودة." }
        require(target.length() in 1..MAX_BANNER_BYTES.toLong()) { "حجم صورة رأس المستند غير صالح." }
        val bytes = target.readBytes()
        require(asset.matches(bytes)) { "فشل فحص سلامة صورة رأس المستند." }
        require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
  "صورة رأس المستند المخزنة غير قابلة للقراءة."
        }
        return bytes
    }

    private fun resolve(relativePath: String): File {
        require(relativePath.startsWith("$DIRECTORY/")) { "مسار صورة الرأس خارج الخزنة المسموحة." }
        require(relativePath.count { it == '/' } == 1) { "مسار صورة الرأس غير صالح." }
        val fileName = relativePath.substringAfter('/')
        require(fileName.matches(Regex("[0-9a-f]{64}\\.img"))) { "اسم ملف صورة الرأس غير صالح." }
        val target = File(root, fileName).canonicalFile
        require(target.parentFile == root) { "مسار صورة الرأس غير آمن." }
        return target
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
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

    private fun moveAtomically(source: File, target: File) {
        try {
  Files.move(
      source.toPath(),
      target.toPath(),
      StandardCopyOption.ATOMIC_MOVE,
  )
        } catch (_: AtomicMoveNotSupportedException) {
  Files.move(source.toPath(), target.toPath())
        }
    }

    companion object {
        const val DIRECTORY = "document-banners"
        const val MAX_BANNER_BYTES = 8 * 1024 * 1024
    }
}
