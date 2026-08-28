# خطة الاختبارات

آخر تحديث: 2026-08-27

## الهدف

إثبات صحة المال والاستمرارية والأمان والمستندات والمرفقات والمتابعة والإدخال الطبيعي/الصوتي على تنفيذ Android فعلي، لا مجرد نجاح رسم الشاشة أو compile منفرد.

## قاعدة الإغلاق

أي ميزة دائمة لا تعتبر Complete إلا إذا مرت، حسب نطاقها، عبر Unit + Room/Repository/instrumentation + Compose/UI + Lint/build + Idempotency/Recovery + Backup/Restore، مع تحديث Schema والوثائق.

## طبقات الاختبار

### Unit — Domain/Application

- Money arithmetic وOverflow وCurrency mismatch.
- Debt state وDue state.
- Partial / Full payment وOverpayment.
- Reversal وDouble reversal وDuplicate IDs.
- Summary حسب العملة والاتجاه.
- Parsing مبالغ YER/SAR/USD والأرقام العربية دون `Double`.
- ViewModel idempotency وإعادة المحاولة بعد نتيجة غير مؤكدة.
- Reminder time/recovery/escalation policy.
- App Lock session state والمهلات والمصادقة والتعطيل.
- Installment schedule invariants.
- Payment Claim models وfollow-up resolver.
- Payment message templates.
- Objective statistics builder.
- Natural entry parser وconfirmation service.
- Voice adapter/result extraction عند فصله من Activity لتغطية success/cancel/empty/unavailable.

### Database / Repository

- Entity constraints وForeign Keys.
- Transaction ذرية وعدم ترك نصف كتابة.
- Idempotency للـcommand IDs.
- Concurrent payments ومنع Overdraw.
- Replay يطابق Projection والحالة المغلقة.
- Restart وإعادة فتح قاعدة حقيقية.
- Migrations من Schemas المدعومة حتى **v9**.
- v6→v7: `issued_documents.ledger_entry_id` nullable.
- v7→v8: `payment_claims`.
- v8→v9: `attachments` وفهرس `relative_path` الفريد.
- Promises وClaims وInstallment revisions تبقى منفصلة عن Ledger.
- Document snapshots ثابتة ولا تنشئ Ledger entries مصطنعة.
- Attachment linked ledger entry يجب أن ينتمي للدين نفسه.

### UI / Compose

- إنشاء دين وتسجيل دفعة وإعادة فتح البيانات.
- تعديل/إلغاء الاستحقاق وAudit.
- Today للاستحقاقات والوعود والأقساط والمطالبات.
- Search الأساسي والمتقدم وفتح النتائج.
- إنشاء دين لشخص موجود دون تكرار Person.
- Payment promises وحسمها دون تغيير الرصيد.
- Claims: إنشاء/عرض/متابعة/حسم دون Ledger mutation.
- Attachment vault: إضافة وعرض وفتح/مشاركة وحالات السلامة.
- Person Timeline متعدد الحسابات والعملات.
- Payment message section.
- Statistics screen.
- Natural Entry: text → parse → preview → confirm.
- Voice Dictation الحالي: زر الإملاء موجود؛ المطلوب إضافة اختبار قابل للحقن يثبت أن recognized text يدخل Parser وأنه لا يحدث Save قبل confirmation.
- Security Hub وApp Lock recovery.
- Dark Mode وFont Scale 2.0 في المسارات الحساسة.

ما زال مطلوبًا قبل Release عام:

- تغطية Compact/Medium/Expanded أوسع.
- Font Scale على جميع الشاشات المالية الرئيسية.
- TalkBack semantics وترتيب focus أوسع.
- Touch targets ومراجعة RTL/LTR للأرقام والتواريخ والأموال.
- Voice success/cancel/empty/unavailable tests دون الاعتماد على خدمة تعرف صوت خارجية في CI.

### Platform

- Notification permission وحالة القنوات.
- WorkManager Unique Work وRecovery.
- General Reminder repeat rules.
- Exact Alarm fallback وعدم فتح إعدادات النظام تلقائيًا.
- تغير الوقت/المنطقة الزمنية.
- Safe notification actions وعدم كتابة Ledger من callback.
- Snooze وعدم إعادة إشعار حساب مسدد.
- FileProvider وSHA-256 قبل فتح/مشاركة PDF والمرفقات.
- Android system authentication عبر BiometricPrompt/Device Credential.
- PDF عربي فعلي متعدد الصفحات.
- RecognizerIntent availability/failure mapping للإملاء الصوتي بعد فصل adapter القابل للاختبار.

### Backup / Restore — Schema v9

- Round trip مشفر.
- Wrong passphrase لا يغير الحالة الحية.
- ملفات PDF `READY` تدخل النسخة وتعود ببصمتها.
- Attachment files + metadata تدخل النسخة وتعود ببصمتها.
- `payment_claims` تدخل النسخة وتخضع لفحص الاتجاه والحالات والتواريخ.
- رفض المسارات غير الآمنة وSchema غير المدعوم والبصمات غير المطابقة.
- Restore عبر Stage + Room مؤقتة + Foreign key/invariant checks.
- Attachment linked entry يجب أن يكون من نفس الدين.
- بيانات أضيفت بعد النسخة تختفي بعد Restore الناجح.
- ملف أفسد بعد النسخة يستعاد إلى النسخة الصحيحة.
- Rollback عند فشل اعتماد DB أو الملفات.

## End-to-End

`MvpAcceptanceInstrumentedTest` يبقى اختبار الأساس المالي التاريخي، لكن بوابة Post-MVP لا تعتمد عليه وحده. الاختبارات المتخصصة تغطي Claims/Attachments/Person/Statistics/Natural Entry/Advanced Search إضافة إلى رحلة MVP.

رحلة MVP الأساسية تثبت: إنشاء دين، Restart، دفعات جزئية، سداد، reversal، سداد بديل، PDF READY، Backup مشفر، تغيير الحالة بعد النسخة، Restore والتحقق Offline.

## اختبارات رئيسية منفذة

| الاختبار | ما يثبته |
|---|---|
| `MoneyTest` | الحساب الصحيح ومنع خلط العملات وOverflow |
| `DebtLedgerTest` | الأصل والدفعات والعكس وترتيب الأحداث والاستحقاق |
| `MoneyInputParserTest` | Parsing دقيق دون Floating Point |
| `RoomWaslRepositoryInstrumentedTest` | Restart، الذرية، Idempotency، التزامن، FK ومسارات الحساب الأساسية |
| `WaslDatabaseBaselineTest` | Baseline migrations حتى Schema الحالية |
| `PaymentClaimMigrationInstrumentedTest` | Migration 7→8 |
| `PaymentClaimStoreInstrumentedTest` | Claims persistence/idempotency/invariants |
| `TodayPaymentClaimUiInstrumentedTest` | أولوية Claim في Today |
| `AttachmentStoreInstrumentedTest` | Attachment persistence/safety |
| `AttachmentVaultUiInstrumentedTest` | مسار واجهة الخزنة |
| `AttachmentFileAccessInstrumentedTest` | فتح/مشاركة وفحص الملف |
| `AttachmentBackupRestoreInstrumentedTest` | Round-trip للمرفقات |
| `AccountDocumentMigrationInstrumentedTest` | v6→v7 والمستندات العامة |
| `PaymentReceiptRepositoryInstrumentedTest` | immutable payment snapshots والترقيم |
| `AccountDocumentStoreInstrumentedTest` | Debt receipt/statement snapshots |
| `PaymentReceiptPdfInstrumentedTest` | PDF إيصال السداد |
| `AccountDocumentPdfInstrumentedTest` | Debt Receipt وAccount Statement |
| `BackupRestoreInstrumentedTest` | Backup/Restore العام |
| `AccountDocumentBackupInstrumentedTest` | استعادة المستند وملف PDF |
| `PersonTimelineUiInstrumentedTest` | صفحة الشخص متعددة الحسابات/العملات وFont Scale |
| `StatisticsScreenUiInstrumentedTest` | الإحصاءات الموضوعية وFont Scale |
| `NaturalEntryUiInstrumentedTest` | الإدخال الطبيعي ومسار التأكيد؛ لا يغطي external voice result بعد |
| `AdvancedSearchUiInstrumentedTest` | البحث المتقدم |
| `AdaptiveSearchUiInstrumentedTest` | سلوك البحث المتكيف |
| `SecurityUiInstrumentedTest` | App Lock UI/Recovery وDark/Font Scale |
| `MvpAcceptanceInstrumentedTest` | رحلة مالية/مستند/backup/restore كاملة |

## فحص PDF داخل CI

بعد نجاح Android instrumentation تستخرج CI ملفات حقيقية من مساحة التطبيق وتستخدم `pdfinfo`, `pdftotext`, `pdftoppm` للتحقق وإنتاج evidence.

## CI الحالي

كل Push وPull Request يشغل:

- `:core:domain:test`.
- `:app:testDebugUnitTest`.
- `:app:lintDebug`.
- `:app:assembleDebug`.
- Room Schema `9.json` verification.
- تحقق `payment_claims`, `attachments` وفهرس المسار الفريد.
- `:app:connectedDebugAndroidTest` على Emulator API 35.
- فحص PDF الفعلي ورفع artifacts.

## آخر حالة مرجعية قبل الإصلاح

Android CI #851 على `94ce0adf...`:

- verify ✅ Unit/Lint/APK/Schema v9.
- database-tests توقف قبل instrumentation بسبب أربعة imports غير صالحة لـ`androidx.compose.ui.test.onNode`.

الإصلاح يزيل imports فقط؛ لا يخفف Assertions ولا يعطل Tests.

## بوابات الدمج

- لا Tests حمراء أو مخفية.
- لا Lint errors.
- Debug APK يبنى.
- Room Schema المولد يطابق `9.json`.
- Android instrumentation ينجح كاملًا.
- PDF evidence ينجح.
- أي Migration جديدة لها اختبار من النسخة السابقة.
- Backup contract يحدث مع أي جدول/ملف دائم جديد.
- `PROJECT_CONTEXT.md`, `CURRENT_STATUS.md`, `HANDOFF.md`, `CHANGELOG.md` والوثائق ذات الصلة محدثة.
- Git diff مراجع.
- لا Release signing secrets داخل المستودع.
- لا دمج إلى `main` دون طلب صريح.
