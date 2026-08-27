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
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NaturalEntryUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var promiseStore: RoomPaymentPromiseStore

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-natural-entry-ui-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        promiseStore = RoomPaymentPromiseStore(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun previewsThenPersistsDebtAndPromiseOnlyAfterExplicitConfirmation() {
        val referenceDate = LocalDate.of(2026, 8, 27)
        val clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
        val parser = NaturalEntryParser(today = { referenceDate })
        val service = NaturalDebtConfirmationService(
            repository = repository,
            paymentPromiseStore = promiseStore,
            clock = clock,
            zoneIdProvider = { ZoneOffset.UTC },
        )

        composeRule.setContent {
            NaturalEntryScreen(
                parser = parser,
                confirmationService = service,
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("natural-entry-text").performTextInput(
            "سلفت عبدالله 5000 ريال سعودي اليوم وقال بيرجعها الخميس",
        )
        composeRule.onNodeWithTag("natural-entry-analyze").performClick()
        composeRule.onNodeWithTag("natural-entry-preview").assertIsDisplayed()
        composeRule.onNodeWithText("عبدالله").assertIsDisplayed()
        composeRule.onNodeWithText("5000 SAR").assertIsDisplayed()
        composeRule.onNodeWithTag("natural-entry-confirm").assertIsDisplayed()

        assertEquals(0, runBlocking { database.debtDao().count() })
        composeRule.onNodeWithTag("natural-entry-confirm").performClick()

        val account = runBlocking {
            withTimeout(10_000) {
                repository.observeAccounts().first { it.size == 1 }.single()
            }
        }
        val promises = runBlocking {
            withTimeout(10_000) {
                promiseStore.observePaymentPromises(account.ledger.header.id)
                    .first { it.isNotEmpty() }
            }
        }

        assertEquals("عبدالله", account.person.displayName)
        assertEquals(CurrencyCode.SAR, account.ledger.header.originalAmount.currency)
        assertEquals(500_000L, account.ledger.header.originalAmount.minorUnits)
        assertEquals(1, promises.size)
        assertTrue(promises.single().promisedDate.isAfter(referenceDate))
    }
}
