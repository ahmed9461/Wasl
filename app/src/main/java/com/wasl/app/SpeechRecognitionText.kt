package com.wasl.app

internal fun recognizedSpeechText(
    accepted: Boolean,
    candidates: List<String>?,
): String? {
    if (!accepted) return null
    return candidates
        .orEmpty()
        .asSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
}
