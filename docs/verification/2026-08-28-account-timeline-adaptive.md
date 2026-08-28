# Account Timeline Adaptive / RTL Verification Gate

Date: 2026-08-28

## Verified baseline

Android CI #912 — run `33129069861` — head `e007ef9be0217eec82a71e2697afa422f3f8bba3` completed successfully.

- Unit tests: passed.
- Lint: passed.
- Debug APK: passed.
- Room schema v9 verification: passed.
- Android instrumentation: **106/106**, 0 failures, 0 errors, 0 skipped.
- Payment receipt PDF evidence: passed.
- Debt receipt and account statement evidence: passed.

This verifies the Home and Account Details large-font batches through the Account Details hero badges, financial metrics, and timeline heading.

## Current gate

Product commit: `9d939f082ab15cf7cb9284972e2b64cde1fb49dd` — `fix: harden account timeline adaptive RTL layout`.

Scope is intentionally UI/display-only:

- payment timeline title/status stacks at large font or narrow width;
- detail money rows and metadata rows stack via the shared adaptive threshold;
- two-action timeline groups stack to full-width actions and preserve click behavior;
- ready receipt share/open actions and issue/reverse actions reuse the adaptive component;
- receipt document numbers and currency codes are LTR-isolated;
- account date, time, and local-time formatting reuses the central `ltrIsolate()` helper instead of handwritten LRI/PDI literals;
- payment receipt notice isolates the document number;
- `AccountDetailsTimelineAdaptiveUiInstrumentedTest` adds two focused 200% Font Scale tests, including click verification for both actions.

No Ledger, Room schema, balance, payment, reversal, receipt-generation, PDF snapshot, or backup behavior was changed.

Status: **Pending full Android CI verification**. Do not mark this batch Verified until Unit/Lint/APK/Room, emulator instrumentation, and PDF evidence all pass on this code.
