# Group Expense UI verification gate

This marker queues the final full Android CI verification for the Group Expense creation UI after updating legacy individual-debt instrumentation flows to pass through the explicit create-type picker.

Verification must include unit tests, lint, APK build, Room schema v10 validation, the full Android instrumentation suite, and PDF evidence before this UI batch is considered verified or integrated into the draft PR branch.
