# Group Expense v10 verification gate

This marker records the final full Android CI gate for the Group Expense schema v10 foundation after aligning the MVP acceptance backup schema expectation with database version 10.

The foundation remains isolated from `main` and from the draft PR branch until the full gate (unit tests, lint, APK, Room v10, Android instrumentation, and PDF evidence) is green and the instrumentation XML artifact is parsed.
