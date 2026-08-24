package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePaymentPromiseCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.PaymentPromiseStatus
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentPromiseUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var promiseStore: RoomPaymentPromiseStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-promise-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        promiseStore = RoomPaymentPromiseStore(database)
        runBlocking {
            repository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-promise-ui"),
                    debtId = DebtId("debt-promise-ui"),
                    personName = "محمد",
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(100_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-20T08:00:00Z"),
                ),
            )
            promiseStore.createPaymentPromise(
                CreatePaymentPromiseCommand(
                    commandId = "promise-command-ui",
                    promiseId = "promise-ui",
                    debtId = DebtId("debt-promise-ui"),
                    promisedDate = LocalDate.parse("2026-08-23"),
                    note = "قال إنه سيدفع بعد الراتب",
                    createdAt = Instant.parse("2026-08-21T10:00:00Z"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun storedPromiseAppearsAndCanBeMarkedMissedWithoutChangingDebt() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                paymentPromiseStore = promiseStore,
                instanceKey = "promise-resolution-ui-test",
                requestedDebtId = "debt-promise-ui",
            )
        }

        scrollToTag("payment-promise-card-promise-ui")
        composeRule.onNodeWithTag("payment-promise-card-promise-ui").assertIsDisplayed()
        composeRule.onNodeWithText("متأخر — بانتظار الحسم").assertIsDisplayed()
        composeRule.onNodeWithTag("resolve-promise-missed-promise-ui").performClick()

        waitForTag("payment-promise-resolution-note")
        composeRule.onNodeWithTag("payment-promise-resolution-note")
            .performTextInput("لم يلتزم بالموعد")
        composeRule.onNodeWithTag("confirm-payment-promise-resolution").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                database.paymentPromiseDao().findById("promise-ui")?.status ==
                    PaymentPromiseStatus.MISSED.name
            }
        }
        scrollToTag("payment-promise-card-promise-ui")
        composeRule.onNodeWithText("لم يُنفذ").assertIsDisplayed()

        runBlocking {
            val account = requireNotNull(repository.getAccount(DebtId("debt-promise-ui")))
            assertEquals(Money(100_000L, CurrencyCode.YER), account.ledger.balance)
            assertEquals(null, account.ledger.header.dueDate)
            val promise = requireNotNull(database.paymentPromiseDao().findById("promise-ui"))
            assertEquals("لم يلتزم بالموعد", promise.resolutionNote)
        }
    }

    @Test
    fun addPromiseDialogRequiresAChosenDate() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                paymentPromiseStore = promiseStore,
                instanceKey = "promise-create-ui-test",
                requestedDebtId = "debt-promise-ui",
            )
        }

        scrollToTag("add-payment-promise")
        composeRule.onNodeWithTag("add-payment-promise").performClick()
        waitForTag("save-payment-promise")
        composeRule.onNodeWithTag("save-payment-promise").performClick()
        composeRule.onNodeWithText("اختر تاريخ الوعد بالسداد.").assertIsDisplayed()
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
