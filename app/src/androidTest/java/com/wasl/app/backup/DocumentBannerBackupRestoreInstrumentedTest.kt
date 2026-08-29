package com.wasl.app.backup

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.data.local.entity.DocumentIdentityEntity
import com.wasl.app.document.AndroidDocumentBannerAssetStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentBannerBackupRestoreInstrumentedTest {
    private lateinit var database: WaslDatabase
    private lateinit var backupService: BackupService
    private lateinit var bannerStore: AndroidDocumentBannerAssetStore
    private lateinit var testFilesDir: File

    @BeforeTest
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testFilesDir = File(baseContext.cacheDir, "banner-backup-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testFilesDir
        }
        database = Room.inMemoryDatabaseBuilder(baseContext, WaslDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupService = AndroidBackupService(
            context = isolatedContext,
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC),
        )
        bannerStore = AndroidDocumentBannerAssetStore(isolatedContext)
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testFilesDir.isInitialized) testFilesDir.deleteRecursively()
    }

    @Test
    fun encryptedBackupRestoresIdentityBannerBytesAndReference() = runTest {
        val bytes = validPng(Color.rgb(8, 127, 114))
        val asset = bannerStore.importImage(ByteArrayInputStream(bytes))
        database.documentIdentityDao().insert(
            identity(asset.relativePath, asset.sha256),
        )

        val password = "banner-backup-secret".toCharArray()
        val backup = try {
            backupService.create(password)
        } finally {
            password.fill('\u0000')
        }
        assertEquals(12, backup.schemaVersion)
        assertEquals(1, backup.documentCount)

        database.openHelper.writableDatabase.execSQL("DELETE FROM document_identities")
        File(testFilesDir, asset.relativePath).delete()

        val restorePassword = "banner-backup-secret".toCharArray()
        val restored = try {
            backupService.restore(backup.bytes, restorePassword)
        } finally {
            restorePassword.fill('\u0000')
        }
        assertEquals(12, restored.schemaVersion)
        assertEquals(1, restored.documentCount)

        val identity = assertNotNull(database.documentIdentityDao().findById(IDENTITY_ID))
        assertEquals(asset.relativePath, identity.bannerRelativePath)
        assertEquals(asset.sha256, identity.bannerSha256)
        assertContentEquals(bytes, bannerStore.readVerified(asset))
    }

    @Test
    fun createRejectsTamperedLiveBannerBeforeSealingBackup() = runTest {
        val asset = bannerStore.importImage(ByteArrayInputStream(validPng(Color.rgb(212, 176, 94))))
        database.documentIdentityDao().insert(identity(asset.relativePath, asset.sha256))
        File(testFilesDir, asset.relativePath).writeBytes("tampered-banner".toByteArray())

        val password = "banner-backup-secret".toCharArray()
        try {
            assertFailsWith<IllegalArgumentException> {
                backupService.create(password)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun identity(relativePath: String, sha256: String) = DocumentIdentityEntity(
        id = IDENTITY_ID,
        displayName = "هوية اختبار البانر",
        activityName = "متجر",
        phone = null,
        footerText = "شكرًا لتعاملكم",
        isDefault = true,
        createdAt = 1_777_000_000_000L,
        updatedAt = 1_777_000_000_000L,
        bannerRelativePath = relativePath,
        bannerSha256 = sha256,
    )

    private fun validPng(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(12, 4, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val IDENTITY_ID = "banner-backup-identity"
    }
}
