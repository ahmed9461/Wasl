package com.wasl.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechRecognitionTextTest {
    @Test
    fun acceptedResultUsesFirstNonBlankCandidateAndTrimsIt() {
        assertEquals(
            "سلفت عبدالله 5000 ريال سعودي",
            recognizedSpeechText(
                accepted = true,
                candidates = listOf("   ", "  سلفت عبدالله 5000 ريال سعودي  ", "بديل"),
            ),
        )
    }

    @Test
    fun acceptedEmptyResultReturnsNull() {
        assertNull(
            recognizedSpeechText(
                accepted = true,
                candidates = listOf(" ", "\t"),
            ),
        )
    }

    @Test
    fun cancelledResultNeverUsesReturnedCandidates() {
        assertNull(
            recognizedSpeechText(
                accepted = false,
                candidates = listOf("نص يجب تجاهله"),
            ),
        )
    }
}
