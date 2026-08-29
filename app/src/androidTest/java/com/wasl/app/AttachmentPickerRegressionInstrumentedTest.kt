package com.wasl.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.local.RoomAttachmentStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.UnavailablePaymentReceiptService
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentPickerRegressionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var attachmentStore: RoomAttachmentStore
    private lateinit var registry: ControlledActivityResultRegistry
    private val debtId = DebtId("attachment-picker-regression-debt")
    private val sourceFiles = mutableListOf<File>()

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-attachment-picker-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        attachmentStore = RoomAttachmentStore(context, database)
        registry = ControlledActivityResultRegistry()

        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("attachment-picker-regression-person"),
                    debtId = debtId,
                    personName = "اختبار منتقي المرفقات",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(50_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-29T10:00:00Z"),
                    createdAt = Instant.parse("2026-08-29T10:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        if (::attachmentStore.isInitialized) {
            runBlocking {
                attachmentStore.observeForDebt(debtId).first().forEach { record ->
                    File(context.filesDir, record.relativePath).delete()
                }
            }
        }
        sourceFiles.forEach(File::delete)
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun cancellingPickerDoesNotImportOrCrash() {
        registry.nextResult = null
        setDocumentsContent()

        composeRule.onNodeWithTag("add-attachment").performClick()
        composeRule.waitForIdle()

        assertEquals(1, registry.launchCount)
        assertEquals(0, attachmentCount())
        composeRule.onNodeWithTag("add-attachment").assertIsDisplayed()
    }

    @Test
    fun selectingImageImportsThroughRealContentUri() {
        val image = createPngSource()
        registry.nextResult = fileProviderUri(image)
        setDocumentsContent()

        composeRule.onNodeWithTag("add-attachment").performClick()
        waitForAttachmentCount(1)

        val record = attachments().single()
        assertEquals(image.name, record.displayName)
        assertEquals("image/png", record.mimeType)
        assertTrue(File(context.filesDir, record.relativePath).isFile)
        composeRule.onNodeWithText(image.name).assertIsDisplayed()
    }

    @Test
    fun selectingPdfImportsThroughRealContentUri() {
        val pdf = createPdfSource()
        registry.nextResult = fileProviderUri(pdf)
        setDocumentsContent()

        composeRule.onNodeWithTag("add-attachment").performClick()
        waitForAttachmentCount(1)

        val record = attachments().single()
        assertEquals(pdf.name, record.displayName)
        assertEquals("application/pdf", record.mimeType)
        assertTrue(File(context.filesDir, record.relativePath).isFile)
        composeRule.onNodeWithText(pdf.name).assertIsDisplayed()
    }

    @Test
    fun unreadableContentUriFailsSafelyWithoutImport() {
        registry.nextResult = Uri.parse("content://com.wasl.app.missing-provider/not-readable.pdf")
        setDocumentsContent()

        composeRule.onNodeWithTag("add-attachment").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("تعذر حفظ المرفق. جرّب ملفًا آخر.").fetchSemanticsNode()
            }.isSuccess
        }

        assertEquals(0, attachmentCount())
        composeRule.onNodeWithTag("add-attachment").assertIsDisplayed()
    }

    private fun setDocumentsContent() {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                DocumentsHubRoute(
                    repository = repository,
                    documentService = UnavailablePaymentReceiptService,
                    attachmentStore = attachmentStore,
                    initialDebtId = debtId,
                    onBack = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("add-attachment").fetchSemanticsNode() }.isSuccess
        }
    }

    private fun createPngSource(): File {
        val file = sourceFile("picker-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }

    private fun createPdfSource(): File = sourceFile("picker-${UUID.randomUUID()}.pdf").apply {
        writeBytes("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".encodeToByteArray())
    }

    private fun sourceFile(name: String): File {
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        return File(directory, name).also(sourceFiles::add)
    }

    private fun fileProviderUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun attachments() = runBlocking { attachmentStore.observeForDebt(debtId).first() }

    private fun attachmentCount(): Int = attachments().size

    private fun waitForAttachmentCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = 10_000) { attachmentCount() == expected }
    }

    private class ControlledActivityResultRegistry : ActivityResultRegistry() {
        var nextResult: Uri? = null
        var launchCount: Int = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launchCount += 1
            dispatchResult(requestCode, nextResult)
        }
    }
}
