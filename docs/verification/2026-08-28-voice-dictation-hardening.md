# Voice Dictation Hardening — 2026-08-28

Status: CI pending.

Scope:
- Move Android speech activity-result handling behind a replaceable VoiceDictationBridge.
- Preserve Arabic free-form speech request defaults.
- Model recognized, empty, and cancelled outcomes explicitly.
- Handle unavailable recognizer and launch failure without financial writes.
- Verify recognized speech reaches Natural Entry preview and still requires explicit confirmation before persistence.
- Preserve existing Natural Entry parsing and confirmation semantics.

Verification gate: full Android CI on this branch, including Unit/Lint/APK, Room schema v10, emulator instrumentation, and PDF evidence.
