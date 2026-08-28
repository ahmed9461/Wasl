package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AddAttachmentCommand
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
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentVaultUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var attachmentStore: RoomAttachmentStore
    private val debtId = DebtId("attachment-vault-ui-debt")
    private val createdIds = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-attachment-vault-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        attachmentStore = RoomAttachmentStore(context, database)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("attachment-vault-ui-person"),
                    debtId = debtId,
                    personName = "حساب خزنة المرفقات",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(80_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-27T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-27T08:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        createdIds.forEach { id -> File(context.filesDir, "attachments/$id.blob").delete() }
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun accountDocumentsExposePrivateAttachmentVaultAndHealthyFile() {
        val attachmentId = "attachment-${UUID.randomUUID()}".also(createdIds::add)
        runBlocking {
            attachmentStore.importAttachment(
                AddAttachmentCommand(
                    id = attachmentId,
                    debtId = debtId,
                    displayName = "invoice-proof.pdf",
                    mimeType = "application/pdf",
                    createdAt = Instant.parse("2026-08-27T09:00:00Z"),
                ),
                ByteArrayInputStream("%PDF-1.4 wasl proof".encodeToByteArray()),
            )
        }

        setDocumentsContent()
        waitForTag("account-attachments")
        composeRule.onNodeWithTag("account-attachments").assertIsDisplayed()
        composeRule.onNodeWithTag("add-attachment").assertIsDisplayed()
        composeRule.onNodeWithTag("attachment-$attachmentId").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("invoice-proof.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("سلامة الملف: سليمة").assertIsDisplayed()
    }

    @Test
    fun accountDocumentsSurfaceTamperedAttachmentAsCorrupt() {
        val attachmentId = "attachment-${UUID.randomUUID()}".also(createdIds::add)
        val record = runBlocking {
            attachmentStore.importAttachment(
                AddAttachmentCommand(
                    id = attachmentId,
                    debtId = debtId,
                    displayName = "tampered.txt",
                    mimeType = "text/plain",
                    createdAt = Instant.parse("2026-08-27T09:00:00Z"),
                ),
                ByteArrayInputStream("original".encodeToByteArray()),
            )
        }
        File(context.filesDir, record.relativePath).writeText("changed after import")

        setDocumentsContent()
        waitForTag("account-attachments")
        composeRule.onNodeWithTag("attachment-$attachmentId").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("سلامة الملف: البصمة لا تطابق المحتوى").assertIsDisplayed()
    }

    private fun setDocumentsContent() {
        composeRule.setContent {
            DocumentsHubRoute(
                repository = repository,
                documentService = UnavailablePaymentReceiptService,
                attachmentStore = attachmentStore,
                initialDebtId = debtId,
                onBack = {},
            )
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }
}
