package com.wasl.app.document

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import java.io.File

object AttachmentFileAccess {
    fun open(context: Context, attachment: AttachmentRecord) {
        require(attachment.integrity == AttachmentIntegrity.OK) { "Attachment integrity check failed" }
        val file = resolve(context, attachment.relativePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "فتح المرفق"))
    }

    fun share(context: Context, attachment: AttachmentRecord) {
        require(attachment.integrity == AttachmentIntegrity.OK) { "Attachment integrity check failed" }
        val file = resolve(context, attachment.relativePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(attachment.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "مشاركة المرفق"))
    }

    private fun resolve(context: Context, relativePath: String): File {
        require(relativePath.startsWith("attachments/")) { "Unsafe attachment path" }
        val root = File(context.filesDir, "attachments").canonicalFile
        val file = File(context.filesDir, relativePath).canonicalFile
        require(file.parentFile == root) { "Unsafe attachment path" }
        require(file.isFile) { "Attachment file is missing" }
        return file
    }
}
