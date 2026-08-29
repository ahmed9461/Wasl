package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentIdentityRecord
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.local.RoomAttachmentStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.DocumentBannerAsset
import com.wasl.app.document.PaymentReceiptService
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentIssuePreviewFlowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var attachmentStore: RoomAttachmentStore
    private val debtId = DebtId("document-preview-debt")

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-document-preview-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        attachmentStore = RoomAttachmentStore(context, database)
        kotlinx.coroutines.runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("document-preview-person"),
                    debtId = debtId,
                    personName = "عميل المعاينة",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(25_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-29T12:00:00Z"),
                    createdAt = Instant.parse("2026-08-29T12:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun documentIsNotIssuedUntilPreviewIsExplicitlyConfirmed() {
        val service = RecordingDocumentIssueService()
        composeRule.setContent {
            DocumentsHubRoute(
                repository = repository,
                documentService = service,
                attachmentStore = attachmentStore,
                initialDebtId = debtId,
                onBack = {},
            )
        }

        waitForTag("documents-issuer-name")
        composeRule.onNodeWithTag("documents-issuer-name").performTextInput("مؤسسة الاختبار")
        scrollToTag("account-documents-${debtId.value}-primary")
        composeRule.onNodeWithTag("account-documents-${debtId.value}-primary").performClick()
        waitForTag("document-issue-preview-card")

        composeRule.runOnIdle { assertEquals(0, service.debtIssueCalls) }
        composeRule.onNodeWithTag("document-issue-confirm").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { service.debtIssueCalls == 1 }
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNode(hasScrollAction()).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }
}

private class RecordingDocumentIssueService : PaymentReceiptService {
    @Volatile
    var debtIssueCalls: Int = 0

    override suspend fun getDefaultIdentity(): DocumentIdentityRecord? = null

    override suspend fun importIdentityBanner(content: InputStream): DocumentBannerAsset =
        error("Not used in preview flow test.")

    override suspend fun issue(command: PreparePaymentReceiptCommand): IssuedDocumentRecord =
        error("Not used in preview flow test.")

    override suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord {
        debtIssueCalls += 1
        error("Intentional stop after confirming issue call.")
    }

    override suspend fun issueAccountStatement(command: PrepareAccountStatementCommand): IssuedDocumentRecord =
        error("Not used in preview flow test.")

    override suspend fun retry(documentId: String): IssuedDocumentRecord =
        error("Not used in preview flow test.")
}
