from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


activity_path = ROOT / "app/src/main/java/com/wasl/app/NaturalEntryActivity.kt"
text = activity_path.read_text(encoding="utf-8")

for unused_import in [
    "import android.app.Activity\n",
    "import android.content.Intent\n",
    "import android.speech.RecognizerIntent\n",
    "import androidx.activity.compose.rememberLauncherForActivityResult\n",
    "import androidx.activity.result.contract.ActivityResultContracts\n",
    "import androidx.compose.ui.platform.LocalContext\n",
]:
    if unused_import not in text:
        raise SystemExit(f"Expected import not found: {unused_import.strip()}")
    text = text.replace(unused_import, "", 1)

old_signature = """internal fun NaturalEntryScreen(
    parser: NaturalEntryParser,
    confirmationService: NaturalDebtConfirmationService,
    onBack: () -> Unit,
) {"""
new_signature = """internal fun NaturalEntryScreen(
    parser: NaturalEntryParser,
    confirmationService: NaturalDebtConfirmationService,
    onBack: () -> Unit,
    voiceBridge: VoiceDictationBridge? = null,
) {"""
if old_signature not in text:
    raise SystemExit("NaturalEntryScreen signature changed unexpectedly.")
text = text.replace(old_signature, new_signature, 1)

old_context = """    val scope = rememberCoroutineScope()
    val context = LocalContext.current
"""
new_context = """    val scope = rememberCoroutineScope()
    val activeVoiceBridge = voiceBridge ?: rememberAndroidVoiceDictationBridge()
"""
if old_context not in text:
    raise SystemExit("NaturalEntryScreen context setup changed unexpectedly.")
text = text.replace(old_context, new_context, 1)

old_launcher = """    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val accepted = result.resultCode == Activity.RESULT_OK
        val recognized = recognizedSpeechText(
            accepted = accepted,
            candidates = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
        )
        when {
            recognized != null -> {
                text = recognized
                analyze(recognized)
            }
            accepted -> {
                message = "لم يتم التعرف على كلام واضح. حاول مرة أخرى أو اكتب النص يدويًا."
            }
        }
    }

"""
new_launcher = """    fun handleVoiceOutcome(outcome: VoiceDictationOutcome) {
        when (outcome) {
            is VoiceDictationOutcome.Recognized -> {
                text = outcome.text
                analyze(outcome.text)
            }
            VoiceDictationOutcome.Empty -> {
                message = "لم يتم التعرف على كلام واضح. حاول مرة أخرى أو اكتب النص يدويًا."
            }
            VoiceDictationOutcome.Cancelled -> Unit
        }
    }

"""
if old_launcher not in text:
    raise SystemExit("Voice launcher block changed unexpectedly.")
text = text.replace(old_launcher, new_launcher, 1)

old_button = """                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن")
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                            }
                            if (intent.resolveActivity(context.packageManager) == null) {
                                message = "خدمة التعرف على الصوت غير متاحة على هذا الجهاز."
                            } else {
                                runCatching { voiceLauncher.launch(intent) }
                                    .onFailure {
                                        message = "تعذر فتح خدمة التعرف على الصوت. يمكنك الكتابة يدويًا."
                                    }
                            }
                        },"""
new_button = """                        onClick = {
                            when (activeVoiceBridge.launch(::handleVoiceOutcome)) {
                                VoiceDictationLaunchResult.LAUNCHED -> Unit
                                VoiceDictationLaunchResult.UNAVAILABLE -> {
                                    message = "خدمة التعرف على الصوت غير متاحة على هذا الجهاز."
                                }
                                VoiceDictationLaunchResult.FAILED -> {
                                    message = "تعذر فتح خدمة التعرف على الصوت. يمكنك الكتابة يدويًا."
                                }
                            }
                        },"""
if old_button not in text:
    raise SystemExit("Voice button block changed unexpectedly.")
text = text.replace(old_button, new_button, 1)

activity_path.write_text(text, encoding="utf-8")

write(
    "app/src/main/java/com/wasl/app/VoiceDictation.kt",
    '''package com.wasl.app

internal data class VoiceDictationRequest(
    val language: String = "ar",
    val prompt: String = "تحدث الآن",
    val maxResults: Int = 3,
) {
    init {
        require(language.isNotBlank()) { "Voice language cannot be blank." }
        require(prompt.isNotBlank()) { "Voice prompt cannot be blank." }
        require(maxResults > 0) { "Voice maxResults must be positive." }
    }
}

internal sealed interface VoiceDictationOutcome {
    data class Recognized(val text: String) : VoiceDictationOutcome {
        init {
            require(text.isNotBlank()) { "Recognized voice text cannot be blank." }
        }
    }

    data object Empty : VoiceDictationOutcome
    data object Cancelled : VoiceDictationOutcome
}

internal enum class VoiceDictationLaunchResult {
    LAUNCHED,
    UNAVAILABLE,
    FAILED,
}

internal interface VoiceDictationBridge {
    fun launch(onResult: (VoiceDictationOutcome) -> Unit): VoiceDictationLaunchResult
}

internal class VoiceDictationAdapter(
    private val recognizerAvailable: () -> Boolean,
) {
    fun isAvailable(): Boolean = runCatching(recognizerAvailable).getOrDefault(false)

    fun request(): VoiceDictationRequest = VoiceDictationRequest()

    fun outcome(
        accepted: Boolean,
        candidates: List<String>?,
    ): VoiceDictationOutcome {
        if (!accepted) return VoiceDictationOutcome.Cancelled
        val recognized = recognizedSpeechText(
            accepted = true,
            candidates = candidates,
        )
        return if (recognized == null) {
            VoiceDictationOutcome.Empty
        } else {
            VoiceDictationOutcome.Recognized(recognized)
        }
    }
}
''',
)

write(
    "app/src/main/java/com/wasl/app/AndroidVoiceDictationBridge.kt",
    '''package com.wasl.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberAndroidVoiceDictationBridge(): VoiceDictationBridge {
    val context = LocalContext.current
    val adapter = remember(context) {
        VoiceDictationAdapter(
            recognizerAvailable = {
                voiceRecognitionIntent(VoiceDictationRequest())
                    .resolveActivity(context.packageManager) != null
            },
        )
    }
    var resultConsumer by remember {
        mutableStateOf<((VoiceDictationOutcome) -> Unit)?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val consumer = resultConsumer
        resultConsumer = null
        consumer?.invoke(
            adapter.outcome(
                accepted = result.resultCode == Activity.RESULT_OK,
                candidates = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
            ),
        )
    }

    return remember(adapter, launcher) {
        object : VoiceDictationBridge {
            override fun launch(
                onResult: (VoiceDictationOutcome) -> Unit,
            ): VoiceDictationLaunchResult {
                if (!adapter.isAvailable()) {
                    return VoiceDictationLaunchResult.UNAVAILABLE
                }
                resultConsumer = onResult
                return runCatching {
                    launcher.launch(voiceRecognitionIntent(adapter.request()))
                    VoiceDictationLaunchResult.LAUNCHED
                }.getOrElse {
                    resultConsumer = null
                    VoiceDictationLaunchResult.FAILED
                }
            }
        }
    }
}

private fun voiceRecognitionIntent(request: VoiceDictationRequest): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.language)
        putExtra(RecognizerIntent.EXTRA_PROMPT, request.prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, request.maxResults)
    }
''',
)

write(
    "app/src/test/java/com/wasl/app/VoiceDictationAdapterTest.kt",
    '''package com.wasl.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VoiceDictationAdapterTest {
    @Test
    fun requestUsesArabicFreeFormDefaults() {
        val adapter = VoiceDictationAdapter { true }

        val request = adapter.request()

        assertEquals("ar", request.language)
        assertEquals("تحدث الآن", request.prompt)
        assertEquals(3, request.maxResults)
    }

    @Test
    fun availabilityIsSafeWhenResolverFails() {
        assertTrue(VoiceDictationAdapter { true }.isAvailable())
        assertFalse(VoiceDictationAdapter { false }.isAvailable())
        assertFalse(
            VoiceDictationAdapter {
                error("resolver failure")
            }.isAvailable(),
        )
    }

    @Test
    fun recognizedOutcomeUsesFirstNonBlankCandidate() {
        val outcome = VoiceDictationAdapter { true }.outcome(
            accepted = true,
            candidates = listOf(" ", "  سلفت عبدالله 5000 ريال سعودي  ", "بديل"),
        )

        val recognized = assertIs<VoiceDictationOutcome.Recognized>(outcome)
        assertEquals("سلفت عبدالله 5000 ريال سعودي", recognized.text)
    }

    @Test
    fun acceptedWithoutTextIsEmpty() {
        assertIs<VoiceDictationOutcome.Empty>(
            VoiceDictationAdapter { true }.outcome(
                accepted = true,
                candidates = listOf(" ", "\t"),
            ),
        )
    }

    @Test
    fun cancelledResultNeverConsumesCandidates() {
        assertIs<VoiceDictationOutcome.Cancelled>(
            VoiceDictationAdapter { true }.outcome(
                accepted = false,
                candidates = listOf("نص يجب تجاهله"),
            ),
        )
    }
}
''',
)

write(
    "app/src/androidTest/java/com/wasl/app/NaturalEntryVoiceUiInstrumentedTest.kt",
    '''package com.wasl.app

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
''',
)

if "rememberLauncherForActivityResult" in text or "RecognizerIntent" in text:
    raise SystemExit("NaturalEntryActivity still owns Android voice launcher details.")
if "voiceBridge: VoiceDictationBridge? = null" not in text:
    raise SystemExit("Voice bridge injection missing.")
if "VoiceDictationLaunchResult.UNAVAILABLE" not in text:
    raise SystemExit("Unavailable voice path missing.")
