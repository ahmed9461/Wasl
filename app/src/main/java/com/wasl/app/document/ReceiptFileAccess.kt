package com.wasl.app.document

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wasl.app.data.IssuedDocumentRecord
import java.io.File

object ReceiptFileAccess {
    fun resolve(filesDir: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Receipt path cannot be blank." }
        require(!File(relativePath).isAbsolute) { "Receipt path must be relative." }
        val documentsRoot = File(filesDir, "documents").canonicalFile
        val resolved = File(filesDir, relativePath).canonicalFile
        require(resolved.parentFile == documentsRoot) {
            "Receipt path must resolve directly inside the documents directory."
        }
        require(resolved.extension.equals("pdf", ignoreCase = true)) {
            "Receipt file must be a PDF."
        }
        return resolved
    }

    fun contentUri(context: Context, document: IssuedDocumentRecord): Uri {
        val file = resolve(context.filesDir, document.pdfRelativePath)
        require(file.isFile) { "Receipt PDF is not available." }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
            "${document.documentNumber}.pdf",
        )
    }

    fun open(context: Context, document: IssuedDocumentRecord) {
        val uri = contentUri(context, document)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, PDF_MIME_TYPE)
                clipData = ClipData.newRawUri(document.documentNumber, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    fun share(context: Context, document: IssuedDocumentRecord) {
        val uri = contentUri(context, document)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "إيصال سداد ${document.documentNumber}")
            clipData = ClipData.newRawUri(document.documentNumber, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة إيصال السداد"))
    }

    private const val PDF_MIME_TYPE = "application/pdf"
}
