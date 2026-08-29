package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.InstallmentAwareWaslRepository
import com.wasl.app.data.PreparePaymentReceiptCommand
import com.wasl.app.data.RecordPaymentCommand
import com.wasl.app.data.local.RoomAdvancedSearchStore
import com.wasl.app.data.local.RoomInstallmentPlanStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedSearchUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var roomRepository: RoomWaslRepository
    private lateinit var repository: InstallmentAwareWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-advanced-search-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        roomRepository = RoomWaslRepository(database)
        repository = InstallmentAwareWaslRepository(
            waslRepository = roomRepository,
            installmentPlanStore = RoomInstallmentPlanStore(database, roomRepository),
            advancedSearchStore = RoomAdvancedSearchStore(database),
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun documentNumberShowsTypedResultAndOpensItsAccount() {
        val documentNumber = runBlocking {
            val openedAt = Instant.parse("2026-08-13T09:00:00Z")
            roomRepository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-advanced-ui"),
                    debtId = DebtId("debt-advanced-ui"),
                    personName = "خالد",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = openedAt,
                    createdAt = openedAt,
                    description = "إيجار مكتب",
                ),
            )
            val paymentId = LedgerEntryId("payment-advanced-ui")
            roomRepository.recordPayment(
                RecordPaymentCommand(
                    commandId = "payment-command-advanced-ui",
                    entryId = paymentId,
                    debtId = DebtId("debt-advanced-ui"),
                    amount = Money(20_000L, CurrencyCode.YER),
                    paidAt = Instant.parse("2026-08-13T10:00:00Z"),
                    recordedAt = Instant.parse("2026-08-13T10:00:00Z"),
                    note = "دفعة بحث متقدم",
                ),
            )
            roomRepository.preparePaymentReceipt(
                PreparePaymentReceiptCommand(
                    commandId = "document-command-advanced-ui",
                    documentId = "document-advanced-ui",
                    identityId = "identity-advanced-ui",
                    debtId = DebtId("debt-advanced-ui"),
                    paymentId = paymentId,
                    issuerDisplayName = "وَصل",
                    issuedAt = Instant.parse("2026-08-14T10:00:00Z"),
                    issueZoneId = ZoneId.of("Asia/Aden"),
                ),
            ).documentNumber
        }

        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "advanced-search-ui",
            )
        }

        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput(documentNumber)
        waitForTag("search-advanced-document-document-advanced-ui")

        composeRule.onNodeWithTag("search-advanced-document-document-advanced-ui")
            .assertIsDisplayed()
            .performScrollTo()
            .performClick()

        waitForText("خالد")
        composeRule.onNodeWithText("خالد").assertIsDisplayed()
        composeRule.onNodeWithTag("account-details-back").performClick()

        waitForTag("search-advanced-document-document-advanced-ui")
        composeRule.onNodeWithTag("search-input").assertTextContains(documentNumber)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithText(text).fetchSemanticsNode() }.isSuccess
        }
    }
}
