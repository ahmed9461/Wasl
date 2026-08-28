package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentTemplateCatalog
import com.wasl.app.data.DocumentTemplateStyle
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTemplateRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-document-template-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshDatabaseSeedsFiveTemplatesAndBusinessIsDefault() = kotlinx.coroutines.runBlocking {
        val templates = repository.getDocumentTemplates()
        assertEquals(5, templates.size)
        assertEquals(5, templates.map { it.id }.distinct().size)
        val default = requireNotNull(repository.getDefaultDocumentTemplate())
        assertEquals(DocumentTemplateCatalog.DEFAULT_TEMPLATE_ID, default.id)
        assertEquals(DocumentTemplateStyle.BUSINESS, default.style)
        assertTrue(default.isDefault)
    }

    @Test
    fun issuedReceiptKeepsTemplateSnapshotWhenLiveTemplateRowChanges() = kotlinx.coroutines.runBlocking {
        val openedAt = Instant.parse("2026-08-28T10:00:00Z")
        val personId = PersonId("template-person")
        val debtId = DebtId("template-debt")
        val paymentId = LedgerEntryId("template-payment")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = personId,
                debtId = debtId,
                personName = "عميل القالب",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000, CurrencyCode.of("YER")),
                openedAt = openedAt,
                createdAt = openedAt,
            ),
        )
        repository.recordPayment(
            RecordPaymentCommand(
                commandId = "template-payment-command",
                entryId = paymentId,
                debtId = debtId,
                amount = Money(20_000, CurrencyCode.of("YER")),
                paidAt = openedAt.plusSeconds(60),
                recordedAt = openedAt.plusSeconds(60),
            ),
        )
        val issued = repository.preparePaymentReceipt(
            PreparePaymentReceiptCommand(
                commandId = "template-document-command",
                documentId = "template-document",
                identityId = "template-identity",
                debtId = debtId,
                paymentId = paymentId,
                issuerDisplayName = "وَصل",
                issuedAt = openedAt.plusSeconds(120),
                issueZoneId = ZoneId.of("Asia/Aden"),
                templateId = DocumentTemplateCatalog.MINIMAL_ID,
            ),
        )
        val snapshot = issued.snapshot as PaymentReceiptSnapshot
        assertEquals(DocumentTemplateStyle.MINIMAL, snapshot.template.style)
        assertEquals("بسيط", snapshot.template.displayName)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE document_templates SET display_name = 'متغير' WHERE id = ?",
            arrayOf(DocumentTemplateCatalog.MINIMAL_ID),
        )
        val reread = requireNotNull(repository.getIssuedDocument(issued.id)).snapshot as PaymentReceiptSnapshot
        assertEquals("بسيط", reread.template.displayName)
        assertEquals(DocumentTemplateStyle.MINIMAL, reread.template.style)
    }
}
