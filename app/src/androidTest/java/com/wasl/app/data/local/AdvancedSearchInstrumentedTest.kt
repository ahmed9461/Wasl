package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AdvancedSearchResultType
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PreparePaymentReceiptCommand
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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedSearchInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var searchStore: RoomAdvancedSearchStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-advanced-search-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        searchStore = RoomAdvancedSearchStore(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun searchesDebtOperationsDocumentsAmountsDatesAndLiteralLikeCharacters() = runBlocking {
        val zoneId = ZoneId.of("Asia/Aden")
        val openedAt = Instant.parse("2026-08-13T09:00:00Z")
        repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("person-advanced-search"),
                debtId = DebtId("debt-advanced-search"),
                personName = "أحمد",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = openedAt,
                createdAt = openedAt,
                description = "إيجار متجر",
            ),
        )
        val paymentId = LedgerEntryId("payment-advanced-search")
        repository.recordPayment(
            RecordPaymentCommand(
                commandId = "command-payment-advanced-search",
                entryId = paymentId,
                debtId = DebtId("debt-advanced-search"),
                amount = Money(20_000L, CurrencyCode.YER),
                paidAt = Instant.parse("2026-08-13T10:00:00Z"),
                recordedAt = Instant.parse("2026-08-13T10:00:00Z"),
                note = "دفعة متجر 100%_\\ اختبار",
            ),
        )
        val document = repository.preparePaymentReceipt(
            PreparePaymentReceiptCommand(
                commandId = "command-document-advanced-search",
                documentId = "document-advanced-search",
                identityId = "identity-advanced-search",
                debtId = DebtId("debt-advanced-search"),
                paymentId = paymentId,
                issuerDisplayName = "وَصل",
                issuedAt = Instant.parse("2026-08-14T10:00:00Z"),
                issueZoneId = zoneId,
            ),
        )

        val byOperationText = searchStore.observeAdvancedSearch("دفعة متجر", zoneId, 50).first()
        assertTrue(
            byOperationText.any {
                it.id == paymentId.value && it.type == AdvancedSearchResultType.PAYMENT
            },
        )

        val byAmount = searchStore.observeAdvancedSearch("20000 YER", zoneId, 50).first()
        assertTrue(byAmount.any { it.id == paymentId.value })
        assertTrue(byAmount.any { it.id == document.id })
        assertTrue(byAmount.all { it.amount.currency == CurrencyCode.YER })

        val byDocumentNumber = searchStore.observeAdvancedSearch(
            document.documentNumber,
            zoneId,
            50,
        ).first()
        assertEquals(listOf(document.id), byDocumentNumber.map { it.id })
        assertEquals(AdvancedSearchResultType.DOCUMENT, byDocumentNumber.single().type)

        val byDate = searchStore.observeAdvancedSearch("13/08/2026", zoneId, 50).first()
        assertTrue(byDate.any { it.type == AdvancedSearchResultType.DEBT })
        assertTrue(byDate.any { it.id == paymentId.value })

        val literalLikeText = searchStore.observeAdvancedSearch("100%_\\", zoneId, 50).first()
        assertTrue(literalLikeText.any { it.id == paymentId.value })

        val originalAmount = searchStore.observeAdvancedSearch("100000 YER", zoneId, 50).first()
        assertEquals(
            listOf("debt-advanced-search"),
            originalAmount.filter { it.type == AdvancedSearchResultType.DEBT }.map { it.id },
        )
    }
}
