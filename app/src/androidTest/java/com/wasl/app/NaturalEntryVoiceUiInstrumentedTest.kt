package com.wasl.app

import android.content.Context
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
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
class NaturalEntryVoiceUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository
    private lateinit var promiseStore: RoomPaymentPromiseStore
    private lateinit var parser: NaturalEntryParser
    private lateinit var service: NaturalDebtConfirmationService

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-natural-entry-voice-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
        promiseStore = RoomPaymentPromiseStore(database)
        parser = NaturalEntryParser(today = { LocalDate.of(2026, 8, 28) })
        service = NaturalDebtConfirmationService(
            repository = repository,
            paymentPromiseStore = promiseStore,
            clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
            zoneIdProvider = { ZoneOffset.UTC },
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun recognizedVoiceReachesPreviewButNeverPersistsBeforeConfirmation() {
        val voice = FakeVoiceDictationBridge()
        setContent(voice)

        composeRule.onNodeWithTag("natural-entry-voice").performClick()
        composeRule.runOnIdle {
            voice.emit(
                VoiceDictationOutcome.Recognized(
                    "سلفت عبدالله 5000 ريال سعودي اليوم",
                ),
            )
        }

        composeRule.onNodeWithTag("natural-entry-text")
            .assertTextContains("سلفت عبدالله 5000 ريال سعودي اليوم")
        composeRule.onNodeWithTag("natural-entry-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("natural-entry-confirm").assertIsDisplayed()
        assertEquals(0, runBlocking { database.debtDao().count() })

        composeRule.onNodeWithTag("natural-entry-confirm").performClick()
        val account = runBlocking {
            withTimeout(10_000) {
                repository.observeAccounts().first { it.size == 1 }.single()
            }
        }
        assertEquals("عبدالله", account.person.displayName)
    }

    @Test
    fun emptyVoiceResultShowsGuidanceAndDoesNotPersist() {
        val voice = FakeVoiceDictationBridge()
        setContent(voice)

        composeRule.onNodeWithTag("natural-entry-voice").performClick()
        composeRule.runOnIdle {
            voice.emit(VoiceDictationOutcome.Empty)
        }

        composeRule.onNodeWithText(
            "لم يتم التعرف على كلام واضح. حاول مرة أخرى أو اكتب النص يدويًا.",
        ).assertIsDisplayed()
        assertEquals(0, runBlocking { database.debtDao().count() })
    }

    @Test
    fun cancelledVoiceResultKeepsManualTextUntouched() {
        val voice = FakeVoiceDictationBridge()
        setContent(voice)
        composeRule.onNodeWithTag("natural-entry-text")
            .performTextInput("نص يدوي باق")

        composeRule.onNodeWithTag("natural-entry-voice").performClick()
        composeRule.runOnIdle {
            voice.emit(VoiceDictationOutcome.Cancelled)
        }

        composeRule.onNodeWithTag("natural-entry-text")
            .assertTextContains("نص يدوي باق")
        assertEquals(0, runBlocking { database.debtDao().count() })
    }

    @Test
    fun unavailableRecognizerShowsFallbackWithoutChangingFinancialData() {
        val voice = FakeVoiceDictationBridge(
            launchResult = VoiceDictationLaunchResult.UNAVAILABLE,
        )
        setContent(voice)

        composeRule.onNodeWithTag("natural-entry-voice").performClick()

        composeRule.onNodeWithText(
            "خدمة التعرف على الصوت غير متاحة على هذا الجهاز.",
        ).assertIsDisplayed()
        assertEquals(0, runBlocking { database.debtDao().count() })
    }

    @Test
    fun launchFailureShowsManualEntryFallback() {
        val voice = FakeVoiceDictationBridge(
            launchResult = VoiceDictationLaunchResult.FAILED,
        )
        setContent(voice)

        composeRule.onNodeWithTag("natural-entry-voice").performClick()

        composeRule.onNodeWithText(
            "تعذر فتح خدمة التعرف على الصوت. يمكنك الكتابة يدويًا.",
        ).assertIsDisplayed()
        assertEquals(0, runBlocking { database.debtDao().count() })
    }

    private fun setContent(voice: VoiceDictationBridge) {
        composeRule.setContent {
            NaturalEntryScreen(
                parser = parser,
                confirmationService = service,
                onBack = {},
                voiceBridge = voice,
            )
        }
    }

    private class FakeVoiceDictationBridge(
        private val launchResult: VoiceDictationLaunchResult =
            VoiceDictationLaunchResult.LAUNCHED,
    ) : VoiceDictationBridge {
        private var callback: ((VoiceDictationOutcome) -> Unit)? = null

        override fun launch(
            onResult: (VoiceDictationOutcome) -> Unit,
        ): VoiceDictationLaunchResult {
            if (launchResult == VoiceDictationLaunchResult.LAUNCHED) {
                callback = onResult
            }
            return launchResult
        }

        fun emit(outcome: VoiceDictationOutcome) {
            requireNotNull(callback) { "Voice bridge was not launched." }
                .invoke(outcome)
        }
    }
}
