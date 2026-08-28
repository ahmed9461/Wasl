package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AccountStatementSnapshot
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDocumentStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var store: RoomAccountDocumentStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-account-documents-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun debtReceiptAndStatementKeepImmutableSnapshotsWithoutCreatingLedgerEntries() = runTest {
        val debtId = DebtId("account-document-debt")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("account-document-person"),
                debtId = debtId,
                personName = "عميل المستندات",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = Instant.parse("2026-08-25T09:00:00Z"),
                createdAt = Instant.parse("2026-08-25T09:00:00Z"),
                description = "حساب توثيق المستندات",
            ),
        )
        repository.recordPayment(
            paymentCommand("payment-1", "payment-command-1", 20_000L, "2026-08-25T10:00:00Z"),
        )

        val debtReceipt = store.prepareDebtReceipt(
            PrepareDebtReceiptCommand(
                commandId = "debt-receipt-command",
                documentId = "debt-receipt-document",
                identityId = "account-document-identity",
                debtId = debtId,
                issuerDisplayName = "متجر وَصل",
                issuerActivityName = "تجارة عامة",
                issuerPhone = "+967 777 000 000",
                footerText = "نسخة تاريخية",
                issuedAt = Instant.parse("2026-08-25T10:05:00Z"),
                issueZoneId = ZoneId.of("Asia/Aden"),
            ),
        )
        assertEquals("DEBT-2026-00001", debtReceipt.documentNumber)
        assertNull(debtReceipt.ledgerEntryId)
        assertEquals(DocumentStatus.PENDING_PDF, debtReceipt.status)
        val debtSnapshot = debtReceipt.snapshot as DebtReceiptSnapshot
        assertEquals(Money(80_000L, CurrencyCode.YER), debtSnapshot.balanceAtIssue)
        assertEquals(Money(20_000L, CurrencyCode.YER), debtSnapshot.paidAmountAtIssue)

        repository.recordPayment(
            paymentCommand("payment-2", "payment-command-2", 5_000L, "2026-08-25T11:00:00Z"),
        )
        val statement = store.prepareAccountStatement(
            PrepareAccountStatementCommand(
                commandId = "statement-command",
                documentId = "statement-document",
                identityId = "account-document-identity",
                debtId = debtId,
                issuerDisplayName = "متجر وَصل الجديد",
                issuerActivityName = "تجارة عامة",
                issuerPhone = "+967 777 000 000",
                footerText = "كشف تاريخي",
                issuedAt = Instant.parse("2026-08-25T11:05:00Z"),
                issueZoneId = ZoneId.of("Asia/Aden"),
            ),
        )
        assertEquals("STAT-2026-00002", statement.documentNumber)
        assertNull(statement.ledgerEntryId)
        val statementSnapshot = statement.snapshot as AccountStatementSnapshot
        assertEquals(Money(75_000L, CurrencyCode.YER), statementSnapshot.balanceAtIssue)
        assertEquals(Money(25_000L, CurrencyCode.YER), statementSnapshot.paidAmountAtIssue)
        assertEquals(2, statementSnapshot.entries.size)

        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM ledger_entries").use {
            check(it.moveToFirst())
            assertEquals(2L, it.getLong(0))
        }

        reopenDatabase()
        val historicalDebtReceipt = assertNotNull(store.getAccountDocument("debt-receipt-document"))
        val historicalSnapshot = historicalDebtReceipt.snapshot as DebtReceiptSnapshot
        assertEquals(Money(80_000L, CurrencyCode.YER), historicalSnapshot.balanceAtIssue)
        assertEquals("متجر وَصل", historicalSnapshot.identity.displayName)

        val historicalStatement = assertNotNull(store.getAccountDocument("statement-document"))
        assertEquals(2, (historicalStatement.snapshot as AccountStatementSnapshot).entries.size)
    }

    private fun paymentCommand(
        paymentId: String,
        commandId: String,
        amount: Long,
        timestamp: String,
    ) = RecordPaymentCommand(
        commandId = commandId,
        entryId = LedgerEntryId(paymentId),
        debtId = DebtId("account-document-debt"),
        amount = Money(amount, CurrencyCode.YER),
        paidAt = Instant.parse(timestamp),
        recordedAt = Instant.parse(timestamp),
        note = "دفعة مستند",
    )

    private fun openDatabase() {
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        store = RoomAccountDocumentStore(database, repository)
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }
}
