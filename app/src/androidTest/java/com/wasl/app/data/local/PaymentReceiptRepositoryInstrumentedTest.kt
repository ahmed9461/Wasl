package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.ReversePaymentCommand
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentReceiptRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-receipt-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun paymentReceiptSnapshotComesFromLedgerAndSurvivesIdentityChangesAndReopen() = runTest {
        repository.createPersonWithDebt(debtCommand())
        repository.recordPayment(paymentCommand("payment-1", "payment-command-1", 20_000L, 1))
        repository.recordPayment(paymentCommand("payment-2", "payment-command-2", 5_000L, 2))

        val firstCommand = receiptCommand(
            paymentId = "payment-1",
            documentId = "document-1",
            commandId = "receipt-command-1",
            identityName = "متجر أحمد",
            minute = 3,
        )
        val first = repository.preparePaymentReceipt(firstCommand)
        val ready = repository.markDocumentReady(
            documentId = first.id,
            pdfSha256 = "a".repeat(64),
            pageCount = 1,
            updatedAt = Instant.parse("2026-08-13T00:04:00Z"),
        )
        assertEquals(DocumentStatus.READY, ready.status)
        assertEquals("PAY-2026-00001", ready.documentNumber)
        assertEquals(PersonId("person-1"), ready.snapshot.personId)
        assertEquals("أحمد", ready.snapshot.personName)
        assertEquals(Money(100_000L, CurrencyCode.YER), ready.snapshot.originalAmount)
        assertEquals(Money(100_000L, CurrencyCode.YER), ready.snapshot.balanceBefore)
        assertEquals(Money(20_000L, CurrencyCode.YER), ready.snapshot.paymentAmount)
        assertEquals(Money(80_000L, CurrencyCode.YER), ready.snapshot.balanceAfter)
        assertEquals(CurrencyCode.YER, ready.snapshot.paymentAmount.currency)
        assertEquals("متجر أحمد", ready.snapshot.identity.displayName)

        val second = repository.preparePaymentReceipt(
            receiptCommand(
                paymentId = "payment-2",
                documentId = "document-2",
                commandId = "receipt-command-2",
                identityName = "مؤسسة أحمد الجديدة",
                minute = 5,
            ).copy(identityId = firstCommand.identityId),
        )
        assertEquals("PAY-2026-00002", second.documentNumber)
        assertEquals(Money(80_000L, CurrencyCode.YER), second.snapshot.balanceBefore)
        assertEquals(Money(5_000L, CurrencyCode.YER), second.snapshot.paymentAmount)
        assertEquals(Money(75_000L, CurrencyCode.YER), second.snapshot.balanceAfter)
        assertEquals("مؤسسة أحمد الجديدة", repository.getDefaultDocumentIdentity()?.displayName)

        reopenDatabase()
        val reopened = assertNotNull(repository.getAccount(DebtId("debt-1")))
        val historical = reopened.issuedDocuments.first { it.id == "document-1" }
        assertEquals("متجر أحمد", historical.snapshot.identity.displayName)
        assertEquals("أحمد", historical.snapshot.personName)
        assertEquals(Money(20_000L, CurrencyCode.YER), historical.snapshot.paymentAmount)
        assertEquals("a".repeat(64), historical.pdfSha256)
    }

    @Test
    fun receiptIsIdempotentByCommandAndPaymentAndRejectsConflictingReplay() = runTest {
        repository.createPersonWithDebt(debtCommand())
        repository.recordPayment(paymentCommand("payment-1", "payment-command-1", 20_000L, 1))
        val command = receiptCommand("payment-1", "document-1", "receipt-command-1", "أحمد", 2)

        val first = repository.preparePaymentReceipt(command)
        assertEquals(first, repository.preparePaymentReceipt(command))
        assertFailsWith<CommandConflictException> {
            repository.preparePaymentReceipt(command.copy(issuerDisplayName = "هوية مختلفة"))
        }

        val duplicateIntent = repository.preparePaymentReceipt(
            command.copy(commandId = "new-command", documentId = "new-document"),
        )
        assertEquals(first.id, duplicateIntent.id)
        assertEquals(1, database.issuedDocumentDao().count())
    }

    @Test
    fun reversedOrForeignPaymentCannotIssueAReceipt() = runTest {
        repository.createPersonWithDebt(debtCommand())
        repository.recordPayment(paymentCommand("payment-1", "payment-command-1", 20_000L, 1))
        repository.reversePayment(
            ReversePaymentCommand(
                commandId = "reverse-command",
                entryId = LedgerEntryId("reversal-1"),
                debtId = DebtId("debt-1"),
                paymentId = LedgerEntryId("payment-1"),
                recordedAt = Instant.parse("2026-08-13T00:02:00Z"),
                reason = "تصحيح موثق",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            repository.preparePaymentReceipt(
                receiptCommand("payment-1", "document-1", "receipt-command-1", "أحمد", 3),
            )
        }
        assertFailsWith<IllegalStateException> {
            repository.preparePaymentReceipt(
                receiptCommand("missing", "document-2", "receipt-command-2", "أحمد", 3),
            )
        }
        assertEquals(0, database.issuedDocumentDao().count())
    }

    @Test
    fun concurrentReceiptsReceiveDistinctAtomicNumbers() = runTest {
        repository.createPersonWithDebt(debtCommand())
        repository.recordPayment(paymentCommand("payment-1", "payment-command-1", 20_000L, 1))
        repository.recordPayment(paymentCommand("payment-2", "payment-command-2", 5_000L, 2))

        val documents = coroutineScope {
            listOf(
                receiptCommand("payment-1", "document-1", "receipt-command-1", "أحمد", 3),
                receiptCommand("payment-2", "document-2", "receipt-command-2", "أحمد", 4),
            ).map { command ->
                async(Dispatchers.IO) { repository.preparePaymentReceipt(command) }
            }.awaitAll()
        }

        assertEquals(2, documents.map { it.documentNumber }.toSet().size)
        assertEquals(setOf("PAY-2026-00001", "PAY-2026-00002"), documents.map { it.documentNumber }.toSet())
        assertTrue(documents.all { it.status == DocumentStatus.PENDING_PDF })
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }

    private fun debtCommand() = CreatePersonWithDebtCommand(
        personId = PersonId("person-1"),
        debtId = DebtId("debt-1"),
        personName = "أحمد",
        direction = DebtDirection.RECEIVABLE,
        originalAmount = Money(100_000L, CurrencyCode.YER),
        openedAt = Instant.parse("2026-08-13T00:00:00Z"),
        createdAt = Instant.parse("2026-08-13T00:00:00Z"),
        description = "دين أجهزة",
    )

    private fun paymentCommand(
        paymentId: String,
        commandId: String,
        amount: Long,
        minute: Int,
    ) = RecordPaymentCommand(
        commandId = commandId,
        entryId = LedgerEntryId(paymentId),
        debtId = DebtId("debt-1"),
        amount = Money(amount, CurrencyCode.YER),
        paidAt = Instant.parse("2026-08-13T00:0${minute}:00Z"),
        recordedAt = Instant.parse("2026-08-13T00:0${minute}:00Z"),
        note = "دفعة رقم $minute",
    )

    private fun receiptCommand(
        paymentId: String,
        documentId: String,
        commandId: String,
        identityName: String,
        minute: Int,
    ) = PreparePaymentReceiptCommand(
        commandId = commandId,
        documentId = documentId,
        identityId = "identity-1",
        debtId = DebtId("debt-1"),
        paymentId = LedgerEntryId(paymentId),
        issuerDisplayName = identityName,
        issuerActivityName = "تجارة عامة",
        issuerPhone = "+967 777 000 000",
        footerText = "شكرًا لتعاملكم معنا",
        issuedAt = Instant.parse("2026-08-13T00:0${minute}:00Z"),
        issueZoneId = ZoneId.of("Asia/Aden"),
    )
}
