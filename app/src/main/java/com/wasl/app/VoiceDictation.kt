package com.wasl.app

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
