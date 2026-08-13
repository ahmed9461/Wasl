package com.wasl.app

import android.content.Context
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    }

    @Test
    fun createDebtRecordPartialPaymentAndReopenFromPersistedData() {
        val repositoryState = mutableStateOf<WaslRepository>(RoomWaslRepository(database!!))
        val generation = mutableIntStateOf(0)
        composeRule.setContent {
            val currentGeneration = generation.intValue
            key(currentGeneration) {
                WaslApp(
                    repository = repositoryState.value,
                    instanceKey = "ui-test-$currentGeneration",
                )
            }
        }

        composeRule.onNodeWithText("إضافة حساب").performClick()
        composeRule.onNodeWithTag("create-person-name").performTextInput("أحمد")
        composeRule.onNodeWithTag("create-debt-amount").performTextInput("100000")
        composeRule.onNodeWithTag("create-debt-save").performClick()
        waitForText("أحمد")
        val debtId = runBlocking {
            repositoryState.value.observeAccounts().first().single().ledger.header.id.value
        }

        composeRule.onNodeWithTag("account-$debtId").assertIsDisplayed().performClick()
        waitForTag("record-payment")
        composeRule.onNodeWithTag("record-payment").performClick()
        composeRule.onNodeWithTag("payment-amount").performTextInput("20000")
        composeRule.onNodeWithTag("payment-review").performClick()
        composeRule.onNodeWithTag("payment-confirm").performClick()
        waitForText("دفعة مسجلة")

        composeRule.onNodeWithTag("account-remaining")
            .assertTextContains("80,000 YER", substring = true)
        composeRule.onNodeWithText("دفعة مسجلة").assertIsDisplayed()

        composeRule.runOnIdle {
            database!!.close()
            openDatabase()
            repositoryState.value = RoomWaslRepository(database!!)
            generation.intValue += 1
        }
        waitForText("أحمد")

        composeRule.onNodeWithTag("account-$debtId").assertIsDisplayed().performClick()
        waitForText("دفعة مسجلة")
        composeRule.onNodeWithTag("account-remaining")
            .assertTextContains("80,000 YER", substring = true)
        composeRule.onNodeWithText("دفعة مسجلة").assertIsDisplayed()
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

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
