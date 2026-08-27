package com.wasl.app.document

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import com.wasl.domain.DebtId
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentFileAccessInstrumentedTest {
    private val baseContext: Context = ApplicationProvider.getApplicationContext()
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        createdFiles.forEach(File::delete)
    }

    @Test
    fun openAndShareUseReadOnlyFileProviderUrisInsideAttachmentVault() {
        val attachment = createAttachment()
        val context = CapturingContext(baseContext)

        AttachmentFileAccess.open(context, attachment)
        val openTarget = chooserTarget(assertNotNull(context.startedIntent))
        assertEquals(Intent.ACTION_VIEW, openTarget.action)
        assertSecureAttachmentUri(assertNotNull(openTarget.data))
        assertTrue(openTarget.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(openTarget.clipData)

        context.startedIntent = null
        AttachmentFileAccess.share(context, attachment)
        val shareTarget = chooserTarget(assertNotNull(context.startedIntent))
        assertEquals(Intent.ACTION_SEND, shareTarget.action)
        val sharedUri = shareTarget.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertSecureAttachmentUri(assertNotNull(sharedUri))
        assertTrue(shareTarget.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(shareTarget.clipData)
    }

    @Test
    fun corruptOrUnsafeAttachmentCannotBeOpenedOrShared() {
        val valid = createAttachment()
        val context = CapturingContext(baseContext)

        assertFailsWith<IllegalArgumentException> {
            AttachmentFileAccess.open(
                context,
                valid.copy(integrity = AttachmentIntegrity.HASH_MISMATCH),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AttachmentFileAccess.share(
                context,
                valid.copy(relativePath = "../outside.blob"),
            )
        }
        assertEquals(null, context.startedIntent)
    }

    private fun createAttachment(): AttachmentRecord {
        val id = "file-access-${UUID.randomUUID()}"
        val root = File(baseContext.filesDir, "attachments").apply { mkdirs() }
        val file = File(root, "$id.blob").apply { writeText("wasl attachment") }
        createdFiles += file
        return AttachmentRecord(
            id = id,
            debtId = DebtId("file-access-debt"),
            ledgerEntryId = null,
            displayName = "proof.txt",
            mimeType = "text/plain",
            sizeBytes = file.length(),
            relativePath = "attachments/${file.name}",
            sha256 = "0".repeat(64),
            createdAt = Instant.parse("2026-08-27T12:00:00Z"),
            note = null,
            integrity = AttachmentIntegrity.OK,
        )
    }

    private fun chooserTarget(chooser: Intent): Intent {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        return assertNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
    }

    private fun assertSecureAttachmentUri(uri: Uri) {
        assertEquals("content", uri.scheme)
        assertEquals("${baseContext.packageName}.fileprovider", uri.authority)
        assertTrue(uri.path.orEmpty().contains("attachments"))
    }

    private class CapturingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }
}
