package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExistingPersonDebtUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-existing-person-ui-${UUID.randomUUID()}.db"
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
    fun createsSecondIndependentDebtForSelectedExistingPersonAndFindsBothInSearch() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "existing-person-ui",
            )
        }

        createFirstDebt()
        val firstAccount = runBlocking {
            withTimeout(10_000) {
                repository.observeAccounts().first { it.size == 1 }.single()
            }
        }
        waitForTag("account-${firstAccount.ledger.header.id.value}")

        composeRule.onNodeWithText("إضافة حساب").performClick()
        composeRule.onNodeWithTag("create-person-mode-existing").performClick()
        waitForTag("existing-person-${firstAccount.person.id.value}")
        composeRule.onNodeWithTag("existing-person-${firstAccount.person.id.value}").performClick()
        composeRule.onNodeWithTag("selected-existing-person").assertIsDisplayed()
        composeRule.onNodeWithTag("create-debt-amount").performTextInput("50000")
        composeRule.onNodeWithTag("create-debt-save").performClick()

        val accounts = runBlocking {
            withTimeout(10_000) {
                repository.observeAccounts().first { it.size == 2 }
            }
        }
        waitForTagToDisappear("create-debt-save")

        assertEquals(1, runBlocking { database.personDao().count() })
        assertEquals(2, runBlocking { database.debtDao().count() })
        assertEquals(setOf(firstAccount.person.id), accounts.map { it.person.id }.toSet())

        composeRule.onNodeWithTag("nav-search").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("أحمد")
        accounts.forEach { waitForTag("search-result-${it.ledger.header.id.value}") }
    }

    private fun createFirstDebt() {
        composeRule.onNodeWithText("إضافة حساب").performClick()
        composeRule.onNodeWithTag("create-person-name").performTextInput("أحمد")
        composeRule.onNodeWithTag("create-debt-amount").performTextInput("100000")
        composeRule.onNodeWithTag("create-debt-save").performClick()
        waitForTagToDisappear("create-debt-save")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun waitForTagToDisappear(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isFailure
        }
    }
}
