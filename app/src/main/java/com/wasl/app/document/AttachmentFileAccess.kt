package com.wasl.app.document

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import java.io.File

object AttachmentFileAccess {
    fun contentUri(context: Context, attachment: AttachmentRecord): Uri {
        require(attachment.integrity == AttachmentIntegrity.OK) { "Attachment integrity check failed" }
        val file = resolve(context, attachment.relativePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
            attachment.displayName,
        )
    }

    fun open(context: Context, attachment: AttachmentRecord) {
        val uri = contentUri(context, attachment)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            clipData = ClipData.newRawUri(attachment.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "فتح المرفق"))
    }

    fun share(context: Context, attachment: AttachmentRecord) {
        val uri = contentUri(context, attachment)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = attachment.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(attachment.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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
