# Voice Dictation Hardening — 2026-08-28

Status: Verified.

Scope:
- Move Android speech activity-result handling behind a replaceable `VoiceDictationBridge`.
- Preserve Arabic free-form speech request defaults.
- Model recognized, empty, and cancelled outcomes explicitly.
- Handle unavailable recognizer and launch failure without financial writes.
- Verify recognized speech reaches Natural Entry preview and still requires explicit confirmation before persistence.
- Preserve existing Natural Entry parsing and confirmation semantics.

Verification:
- Android CI #963 — run `33136770143` — head `c13c52a84b03202bd842c022184f6ca2f118778c`.
- Unit tests, lint, debug APK, and Room schema v10 verification: passed.
- Emulator instrumentation: **123/123**, 0 failures, 0 errors, 0 skipped.
- Payment Receipt, Debt Receipt, and Account Statement PDF evidence checks: passed.
- Instrumentation artifact: `9672607105` (`Wasl-room-instrumentation-results`).
- Artifact SHA-256: `4e0cf315b65293123e4c6a1c75aee8f3ffe781846fc8ec8f1b48ba328e688b9e` (matches GitHub digest).

Voice instrumentation cases verified:
- `recognizedVoiceReachesPreviewButNeverPersistsBeforeConfirmation`
- `emptyVoiceResultShowsGuidanceAndDoesNotPersist`
- `cancelledVoiceResultKeepsManualTextUntouched`
- `unavailableRecognizerShowsFallbackWithoutChangingFinancialData`
- `launchFailureShowsManualEntryFallback`

The existing manual Natural Entry confirmation test remains green, so voice continues to feed the same Preview/Confirmation path and never performs a direct financial write.
