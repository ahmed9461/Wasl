package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class PersonContactDetailsUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-person-contact-ui-${UUID.randomUUID()}.db"
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
    fun newPersonContactFieldsPersistThroughCreateFlow() {
        composeRule.setContent {
            WaslApp(repository = repository, instanceKey = "person-contact-ui")
        }

        composeRule.onNodeWithText("إضافة حساب").performClick()
        composeRule.onNodeWithTag("create-entry-individual").performClick()

        input("create-person-name", "أحمد محمد")
        input("create-person-phone", "  +967 777 123 456  ")
        input("create-person-email", "  ahmed@example.com  ")
        input("create-person-notes", "  مورد رئيسي  ")
        input("create-debt-amount", "150000")

        composeRule.onNodeWithTag("create-debt-save").performClick()

        val account = runBlocking {
            withTimeout(10_000) {
                repository.observeAccounts().first { it.size == 1 }.single()
            }
        }
        assertEquals("أحمد محمد", account.person.displayName)
        assertEquals("+967 777 123 456", account.person.phone)
        assertEquals("ahmed@example.com", account.person.email)
        assertEquals("مورد رئيسي", account.person.notes)
    }

    private fun input(tag: String, value: String) {
        scrollToTag(tag)
        composeRule.onNodeWithTag(tag).performTextInput(value)
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }
}
