package com.wasl.app

import android.content.Context
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AndroidPaymentReceiptService
import com.wasl.app.document.PaymentReceiptService
import com.wasl.domain.PaymentRecorded
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentFlowUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private var database: WaslDatabase? = null

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-ui-test-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @AfterTest
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
        File(context.filesDir, "documents").listFiles()?.forEach { file -> file.delete() }
    }

    @Test
    fun createDebtRecordPaymentReceiptAndReopenFromPersistedData() {
        val initialRepository = RoomWaslRepository(database!!)
        val repositoryState = mutableStateOf<WaslRepository>(initialRepository)
        val receiptServiceState = mutableStateOf<PaymentReceiptService>(
            AndroidPaymentReceiptService(context, initialRepository),
        )
        val generation = mutableIntStateOf(0)
        val requestedDebtIdState = mutableStateOf<String?>(null)
        composeRule.setContent {
            val currentGeneration = generation.intValue
            key(currentGeneration) {
                WaslApp(
                    repository = repositoryState.value,
                    paymentReceiptService = receiptServiceState.value,
                    instanceKey = "ui-test-$currentGeneration",
                    requestedDebtId = requestedDebtIdState.value,
                    onRequestedDebtHandled = { requestedDebtIdState.value = null },
                )
            }
        }

        composeRule.onNodeWithText("إضافة حساب").performClick()
        composeRule.onNodeWithTag("create-person-name").performTextInput("أحمد")
        composeRule.onNodeWithTag("create-debt-amount").performTextInput("100000")
        composeRule.onNodeWithTag("create-debt-save").performClick()
        val debtId = runBlocking {
            withTimeout(10_000) {
                repositoryState.value.observeAccounts()
                    .first { it.isNotEmpty() }
                    .single()
                    .ledger.header.id.value
            }
        }

        waitForTagToDisappear("create-debt-save")
        scrollToTag("account-$debtId")
        composeRule.onNodeWithTag("account-$debtId").performClick()
        waitForTag("record-payment")
        composeRule.onNodeWithTag("record-payment").performClick()
        composeRule.onNodeWithTag("payment-amount").performTextInput("20000")
        composeRule.onNodeWithTag("payment-review").performClick()
        composeRule.onNodeWithTag("payment-confirm").performClick()

        val paymentId = runBlocking {
            withTimeout(10_000) {
                repositoryState.value.observeAccount(com.wasl.domain.DebtId(debtId))
                    .first { it?.ledger?.entries?.filterIsInstance<PaymentRecorded>()?.isNotEmpty() == true }
                    ?.ledger
                    ?.entries
                    ?.filterIsInstance<PaymentRecorded>()
                    ?.single()
                    ?.id
                    ?.value
            }
        } ?: error("Persisted payment was not found.")

        waitForTagText("account-remaining", "80,000 YER")
        composeRule.onNodeWithTag("account-remaining")
            .assertTextContains("80,000 YER", substring = true)
        scrollToText("دفعة مسجلة")
        composeRule.onNodeWithText("دفعة مسجلة").assertIsDisplayed()

        scrollToTag("issue-receipt-$paymentId")
        composeRule.onNodeWithTag("issue-receipt-$paymentId").performClick()
        waitForTag("receipt-issuer-name")
        composeRule.onNodeWithTag("receipt-issuer-name").performTextInput("متجر أحمد")
        composeRule.onNodeWithTag("receipt-confirm").performClick()
        val document = runBlocking {
            withTimeout(10_000) {
                repositoryState.value.observeAccount(com.wasl.domain.DebtId(debtId))
                    .first { account -> account?.issuedDocuments?.singleOrNull()?.pdfSha256 != null }
                    ?.issuedDocuments
                    ?.single()
            }
        } ?: error("Ready payment receipt was not found.")
        scrollToText(document.documentNumber)
        composeRule.onNodeWithText(document.documentNumber).assertIsDisplayed()
        scrollToTag("open-receipt-${document.id}")
        composeRule.onNodeWithTag("open-receipt-${document.id}").assertIsDisplayed()

        composeRule.runOnIdle {
            database!!.close()
            openDatabase()
            val reopenedRepository = RoomWaslRepository(database!!)
            repositoryState.value = reopenedRepository
            receiptServiceState.value = AndroidPaymentReceiptService(context, reopenedRepository)
            generation.intValue += 1
            requestedDebtIdState.value = debtId
        }

        waitForTagText("account-remaining", "80,000 YER")
        composeRule.onNodeWithTag("account-remaining")
            .assertTextContains("80,000 YER", substring = true)
        scrollToText("دفعة مسجلة")
        composeRule.onNodeWithText("دفعة مسجلة").assertIsDisplayed()
        scrollToTag("open-receipt-${document.id}")
        composeRule.onNodeWithTag("open-receipt-${document.id}").assertIsDisplayed()
        scrollToText(document.documentNumber)
        composeRule.onNodeWithText(document.documentNumber).assertIsDisplayed()
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(
            context,
            WaslDatabase::class.java,
            databaseName,
        )
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun waitForTagText(tag: String, text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag)
                    .assertTextContains(text, substring = true)
            }.isSuccess
        }
    }

    private fun waitForTagToDisappear(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isFailure
        }
    }
}