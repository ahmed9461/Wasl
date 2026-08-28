package com.wasl.app

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
                candidates = listOf(" ", "	"),
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
